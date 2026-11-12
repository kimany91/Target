package com.target.app.search

import android.graphics.BitmapFactory
import com.target.app.data.Profile
import com.target.app.data.Settings
import com.target.app.vinted.VintedClient
import com.target.app.vision.Embedder
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
)

/** Plans dozens of query variants, runs them in parallel on Vinted, merges, de-duplicates and ranks. */
class SearchEngine(private val client: VintedClient, private val settings: Settings, private val embedder: Embedder) {

    fun targetsFor(profile: Profile): List<Target> = Planner.targetsForProfile(
        profile.keywords,
        profile.images.mapNotNull { img ->
            img.analysis ?: return@mapNotNull null
            ImageSignal(img.id, img.visibleItems(), img.visibleColors(), img.visibleMaterials(), img.visibleBrands())
        },
    )

    fun searchProfile(profile: Profile, budget: Int? = null, visual: Boolean = true): Flow<SearchState> {
        val refs = if (!visual) emptyList() else profile.images.mapNotNull { it.analysis?.embedding?.takeIf { e -> e.isNotEmpty() }?.toFloatArray() }
        return run(targetsFor(profile), Filters(profile.maxPrice, profile.sizes), refs, budget ?: if (settings.value.deepSearch) 36 else 20)
    }

    fun searchText(text: String): Flow<SearchState> =
        run(Planner.targetsForSearch(text), Filters(), emptyList(), if (settings.value.deepSearch) 32 else 18)

    /** Plan per domain: the home site gets the full budget in every language, extra sites a few queries in theirs. */
    fun planFor(targets: List<Target>, budget: Int): List<Pair<VintedDomain, PlannedQuery>> {
        val s = settings.value
        val home = Planner.plan(targets, s.home.lang, budget).map { s.home to it }
        val extra = s.extraDomains.filter { it != s.home }.flatMap { d -> Planner.plan(targets, d.lang, 6).map { d to it } }
        return home + extra
    }

    fun run(targets: List<Target>, filters: Filters, refEmbeddings: List<FloatArray>, budget: Int): Flow<SearchState> = channelFlow {
        val plan = planFor(targets, budget)
        if (plan.isEmpty()) {
            send(SearchState(false, 0, 0, null, 0, emptySet(), emptyList(), 0, "Ajoute des réfs ou tape une recherche"))
            return@channelFlow
        }
        val ranker = Ranker(targets, settings.value.sensitivity)
        val pool = ConcurrentHashMap<Long, Listing>()
        val hits = ConcurrentHashMap<Long, Int>()
        val done = AtomicInteger(0)
        val errors = AtomicInteger(0)
        val langs = plan.mapNotNull { it.second.lang }.toSet()
        var lastError: String? = null
        val gate = Semaphore(3)
        var lastEmit = 0L

        suspend fun emit(running: Boolean, current: String?, final: List<RankedListing>? = null, visual: Boolean = false) {
            val ranked = final ?: withContext(Dispatchers.Default) { ranker.rank(pool.values.toList(), hits, emptyMap(), filters) }
            send(SearchState(running, done.get(), plan.size, current, pool.size, langs, ranked, errors.get(),
                if (!running && pool.isEmpty() && errors.get() > 0) lastError else null, visual))
        }

        send(SearchState(true, 0, plan.size, plan.first().second.text, 0, langs, emptyList(), 0))
        coroutineScope {
            plan.mapIndexed { i, (domain, q) ->
                async {
                    gate.withPermit {
                        delay(120L * (i % 3))
                        try {
                            val items = client.search(domain.host, q.text, priceTo = filters.maxPrice, currency = domain.currency)
                            for (it in items) {
                                pool.putIfAbsent(it.id, it)
                                hits.merge(it.id, 1, Int::plus)
                            }
                        } catch (e: Exception) {
                            errors.incrementAndGet()
                            lastError = e.message
                        }
                        done.incrementAndGet()
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 450) { lastEmit = now; emit(true, q.text) }
                    }
                }
            }.awaitAll()
        }

        var ranked = withContext(Dispatchers.Default) { ranker.rank(pool.values.toList(), hits, emptyMap(), filters) }
        if (refEmbeddings.isNotEmpty() && ranked.isNotEmpty()) {
            emit(true, "Analyse visuelle", ranked, visual = true)
            val visual = visualScores(ranked.take(60).map { it.listing }, refEmbeddings)
            if (visual.isNotEmpty()) ranked = withContext(Dispatchers.Default) { ranker.rank(pool.values.toList(), hits, visual, filters) }
        }
        emit(false, null, ranked)
    }

    /** Cosine similarity between each listing's photo and the closest reference photo. */
    private suspend fun visualScores(listings: List<Listing>, refs: List<FloatArray>): Map<Long, Double> = coroutineScope {
        val out = ConcurrentHashMap<Long, Double>()
        val gate = Semaphore(4)
        listings.map { l ->
            launch(Dispatchers.IO) {
                val url = l.photoUrl ?: return@launch
                gate.withPermit {
                    val bytes = runCatching {
                        client.http.newCall(Request.Builder().url(url).build()).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null }
                    }.getOrNull() ?: return@withPermit
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return@withPermit
                    val emb = embedder.embed(bmp) ?: return@withPermit
                    out[l.id] = refs.maxOf { Embedder.cosine(it, emb) }
                }
            }
        }.forEach { it.join() }
        out
    }
}
