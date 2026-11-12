package com.target.engine

/** Kind of a recognised keyword (used for coloured chips in the Ref tab). */
enum class KeywordKind { STYLE, BRAND, ITEM, MATERIAL, COLOR, FREE }

data class Recognized(val kind: KeywordKind, val id: String, val label: String, val source: String, val corrected: Boolean)

data class ParsedQuery(
    val brands: List<Brand>,
    val items: List<Concept>,
    val materials: List<Concept>,
    val colors: List<Concept>,
    val styles: List<Style>,
    val free: List<String>,
    val recognized: List<Recognized>,
) {
    val isEmpty: Boolean get() = brands.isEmpty() && items.isEmpty() && materials.isEmpty() && colors.isEmpty() && styles.isEmpty() && free.isEmpty()
}

/**
 * Turns free text such as "knit ralph laurent bleu marine" into structured intent.
 * Greedy longest-match over styles, brands and the multilingual lexicon, then typo-tolerant brand/term matching
 * ("ralph laurent" → Ralph Lauren, "stussy" → Stüssy, "sweatter" → sweater).
 */
object QueryParser {
    private const val MAX_SPAN = 4

    fun parse(input: String): ParsedQuery {
        val tokens = Text.tokens(input)
        val brands = LinkedHashSet<Brand>()
        val items = LinkedHashSet<Concept>()
        val materials = LinkedHashSet<Concept>()
        val colors = LinkedHashSet<Concept>()
        val styles = LinkedHashSet<Style>()
        val free = ArrayList<String>()
        val recognized = ArrayList<Recognized>()

        fun addConcept(c: Concept, src: String, corrected: Boolean) {
            when (c.kind) {
                ConceptKind.ITEM -> if (items.add(c)) recognized += Recognized(KeywordKind.ITEM, c.id, c.label, src, corrected)
                ConceptKind.MATERIAL, ConceptKind.PATTERN -> if (materials.add(c)) recognized += Recognized(KeywordKind.MATERIAL, c.id, c.label, src, corrected)
                ConceptKind.COLOR -> if (colors.add(c)) recognized += Recognized(KeywordKind.COLOR, c.id, c.label, src, corrected)
            }
        }

        var i = 0
        while (i < tokens.size) {
            var consumed = 0
            for (len in minOf(MAX_SPAN, tokens.size - i) downTo 1) {
                val span = tokens.subList(i, i + len)
                val src = span.joinToString(" ")
                // 1. styles (exact)
                Styles.phraseIndex.firstOrNull { it.first == span }?.let { (_, s) ->
                    if (styles.add(s)) recognized += Recognized(KeywordKind.STYLE, s.id, s.label, src, false)
                    consumed = len
                }
                if (consumed > 0) break
                // 2. brands (exact or glued: "northface", "ralphlauren")
                val glued = span.joinToString("")
                Brands.all.firstOrNull { b -> b.phrases.any { it == span } || (len > 1 && b.glued.contains(glued)) || (len == 1 && glued.length >= 5 && b.glued.contains(glued)) }?.let { b ->
                    consumed = len
                    // "ralph laurent": exact "ralph" alias, then extend over a mistyped continuation of a longer phrase.
                    for (p in b.phrases) {
                        if (p.size <= len || p.subList(0, len) != span || i + p.size > tokens.size) continue
                        val ok = (len until p.size).all { k ->
                            val t = tokens[i + k]
                            Text.distance(t, p[k], Text.typoBudget(p[k].length).coerceAtLeast(1)) <= Text.typoBudget(p[k].length).coerceAtLeast(1)
                        }
                        if (ok) { consumed = p.size; break }
                    }
                    val srcFull = tokens.subList(i, i + consumed).joinToString(" ")
                    if (brands.add(b)) recognized += Recognized(KeywordKind.BRAND, b.id, b.name, srcFull, consumed != len)
                }
                if (consumed > 0) break
                // 3. lexicon (exact, light stemming)
                (Lexicon.phraseIndex.firstOrNull { (p, _) -> p == span }
                    ?: Lexicon.phraseIndex.firstOrNull { (p, _) -> len == 1 && p.size > 1 && p.joinToString("") == span[0] }
                    ?: Lexicon.phraseIndex.firstOrNull { (p, _) -> p.size == len && p.indices.all { Text.tokenEquals(p[it], span[it]) } })?.let { (_, c) ->
                    addConcept(c, src, false)
                    consumed = len
                }
                if (consumed > 0) break
            }
            if (consumed == 0) {
                // 4. typo-tolerant matching (2-token then 1-token windows)
                consumed = fuzzy(tokens, i, brands, recognized) { c, src -> addConcept(c, src, true) }
            }
            if (consumed == 0) {
                val t = tokens[i]
                if (t.length >= 2 && t !in STOP_WORDS) free += t
                consumed = 1
            }
            i += consumed
        }
        return ParsedQuery(brands.toList(), items.toList(), materials.toList(), colors.toList(), styles.toList(), free, recognized)
    }

    private fun fuzzy(
        tokens: List<String>, i: Int, brands: MutableSet<Brand>, recognized: MutableList<Recognized>,
        onConcept: (Concept, String) -> Unit,
    ): Int {
        for (len in minOf(3, tokens.size - i) downTo 1) {
            val span = tokens.subList(i, i + len)
            if (len == 1 && span[0] in Lexicon.knownWords) return 0
            val glued = span.joinToString("")
            val budget = Text.typoBudget(glued.length)
            if (budget == 0) continue
            var best: Brand? = null
            var bestD = Int.MAX_VALUE
            for (b in Brands.all) for (g in b.glued) {
                if (g.length < 4) continue
                val d = Text.distance(glued, g, budget)
                if (d <= budget && d < bestD) { best = b; bestD = d }
            }
            if (best != null) {
                if (brands.add(best)) recognized += Recognized(KeywordKind.BRAND, best.id, best.name, span.joinToString(" "), true)
                return len
            }
            if (len == 1) {
                var bestC: Concept? = null
                var bestCD = Int.MAX_VALUE
                for ((p, c) in Lexicon.phraseIndex) {
                    if (p.size != 1 || p[0].length < 5) continue
                    val d = Text.distance(glued, p[0], budget)
                    if (d <= budget && d < bestCD) { bestC = c; bestCD = d }
                }
                if (bestC != null) { onConcept(bestC, glued); return 1 }
            }
        }
        return 0
    }

    private val STOP_WORDS = setOf(
        "de", "du", "des", "le", "la", "les", "un", "une", "en", "et", "a", "au", "aux", "pour", "avec", "the", "of", "and", "for", "with",
        "der", "die", "das", "und", "il", "lo", "di", "el", "los", "las", "y", "het", "een", "van", "en", "style", "type", "taille", "size",
    )
}
