package com.target.app.search

import android.graphics.BitmapFactory
import com.target.app.data.Profile
import com.target.app.data.Settings
import com.target.app.vinted.VintedClient
import com.target.app.vision.VisionModel
import com.target.engine.PhotoSignals
import com.target.engine.TasteModel
import com.target.engine.Filters
import com.target.engine.ImageSignal
import com.target.engine.Lang
import com.target.engine.Listing
import com.target.engine.PlannedQuery
import com.target.engine.Planner
import com.target.engine.RankedListing
import com.target.engine.Ranker
import com.target.engine.Target
import com.target.engine.VintedDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class SearchState(
    val running: Boolean,
    val done: Int,
    val total: Int,
    val current: String?,
    val scanned: Int,
    val langs: Set<Lang>,
    val results: List<RankedListing>,
    val errors: Int,
    val error: String? = null,
    val visualPass: Boolean = false,
    /** What the photo pass did ("📷 182 photos analysées") or why it did not run. */
    val photoInfo: String? = null,
    /** Relevant, right kind of piece, but under the beauty bar: shown on demand ("voir aussi les moins belles"). */
    val relaxed: List<RankedListing> = emptyList(),
)

/** Plans dozens of query variants, runs them in parallel on Vinted, merges, de-duplicates and ranks. */
/** Listing photos examined per search (all candidates in practice) and the time the photo pass may take. */
const val PHOTO_BUDGET = 1500
const val PHOTO_TIME_MS = 90_000L

class SearchEngine(
    private val client: VintedClient,
    private val settings: Settings,
    private val vision: VisionModel,
    private val clock: IdClockStore,
    /** Photo embeddings of listings already seen, persisted: a photo is analysed once, ever. */
    private val embeddings: com.target.app.vision.EmbeddingCache,
) {
    fun cachedEmbedding(id: Long): FloatArray? = embeddings.get(id)

    fun targetsFor(profile: Profile): List<Target> = Planner.targetsForProfile(
        profile.keywords,
        profile.images.mapNotNull { img ->
            img.analysis ?: return@mapNotNull null
            ImageSignal(img.id, img.visibleItems(), img.visibleColors(), img.visibleMaterials(), img.visibleBrands())
        },
    )

    /** Embeddings and taste of a profile, for the photo pass. */
    data class Look(val refs: List<FloatArray>, val liked: List<FloatArray>, val disliked: List<FloatArray>)

    private fun lookOf(p: Profile?) = Look(
        refs = p?.images?.mapNotNull { it.analysis?.embedding?.takeIf { e -> e.size >= 64 }?.toFloatArray() }.orEmpty(),
        liked = p?.liked?.map { it.toFloatArray() }.orEmpty(),
        disliked = p?.disliked?.map { it.toFloatArray() }.orEmpty(),
    )

    /**
     * @param photoBudget how many listing photos the vision model examines (most relevant first)
     */
    fun searchProfile(profile: Profile, budget: Int? = null, photoBudget: Int = PHOTO_BUDGET, ui: SearchFilters = SearchFilters()): Flow<SearchState> =
        run(targetsFor(profile), Filters(profile.maxPrice, profile.sizes), lookOf(profile), budget ?: if (settings.value.deepSearch) 36 else 20, photoBudget, ui)

    /** "Lens" search: a single photo, no keywords. The picture itself ranks the results. */
    fun searchPhoto(analysis: com.target.app.data.ImageAnalysis, profile: Profile?, ui: SearchFilters = SearchFilters()): Flow<SearchState> {
        val signal = ImageSignal("photo", analysis.items, analysis.colors, analysis.materials, analysis.brands)
        val targets = Planner.targetsForProfile(emptyList(), listOf(signal)).ifEmpty {
            listOf(Target(items = listOf("knit", "jacket", "shirt", "trousers", "dress"), origin = "image:photo"))
        }
        val look = lookOf(profile).copy(refs = listOfNotNull(analysis.embedding.takeIf { it.isNotEmpty() }?.toFloatArray()))
        return run(targets, Filters(), look, if (settings.value.deepSearch) 28 else 16, PHOTO_BUDGET, ui)
    }

    fun searchText(text: String, profile: Profile?, ui: SearchFilters = SearchFilters()): Flow<SearchState> =
        run(Planner.targetsForSearch(text), Filters(), lookOf(profile).copy(refs = emptyList()), if (settings.value.deepSearch) 32 else 18, PHOTO_BUDGET, ui)

    /** Plan per domain: the home site gets the full budget in every language, extra sites a few queries in theirs. */
    fun planFor(targets: List<Target>, budget: Int): List<Pair<VintedDomain, PlannedQuery>> {
        val s = settings.value
        val home = Planner.plan(targets, s.home.lang, budget).map { s.home to it }
        val extra = s.extraDomains.filter { it != s.home }.flatMap { d -> Planner.plan(targets, d.lang, 6).map { d to it } }
        return home + extra
    }

    fun run(
        baseTargets: List<Target>, baseFilters: Filters, look: Look, budget: Int, photoBudget: Int, ui: SearchFilters = SearchFilters(),
    ): Flow<SearchState> = channelFlow {
        // filters chosen under the search bar override the profile's
        val targets = if (ui.gender != null) baseTargets.map { it.copy(gender = ui.gender) } else baseTargets
        val filters = baseFilters.copy(
            minPrice = ui.minPrice?.toDouble() ?: baseFilters.minPrice,
            maxPrice = ui.maxPrice?.toDouble() ?: baseFilters.maxPrice,
            sizes = if (ui.sizes.isNotEmpty()) ui.sizes.toList() else baseFilters.sizes,
        )
        val queries = planFor(targets, budget)
        // every query newest first (fresh listings), and the main ones also in Vinted's own relevance order, which
        // surfaces the well-presented, popular listings a search on the Vinted app shows first
        val plan = queries.map { Triple(it.first, it.second, "newest_first") } + queries.take(12).map { Triple(it.first, it.second, "relevance") }
        if (plan.isEmpty()) {
            send(SearchState(false, 0, 0, null, 0, emptySet(), emptyList(), 0, "Ajoute des réfs ou tape une recherche"))
            return@channelFlow
        }
        val ranker = Ranker(targets, settings.value.sensitivity)
        val gender = targets.firstNotNullOfOrNull { it.gender }
        val pool = ConcurrentHashMap<Long, Listing>()
        val hits = ConcurrentHashMap<Long, Int>()
        val photos = ConcurrentHashMap<Long, PhotoSignals>()
        val visual = ConcurrentHashMap<Long, Double>()
        val boost = ConcurrentHashMap<Long, Double>()
        val done = AtomicInteger(0)
        val errors = AtomicInteger(0)
        val failStreak = AtomicInteger(0)
        val skipped = AtomicInteger(0)
        val langs = plan.mapNotNull { it.second.lang }.toSet()
        var lastError: String? = null
        val gate = Semaphore(3)
        var lastEmit = 0L

        suspend fun rankNow(listings: Collection<Listing>) = withContext(Dispatchers.Default) {
            ranker.rank(listings, hits, HashMap(visual), filters, HashMap(photos), HashMap(boost))
        }

        var photoInfo: String? = null
        var photoError: String? = null
        suspend fun emit(running: Boolean, current: String?, ranked: List<RankedListing>, visualPass: Boolean = false, relaxed: List<RankedListing> = emptyList()) {
            val error = when {
                running || ranked.isNotEmpty() -> null
                pool.isEmpty() && errors.get() > 0 -> lastError ?: "Vinted ne répond pas"
                pool.isEmpty() -> "Vinted n'a renvoyé aucune annonce pour ces ${plan.size} recherches."
                else -> "${pool.size} annonces trouvées, mais aucune ne correspond assez à ta recherche ou n'est assez belle (ou filtres prix / taille)."
            }
            send(SearchState(running, done.get(), plan.size, current, pool.size, langs, ranked, errors.get(), error, visualPass, photoInfo, relaxed))
        }

        send(SearchState(true, 0, plan.size, plan.first().second.text, 0, langs, emptyList(), 0))
        // the vision model loads (or downloads, once) while Vinted is queried
        val visionReady = async { runCatching { vision.ensure() }.getOrDefault(false) }
        val searchDone = java.util.concurrent.atomic.AtomicBoolean(false)
        val analysed = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
        val cacheHits = AtomicInteger(0)
        val photoStart = System.currentTimeMillis()

        // Photo pipeline, running *during* the Vinted searches: the most relevant listings found so far are examined
        // first (loose selection so that a vague title cannot hide a beautiful look-alike). Photos already seen come
        // from the persistent cache; the others are downloaded 16 at a time and embedded in batches of 8.
        val photoJob = launch {
          try {
            if (!visionReady.await() || photoBudget <= 0) return@launch
            val taste = vision.taste ?: return@launch
            embeddings.use(vision.modelName ?: return@launch)
            fun record(l: Listing, e: FloatArray) {
                photos[l.id] = taste.analyze(e)
                if (look.refs.isNotEmpty()) visual[l.id] = look.refs.maxOf { TasteModel.dot(it, e) }
                personal(look, e)?.let { boost[l.id] = it }
            }
            // every candidate is examined (up to [photoBudget]), within a time limit so that a huge search still ends
            while (analysed.size < photoBudget && System.currentTimeMillis() - photoStart < PHOTO_TIME_MS) {
                val snapshot = withContext(Dispatchers.Default) { clock.annotate(pool.values, record = false) }
                val next = withContext(Dispatchers.Default) {
                    ranker.rank(snapshot, hits, emptyMap(), filters.copy(minRelevance = 0.35, minBeauty = 3.5))
                }.asSequence().map { it.listing }.filter { it.id !in analysed }.take(32).toList()
                if (next.isEmpty()) { if (searchDone.get()) break; delay(350); continue }
                next.forEach { analysed += it.id }
                val toFetch = ArrayList<Listing>()
                for (l in next) {
                    val cached = embeddings.get(l.id)
                    if (cached != null) { record(l, cached); cacheHits.incrementAndGet() } else toFetch += l
                }
                val prepared = toFetch.map { l -> async(Dispatchers.IO) { download(l)?.let { bmp -> vision.prepare(bmp) }?.let { l to it } } }.awaitAll().filterNotNull()
                for (batch in prepared.chunked(8)) {
                    val embs = vision.embedBatch(batch.map { it.second }) ?: continue
                    batch.zip(embs).forEach { (p, e) -> if (e.isNotEmpty()) { embeddings.put(p.first.id, e); record(p.first, e) } }
                }
                val now = System.currentTimeMillis()
                if (now - lastEmit > 700) {
                    lastEmit = now
                    val strict = photos.size >= 12
                    val partial = withContext(Dispatchers.Default) {
                        ranker.rank(clock.annotate(pool.values, record = false), hits, HashMap(visual), filters.copy(requirePhoto = strict), HashMap(photos), HashMap(boost))
                    }
                    emit(true, if (searchDone.get()) "Analyse des photos ${analysed.size}" else "Recherche + analyse des photos", partial, visualPass = true)
                }
            }
          } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // the photo analysis failing must never take the search (or the app) down: results stay title-based
            android.util.Log.e("Target", "photo pass failed", e)
            photoError = "⚠ Analyse photo interrompue : ${e.javaClass.simpleName} ${e.message ?: ""}"
          }
        }

        coroutineScope {
            plan.map { (domain, q, order) ->
                async {
                    gate.withPermit {
                        // Vinted is blocking this connection: stop sending (it would only extend the block) and keep
                        // the results already found
                        if (failStreak.get() >= 3 && client.throttled(domain.host)) {
                            skipped.incrementAndGet(); done.incrementAndGet()
                            lastError = "Vinted limite les recherches trop rapides : résultats partiels, réessaie dans une minute."
                            return@withPermit
                        }
                        try {
                            val items = client.search(domain.host, q.text, priceTo = filters.maxPrice, currency = domain.currency, catalogId = gender?.catalogId,
                                priceFrom = filters.minPrice, order = order)
                            for (it in items) {
                                pool.putIfAbsent(it.id, it)
                                hits.merge(it.id, 1, Int::plus)
                            }
                            failStreak.set(0)
                        } catch (e: Exception) {
                            failStreak.incrementAndGet()
                            errors.incrementAndGet()
                            lastError = e.message
                        }
                        done.incrementAndGet()
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 450 && photos.isEmpty()) { lastEmit = now; emit(true, q.text, rankNow(clock.annotate(pool.values, record = false))) }
                    }
                }
            }.awaitAll()
        }
        searchDone.set(true)
        photoJob.join()

        val annotated = withContext(Dispatchers.Default) { clock.annotate(pool.values, record = true) }
        val ranked = if (photos.isEmpty()) rankNow(annotated)
            else withContext(Dispatchers.Default) { ranker.rank(annotated, hits, HashMap(visual), filters.copy(requirePhoto = true), HashMap(photos), HashMap(boost)) }
        // where the listings went: relevant by title → photo checked → right piece → beautiful enough
        var relaxed = emptyList<RankedListing>()
        photoInfo = when {
            photos.isNotEmpty() -> withContext(Dispatchers.Default) {
                val secs = (System.currentTimeMillis() - photoStart) / 1000
                val loose = ranker.rank(annotated, hits, emptyMap(), filters.copy(minBeauty = 0.0))
                val checked = loose.count { photos.containsKey(it.listing.id) }
                val rightPiece = ranker.rank(annotated, hits, HashMap(visual), filters.copy(requirePhoto = true, minBeauty = 0.0), HashMap(photos), HashMap(boost))
                val shownIds = ranked.mapTo(HashSet()) { it.listing.id }
                relaxed = rightPiece.filter { it.listing.id !in shownIds }
                val notChecked = loose.size - checked
                "${pool.size} trouvées · ${loose.size} pertinentes au titre · $checked photos vérifiées" +
                    (if (notChecked > 0) " ($notChecked pas eu le temps)" else "") +
                    "\n→ ${(checked - rightPiece.size).coerceAtLeast(0)} pas la bonne pièce · ${relaxed.size} pas assez belles · ${ranked.size} gardées" +
                    "\n📷 ${photos.size} photos en ${secs} s (${cacheHits.get()} déjà connues) · ${vision.modelName} · ${vision.backend}"
            }
            photoError != null -> photoError
            vision.ready -> "⚠ Aucune photo n'a pu être analysée (connexion aux images Vinted ?)"
            else -> when (val st = vision.state.value) {
                is com.target.app.vision.VisionState.Downloading -> "⚠ Analyse photo pas encore prête : téléchargement du modèle ${(st.progress * 100).toInt()} %"
                is com.target.app.vision.VisionState.Failed -> "⚠ Analyse photo inactive : ${st.message}"
                else -> "⚠ Analyse photo inactive (Réglages → Analyse visuelle) : tri sur les titres uniquement"
            }
        }
        if (skipped.get() > 0) photoInfo = "⚠ Vinted a ralenti la connexion : ${plan.size - skipped.get()}/${plan.size} recherches faites (réessaie dans une minute pour la suite)" +
            (photoInfo?.let { "\n$it" } ?: "")
        emit(false, null, ranked, relaxed = relaxed.take(400))
    }

    /** Personal taste from 👍 / 👎: closer to liked photos than to disliked ones → bonus beauty points. */
    private fun personal(look: Look, emb: FloatArray): Double? {
        if (look.liked.isEmpty() && look.disliked.isEmpty()) return null
        val like = look.liked.maxOfOrNull { TasteModel.dot(it, emb) } ?: 0.6
        val dislike = look.disliked.maxOfOrNull { TasteModel.dot(it, emb) } ?: 0.6
        return ((like - dislike) * 8.0).coerceIn(-2.0, 2.0)
    }

    private fun download(l: Listing): android.graphics.Bitmap? {
        val url = l.thumbUrl ?: l.photoUrl ?: return null
        val bytes = runCatching {
            client.http.newCall(Request.Builder().url(url).build()).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null }
        }.getOrNull() ?: return null
        val opts = BitmapFactory.Options().apply { inSampleSize = if (l.thumbUrl != null) 1 else 2 }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    /** Embedding of one listing photo (for 👍 / 👎 from the detail screen). */
    suspend fun embeddingFor(l: Listing): FloatArray? {
        if (!vision.ensure()) return null
        embeddings.use(vision.modelName ?: return null)
        return embeddings.get(l.id)
            ?: withContext(Dispatchers.IO) { download(l) }?.let { vision.embed(it) }?.also { embeddings.put(l.id, it) }
    }
}
