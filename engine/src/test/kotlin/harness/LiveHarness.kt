package harness

import com.target.engine.*
import com.target.engine.Target
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Developer harness: runs the real pipeline against live Vinted (skipped unless HARNESS_DIR is set).
 *  stage "fetch": plan queries, call Vinted, write pool.json + candidates.json (thumbnails to embed)
 *  stage "rank" : read pool.json + emb.json (MobileCLIP embeddings computed by tools/taste), rank like the app.
 */
class LiveHarness {
    private val dir = System.getenv("HARNESS_DIR")?.let { File(it) }
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true; encodeDefaults = true }
    private val ua = "Mozilla/5.0 (Linux; Android 15; SM-S938B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    private fun targets(): List<Target> {
        val q = System.getenv("HARNESS_QUERY") ?: ""
        val profile = System.getenv("HARNESS_PROFILE")
        return if (profile != null) Planner.targetsForProfile(profile.split("|"), emptyList()) else Planner.targetsForSearch(q)
    }

    @Test fun fetch() {
        Assume.assumeTrue(dir != null && System.getenv("HARNESS_STAGE") == "fetch")
        val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
        val home = http.send(HttpRequest.newBuilder(URI("https://www.vinted.fr/")).header("User-Agent", ua).header("Accept", "text/html").build(), HttpResponse.BodyHandlers.discarding())
        // the same cookie can be sent twice (first deleted, then set): keep the last non-empty value of each
        val jar = LinkedHashMap<String, String>()
        home.headers().allValues("set-cookie").map { it.substringBefore(';') }.forEach { c ->
            val (k, v) = c.substringBefore('=') to c.substringAfter('=')
            if (v.isNotEmpty()) jar[k] = v
        }
        val cookies = jar.map { "${it.key}=${it.value}" }
        val token = jar["access_token_web"]
        println("home ${home.statusCode()} token=${token != null}")
        val ts = targets()
        val gender = ts.firstNotNullOfOrNull { it.gender }
        val plan = Planner.plan(ts, Lang.FR, (System.getenv("HARNESS_BUDGET") ?: "32").toInt())
        val runs = plan.map { it to "newest_first" } + plan.take((System.getenv("HARNESS_REL") ?: "8").toInt()).map { it to "relevance" }
        val pool = LinkedHashMap<Long, Listing>(); val hits = HashMap<Long, Int>()
        for ((q, order) in runs) {
            var url = "https://api.vinted.fr/svc-catalogue/items?search_text=" + URLEncoder.encode(q.text, "UTF-8") + "&page=1&per_page=96&order=$order"
            if (gender != null) url += "&attribute_ids%5Bcatalog%5D=${gender.catalogId}"
            val req = HttpRequest.newBuilder(URI(url)).header("User-Agent", ua).header("Accept", "application/json, text/plain, */*")
                .header("Origin", "https://www.vinted.fr").header("Referer", "https://www.vinted.fr/").header("Locale", "fr-FR")
                .header("Platform", "web").header("X-Next-App", "marketplace-web").header("Cookie", cookies.joinToString("; "))
                .apply { if (token != null) header("Authorization", "Bearer $token") }.build()
            val r = http.send(req, HttpResponse.BodyHandlers.ofString())
            val items = VintedJson.parseCatalog(r.body(), "www.vinted.fr")
            println("  ${r.statusCode()} %-9s %3d  %s".format(order, items.size, q.text))
            for (l in items) { pool.putIfAbsent(l.id, l); hits.merge(l.id, 1, Int::plus) }
            Thread.sleep((System.getenv("HARNESS_DELAY") ?: "900").toLong())
        }
        File(dir, "pool.json").writeText(json.encodeToString(ListSerializer(Listing.serializer()), pool.values.toList()))
        File(dir, "hits.json").writeText(json.encodeToString(MapSerializer(Long.serializer(), Int.serializer()), hits))
        val loose = Ranker(ts).rank(pool.values, hits, filters = Filters(minRelevance = 0.35, minBeauty = 3.5))
        File(dir, "candidates.json").writeText(json.encodeToString(MapSerializer(Long.serializer(), String.serializer()),
            loose.take(260).associate { it.listing.id to (it.listing.thumbUrl ?: it.listing.photoUrl ?: "") }))
        println("pool=${pool.size} candidates=${minOf(260, loose.size)} textOnlyShown=${Ranker(ts).rank(pool.values, hits).size}")
    }

    /** Stage "plan": writes plan.tsv (query \t order) for an external, slow fetcher (curl). */
    @Test fun plan() {
        Assume.assumeTrue(dir != null && System.getenv("HARNESS_STAGE") == "plan")
        val ts = targets()
        val plan = Planner.plan(ts, Lang.FR, (System.getenv("HARNESS_BUDGET") ?: "16").toInt())
        val runs = plan.map { it.text to "newest_first" } + plan.take((System.getenv("HARNESS_REL") ?: "6").toInt()).map { it.text to "relevance" }
        File(dir, "plan.tsv").writeText(runs.joinToString("\n") { "${it.first}\t${it.second}" } + "\n")
        println("gender=${ts.firstNotNullOfOrNull { it.gender }} queries=${runs.size}")
    }

    /** Stage "build": reads raw/NN.json fetched by curl, writes pool / hits / candidates like stage "fetch". */
    @Test fun build() {
        Assume.assumeTrue(dir != null && System.getenv("HARNESS_STAGE") == "build")
        val pool = LinkedHashMap<Long, Listing>(); val hits = HashMap<Long, Int>()
        File(dir, "raw").listFiles()!!.sorted().forEach { f ->
            val items = VintedJson.parseCatalog(f.readText(), "www.vinted.fr")
            println("  %3d  %s".format(items.size, f.name))
            for (l in items) { pool.putIfAbsent(l.id, l); hits.merge(l.id, 1, Int::plus) }
        }
        val ts = targets()
        File(dir, "pool.json").writeText(json.encodeToString(ListSerializer(Listing.serializer()), pool.values.toList()))
        File(dir, "hits.json").writeText(json.encodeToString(MapSerializer(Long.serializer(), Int.serializer()), hits))
        val loose = Ranker(ts).rank(pool.values, hits, filters = Filters(minRelevance = 0.35, minBeauty = 3.5))
        File(dir, "candidates.json").writeText(json.encodeToString(MapSerializer(Long.serializer(), String.serializer()),
            loose.take(260).associate { it.listing.id to (it.listing.thumbUrl ?: it.listing.photoUrl ?: "") }))
        println("pool=${pool.size} candidates=${minOf(260, loose.size)}")
    }

    @Test fun rank() {
        Assume.assumeTrue(dir != null && System.getenv("HARNESS_STAGE") == "rank")
        val pool = json.decodeFromString(ListSerializer(Listing.serializer()), File(dir, "pool.json").readText())
        val hits = json.decodeFromString(MapSerializer(Long.serializer(), Int.serializer()), File(dir, "hits.json").readText())
        val model = TasteModel.parse(File(System.getenv("HARNESS_TASTE")).readText())
        val emb = (json.parseToJsonElement(File(dir, "emb.json").readText()) as JsonObject)
        val photos = emb.entries.associate { (k, v) -> k.toLong() to model.analyze((v as JsonArray).map { it.toString().toFloat() }.toFloatArray()) }
        val ts = targets()
        val ranked = Ranker(ts).rank(pool, hits, filters = Filters(requirePhoto = true), photos = photos)
        val picks = Ranker.sort(ranked, SortMode.PICKS)
        val all = Ranker(ts).score(pool, hits, filters = Filters(minRelevance = 0.0, minBeauty = 0.0), photos = photos).map { it.ranked }
        println("analysed=${photos.size} shown=${ranked.size}")
        val out = StringBuilder()
        fun line(tag: String, r: RankedListing) = "$tag\t${r.listing.id}\t%.1f\t%.2f\t%s\t%s\t%s\t%.0f€\t%s\t%s\n".format(
            r.beauty, r.relevance, r.photoCategory, r.listing.brand, r.listing.size, r.listing.price, r.listing.title.take(70), r.beautyReasons.joinToString(" | "))
        picks.forEachIndexed { i, r -> out.append(line("SHOWN#${i + 1}", r)) }
        val shownIds = ranked.map { it.listing.id }.toSet()
        all.filter { it.listing.id in photos && it.listing.id !in shownIds }.sortedByDescending { it.beauty }.forEach { out.append(line("HIDDEN", it)) }
        File(dir, "results.tsv").writeText(out.toString())
    }
}
