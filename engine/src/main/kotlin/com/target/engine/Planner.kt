package com.target.engine

/** Visual/semantic signals extracted on-device from a reference photo. */
data class ImageSignal(
    val refId: String,
    val items: List<String>,
    val colors: List<String>,
    val materials: List<String>,
    val brands: List<String>,
)

/** Builds [Target]s (what to hunt) from a Ref profile or a free search, then plans the Vinted queries. */
object Planner {

    /** FR, DE, IT, ES, NL, PL, EN: the languages of the biggest Vinted markets (first in [Lang.entries]). */
    private const val BIG_MARKETS = 7

    /** Brands known for each garment family (used to order queries when the budget is small). */
    private val STRONG: Map<String, Set<String>> = mapOf(
        "knit" to setOf("stussy", "supreme", "corteiz", "carhartt", "offwhite", "palace", "ralphlauren", "acnestudios", "lacoste", "tommyhilfiger",
            "gant", "barbour", "massimodutti", "cos", "arket", "apc", "amiparis", "loropiana", "brunellocucinelli", "kenzo", "polene", "sezane", "ganni"),
        "outer" to setOf("carhartt", "thenorthface", "arcteryx", "patagonia", "barbour", "stoneisland", "cpcompany", "moncler", "canadagoose", "schott",
            "levis", "lee", "ralphlauren", "nike", "salomon", "napapijri", "helly hansen", "belstaff"),
        "sweat" to setOf("stussy", "supreme", "corteiz", "carhartt", "nike", "jordan", "palace", "bape", "champion", "thenorthface", "patagonia", "offwhite"),
        "bottom" to setOf("levis", "carhartt", "dickies", "lee", "wrangler", "acnestudios", "apc", "nike", "stussy"),
        "shoes" to setOf("nike", "jordan", "adidas", "newbalance", "salomon", "asics", "drmartens", "veja", "converse", "vans", "redwing", "clarks"),
        "top" to setOf("ralphlauren", "lacoste", "stussy", "carhartt", "fredperry", "supreme", "palace", "tommyhilfiger", "nike"),
    )

    /** Free search bar: everything typed is one intent; styles expand into their brands/pieces. */
    fun targetsForSearch(text: String): List<Target> {
        val p = QueryParser.parse(text)
        if (p.isEmpty && p.gender == null) return emptyList()
        return targetsFrom(p, images = emptyList())
    }

    /** Ref profile: keywords + analysed reference photos. */
    fun targetsForProfile(keywords: List<String>, images: List<ImageSignal>): List<Target> {
        val p = QueryParser.parse(keywords.joinToString(" , "))
        // keep each typed keyword's free words together ("harrington jacket" stays a phrase)
        return targetsFrom(p, images)
    }

    private fun targetsFrom(p: ParsedQuery, images: List<ImageSignal>): List<Target> =
        targetsFromParsed(p, images).map { it.copy(gender = p.gender, sizes = p.sizes) }

    private fun targetsFromParsed(p: ParsedQuery, images: List<ImageSignal>): List<Target> {
        val out = ArrayList<Target>()
        val brandIds = p.brands.map { it.id }
        val itemIds = p.items.map { it.id }
        val matIds = p.materials.map { it.id }
        val colIds = p.colors.map { it.id }
        val free = p.free

        // Core target: what the user typed explicitly. With a style and no brand ("knit" + "streetwear"), the pieces
        // belong to the style (a streetwear knit), so no generic "any knit" target is created.
        val styleOwnsItems = p.styles.isNotEmpty() && brandIds.isEmpty()
        if (brandIds.isNotEmpty() || (itemIds.isNotEmpty() && !styleOwnsItems) || (p.styles.isEmpty() && (matIds.isNotEmpty() || colIds.isNotEmpty() || free.isNotEmpty()))) {
            out += Target(brandIds, itemIds, matIds, colIds, free, 1.0, "keywords")
        }
        // One target per style: style brands × (typed pieces, or the style's own pieces).
        for (s in p.styles) {
            val items = itemIds.ifEmpty { s.items }
            val brands = (brandIds + s.brandIds).distinct()
            // the style's own words ("streetwear", "old money") are NOT added to the queries: few real listings carry
            // them, mostly keyword-stuffed ones; the style is expressed by its brands, pieces and the photo check
            out += Target(brands, items, (matIds + s.materials).distinct(), colIds, free, 0.9, "style:${s.id}")
        }
        // One target per reference photo. Outfit photos hold several pieces (a knit + jeans): only the piece the profile
        // hunts (same family as its keyword pieces) or, without keyword pieces, the main one is searched.
        val wanted = itemIds.mapNotNull { Taste.FAMILY[it] }.toSet()
        for (img in images) {
            val brands = img.brands.ifEmpty { brandIds }
            val items = if (wanted.isNotEmpty()) img.items.filter { Taste.FAMILY[it] in wanted }.ifEmpty { itemIds } else img.items.take(1)
            if (brands.isEmpty() && items.isEmpty() && img.colors.isEmpty()) continue
            out += Target(brands, items, (img.materials + matIds).distinct(), img.colors.take(2), emptyList(), 0.95, "image:${img.refId}")
        }
        return out
    }

    /**
     * Plans up to [budget] distinct Vinted queries. Every brand × piece combination is searched first in the
     * marketplace's language, then in every other Vinted language and with synonyms, round-robin so that a small
     * budget still covers every combination.
     */
    fun plan(targets: List<Target>, home: Lang, budget: Int): List<PlannedQuery> {
        if (targets.isEmpty() || budget <= 0) return emptyList()
        // home language first, then the others by marketplace size. Vinted already maps synonyms inside a language
        // ("pull" ≈ "sweater": 75 % identical results), so other languages are what brings new listings.
        val langs = (listOf(home) + Lang.entries).distinct()
        val streams = ArrayList<List<PlannedQuery>>()
        var pairNo = 0
        targets.forEachIndexed { ti, t ->
            // brands that actually make the requested piece come first (Stüssy knits before Jordan "knits")
            val families = t.items.mapNotNull { Taste.FAMILY[it] }.toSet()
            val brands: List<Brand?> = t.brands.mapNotNull { Brands.byId(it) }
                .sortedByDescending { b -> families.count { f -> b.id in (STRONG[f] ?: emptySet()) } }
                .ifEmpty { listOf(null) }
            val items: List<Concept?> = t.items.mapNotNull { Lexicon.concept(it) }.ifEmpty { listOf(null) }
            val mats = t.materials.mapNotNull { Lexicon.concept(it) }
            val cols = t.colors.mapNotNull { Lexicon.concept(it) }
            val pairs = ArrayList<Pair<Brand?, Concept?>>()
            for (b in brands) for (c in items) pairs += b to c
            // order pairs so that the first brands/items are mixed early: (0,0),(1,0),(0,1),(1,1)...
            pairs.sortBy { (b, c) -> brands.indexOf(b) + items.indexOf(c) }
            for ((b, c) in pairs) {
                // each brand × piece starts in a different big market's language (live test: a search with many brands
                // planned "pull" for all of them, then "pullover", and never reached Italian, Spanish, Dutch, Polish
                // sellers). Vinted shares one EU catalogue: every language brings listings from another country.
                val k = pairNo++ % BIG_MARKETS
                streams += pairStream(b, c, mats, cols, t.keywords, langs.drop(k) + langs.take(k), home, t.weight, ti)
            }
        }
        // round robin
        val out = ArrayList<PlannedQuery>()
        val seen = HashSet<String>()
        var depth = 0
        while (out.size < budget) {
            var progressed = false
            for (s in streams) {
                if (depth < s.size) {
                    progressed = true
                    val q = s[depth]
                    val key = Text.normalize(q.text)
                    if (key.isNotEmpty() && seen.add(key)) {
                        out += q
                        if (out.size >= budget) break
                    }
                }
            }
            if (!progressed) break
            depth++
        }
        return out
    }

    private fun pairStream(
        b: Brand?, c: Concept?, mats: List<Concept>, cols: List<Concept>, kws: List<String>,
        langs: List<Lang>, home: Lang, weight: Double, ti: Int,
    ): List<PlannedQuery> {
        val list = ArrayList<PlannedQuery>()
        val kw = kws.joinToString(" ")
        fun add(parts: List<String?>, lang: Lang?, w: Double) {
            val text = parts.filterNot { it.isNullOrBlank() }.joinToString(" ").trim()
            if (text.isNotEmpty()) list += PlannedQuery(text, lang, w * weight, ti)
        }
        val mat0 = mats.firstOrNull()
        when {
            c != null -> {
                // primary term per language (home first), with the main material when there is one
                for (l in langs) {
                    val term = c.primary(l) ?: continue
                    add(listOf(b?.name, term, mat0?.primary(l), kw), l, if (l == home) 1.0 else 0.85)
                    if (l == home && cols.isNotEmpty()) add(listOf(b?.name, term, cols[0].primary(l)), l, 0.95)
                }
                // synonyms (home language then English), and without material if one was added
                for (l in listOf(home, Lang.EN)) {
                    for (syn in c.synonyms(l).drop(1)) add(listOf(b?.name, syn, kw), l, 0.9)
                }
                if (mat0 != null) add(listOf(b?.name, c.primary(home), kw), home, 0.9)
            }
            b != null -> {
                add(listOf(b.name, kw), null, 1.0)
                for (m in mats) for (l in langs) add(listOf(b.name, m.primary(l), kw), l, 0.85)
                for (alias in b.phrases.drop(1).filter { it.size > 1 }.take(2)) add(listOf(alias.joinToString(" "), kw), null, 0.8)
            }
            else -> {
                for (l in langs) add(listOf(mat0?.primary(l), cols.firstOrNull()?.primary(l), kw), l, if (l == home) 1.0 else 0.8)
            }
        }
        return list
    }
}
