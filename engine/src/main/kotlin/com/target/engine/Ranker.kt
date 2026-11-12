package com.target.engine

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

object Money {
    /** Approximate EUR conversion so that listings from every Vinted country can be compared. */
    private val TO_EUR = mapOf(
        "EUR" to 1.0, "GBP" to 1.17, "PLN" to 0.233, "CZK" to 0.0398, "HUF" to 0.00252, "RON" to 0.201,
        "SEK" to 0.0875, "DKK" to 0.134, "USD" to 0.92, "CHF" to 1.05,
    )
    fun toEur(amount: Double, currency: String): Double = amount * (TO_EUR[currency.uppercase()] ?: 1.0)
}

object Stats {
    fun median(values: List<Double>): Double {
        if (values.isEmpty()) return Double.NaN
        val s = values.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2
    }

    /** Median absolute deviation. */
    fun mad(values: List<Double>, med: Double = median(values)): Double =
        if (values.isEmpty()) Double.NaN else median(values.map { kotlin.math.abs(it - med) })

    /** Tukey-style trimming of extreme outliers (1 € placeholders, 999 € "do not buy" prices). */
    fun trimmed(values: List<Double>): List<Double> {
        if (values.size < 5) return values
        val med = median(values)
        val mad = mad(values, med).coerceAtLeast(med * 0.1)
        return values.filter { kotlin.math.abs(it - med) <= 4.5 * mad }
    }
}

enum class Sensitivity(val factor: Double, val label: String) {
    STRICT(0.8, "Stricte"), NORMAL(1.0, "Équilibrée"), WIDE(1.2, "Large")
}

data class Filters(val maxPrice: Double? = null, val sizes: List<String> = emptyList(), val minRelevance: Double = 0.3)

enum class SortMode(val label: String) { ATTRACTIVE("Plus attractif"), NEWEST("Plus récent"), VALUE("Meilleur rapport"), PRICE("Prix croissant") }

/**
 * Scores listings: relevance to the user's targets, market value vs. price ("ratio prix / valeur"), freshness
 * and popularity, then flags real deals with an adaptive threshold.
 */
class Ranker(
    private val targets: List<Target>,
    private val sensitivity: Sensitivity = Sensitivity.NORMAL,
    private val nowSeconds: Long = System.currentTimeMillis() / 1000,
) {
    private val brandSets = targets.map { t -> t.brands.toSet() }
    private val itemConcepts = targets.map { t -> t.items.mapNotNull { Lexicon.concept(it) } }
    private val matConcepts = targets.map { t -> t.materials.mapNotNull { Lexicon.concept(it) } }
    private val colConcepts = targets.map { t -> t.colors.mapNotNull { Lexicon.concept(it) } }
    private val kwTokens = targets.map { t -> t.keywords.map { Text.tokens(it) }.filter { it.isNotEmpty() } }

    /** Per-listing facts computed once. */
    private class Facts(
        val listing: Listing,
        val tokens: List<String>,
        val fieldBrand: Brand?,
        val titleBrand: Brand?,
        val brand: Brand?,
        val group: PriceGroup,
        val itemIds: Set<String>,
        val priceEur: Double,
        val suspicious: Boolean,
        val hits: Int,
    )

    data class Scored(val ranked: RankedListing, val comps: Int)

    fun rank(raw: Collection<Listing>, hits: Map<Long, Int> = emptyMap(), visual: Map<Long, Double> = emptyMap(), filters: Filters = Filters()): List<RankedListing> =
        score(raw, hits, visual, filters).map { it.ranked }

    fun score(raw: Collection<Listing>, hits: Map<Long, Int> = emptyMap(), visual: Map<Long, Double> = emptyMap(), filters: Filters = Filters()): List<Scored> {
        val unique = raw.distinctBy { it.id }.filter { it.price > 0 }
        val facts = unique.map { facts(it, hits[it.id] ?: 1) }

        // Comparable pools for valuation: same brand (or unknown) × same price group, condition-normalised prices.
        val pools = HashMap<String, MutableList<Pair<Long, Double>>>()
        for (f in facts) if (!f.suspicious) pools.getOrPut(poolKey(f)) { ArrayList() } += f.listing.id to f.priceEur / f.listing.condition.priceFactor

        val sizeTokens = filters.sizes.map { Text.normalize(it) }.filter { it.isNotEmpty() }
        val out = ArrayList<Scored>()
        for (f in facts) {
            val l = f.listing
            if (filters.maxPrice != null && f.priceEur > filters.maxPrice) continue
            if (sizeTokens.isNotEmpty()) {
                val st = Text.tokens(l.size ?: "")
                if (st.isNotEmpty() && sizeTokens.none { s -> st.contains(s) || Text.normalize(l.size ?: "") == s }) continue
            }
            var rel = 0.0
            var matchedItem: String? = null
            for (ti in targets.indices) {
                val (r, item) = relevance(f, ti)
                val wr = r * targets[ti].weight.coerceIn(0.0, 1.0).let { 0.85 + 0.15 * it }
                if (wr > rel) { rel = wr; matchedItem = item }
            }
            if (targets.isEmpty()) rel = 0.6
            val vis = visual[l.id]
            if (vis != null) {
                val v = ((vis - 0.45) / 0.4).coerceIn(0.0, 1.0)
                rel = 0.65 * rel + 0.35 * v + 0.1 * v * rel
            }
            rel = min(1.0, rel + 0.03 * (f.hits - 1).coerceAtMost(5))
            if (rel < filters.minRelevance) continue

            val pool = pools[poolKey(f)].orEmpty().filter { it.first != l.id }.map { it.second }
            val comps = Stats.trimmed(pool)
            val prior = (f.brand?.base ?: UNKNOWN_BASE) * f.group.multiplier
            val med = if (comps.isEmpty()) Double.NaN else Stats.median(comps)
            val w = comps.size / (comps.size + 6.0)
            val baseExpected = if (comps.isEmpty()) prior else w * med + (1 - w) * prior
            val expected = max(1.5, baseExpected * l.condition.priceFactor)
            val ratio = expected / f.priceEur
            val ageH = l.createdAt?.let { max(0.0, (nowSeconds - it) / 3600.0) }
            val recency = ageH?.let { exp(-it / 36.0) } ?: 0.35
            val pop = min(1.0, l.favourites / (2.0 + (ageH ?: 48.0) * 0.5))
            val verdict = assessDeal(f, rel, expected, comps, med)
            var value = sigmoid(1.6 * ln(ratio) / ln(2.0))
            if (verdict.suspectedFake) value = min(value, 0.4)
            val score = Math.pow(rel, 1.5) * (0.5 * value + 0.35 * recency + 0.15 * pop) * (0.6 + 0.4 * l.condition.score)
            out += Scored(
                RankedListing(
                    listing = l, relevance = rel, expectedPrice = round2(expected), valueRatio = ratio, recency = recency,
                    score = score, matchedBrand = f.brand?.name, matchedItem = matchedItem ?: f.itemIds.firstOrNull(),
                    hits = f.hits, visual = vis, suspicious = f.suspicious, deal = verdict,
                ),
                comps.size,
            )
        }
        out.sortByDescending { it.ranked.score }
        return out
    }

    private fun facts(l: Listing, hits: Int): Facts {
        val tokens = Text.tokens(l.title)
        val fieldBrand = Brands.fromBrandField(l.brand)
        val titleBrand = Brands.inTitle(tokens)
        // remove brand words before detecting the piece ("Polo Ralph Lauren pull" is a pull, not a polo)
        val brandPhrase = (fieldBrand ?: titleBrand)?.phrases?.firstOrNull { Text.containsPhrase(tokens, it) }
        val itemTokens = if (brandPhrase != null) removePhrase(tokens, brandPhrase) else tokens
        val items = Lexicon.items.filter { it.matches(itemTokens) }
        val group = items.firstOrNull()?.group ?: PriceGroup.DEFAULT
        val suspicious = tokens.any { it in SUSPECT_WORDS } ||
            (fieldBrand != null && titleBrand != null && fieldBrand.id != titleBrand.id && titleBrand.tier >= Tier.PREMIUM)
        return Facts(
            l, itemTokens, fieldBrand, titleBrand, fieldBrand ?: titleBrand, group, items.map { it.id }.toSet(),
            Money.toEur(l.buyerPrice, l.currency), suspicious, hits,
        )
    }

    private fun removePhrase(tokens: List<String>, phrase: List<String>): List<String> {
        for (i in 0..tokens.size - phrase.size) {
            if (phrase.indices.all { Text.tokenEquals(tokens[i + it], phrase[it]) }) return tokens.subList(0, i) + tokens.subList(i + phrase.size, tokens.size)
        }
        return tokens
    }

    private fun poolKey(f: Facts) = "${f.brand?.id ?: "?"}|${f.group}"

    /** Relevance of a listing for target [ti] in [0,1] and the matched piece id. */
    private fun relevance(f: Facts, ti: Int): Pair<Double, String?> {
        val t = targets[ti]
        var num = 0.0
        var den = 0.0
        var matched: String? = null
        val brands = brandSets[ti]
        if (brands.isNotEmpty()) {
            val s = when {
                f.fieldBrand != null && f.fieldBrand.id in brands -> 1.0
                f.fieldBrand == null && f.titleBrand != null && f.titleBrand.id in brands -> 0.8
                f.fieldBrand != null && f.titleBrand != null && f.titleBrand.id in brands -> 0.15 // "pull style Ralph Lauren" by another brand
                else -> 0.0
            } * if (f.suspicious) 0.35 else 1.0
            num += 0.45 * s; den += 0.45
        }
        val items = itemConcepts[ti]
        if (items.isNotEmpty()) {
            val hit = items.firstOrNull { it.id in f.itemIds }
            val s = when {
                hit != null -> { matched = hit.id; 1.0 }
                f.itemIds.isNotEmpty() -> if (items.any { sameFamily(it.id, f.itemIds) }) 0.55 else 0.0
                else -> 0.35
            }
            num += 0.35 * s; den += 0.35
        }
        val mats = matConcepts[ti]
        if (mats.isNotEmpty()) {
            val s = if (mats.any { it.matches(f.tokens) }) 1.0 else 0.4
            num += 0.1 * s; den += 0.1
        }
        val cols = colConcepts[ti]
        if (cols.isNotEmpty()) {
            val s = when {
                cols.any { it.matches(f.tokens) } -> 1.0
                Lexicon.colors.any { it.matches(f.tokens) } -> 0.2
                else -> 0.5
            }
            num += 0.06 * s; den += 0.06
        }
        val kws = kwTokens[ti]
        if (kws.isNotEmpty()) {
            val s = kws.count { Text.containsPhrase(f.tokens, it) }.toDouble() / kws.size
            num += 0.08 * (0.3 + 0.7 * s); den += 0.08
        }
        return (if (den == 0.0) 0.5 else num / den) to matched
    }

    private fun sameFamily(wanted: String, found: Set<String>): Boolean = FAMILIES.any { fam -> wanted in fam && found.any { it in fam } }

    /** Adaptive deal threshold — see [DealPolicy]. */
    private fun assessDeal(f: Facts, rel: Double, expected: Double, comps: List<Double>, med: Double): DealVerdict =
        DealPolicy.assess(
            priceEur = f.priceEur, expected = expected, relevance = rel, condition = f.listing.condition,
            brand = f.brand, brandDeclared = f.fieldBrand != null, suspicious = f.suspicious,
            comps = comps, compMedian = med * f.listing.condition.priceFactor, sensitivity = sensitivity,
        )

    companion object {
        const val UNKNOWN_BASE = 9.0
        val SUSPECT_WORDS = setOf(
            "style", "type", "facon", "inspired", "inspiration", "inspire", "replica", "replique", "copie", "copy", "dupe", "imitation",
            "fake", "esprit", "genre", "vibe", "vibes", "similaire", "similar", "lookalike", "ahnlich", "stil", "tipo", "stile", "estilo",
            "nachgemacht", "imitazione", "rep", "reps", "ua", "1 1",
        )
        private val FAMILIES = listOf(
            setOf("knit", "cardigan", "quarterzip", "sweatshirt", "hoodie", "fleece"),
            setOf("jacket", "coat", "puffer", "bodywarmer", "bomber", "windbreaker", "leatherjacket", "blazer", "overshirt"),
            setOf("tshirt", "polo", "rugby", "shirt", "top", "jersey"),
            setOf("jeans", "trousers", "chinos", "cargo", "joggers"),
            setOf("sneakers", "boots", "loafers", "shoes"),
        )
        private fun sigmoid(x: Double) = 1.0 / (1.0 + exp(-x))
        private fun round2(v: Double) = Math.round(v * 100) / 100.0

        fun sort(list: List<RankedListing>, mode: SortMode): List<RankedListing> = when (mode) {
            SortMode.ATTRACTIVE -> list.sortedByDescending { it.score }
            SortMode.NEWEST -> list.sortedWith(compareByDescending<RankedListing> { it.listing.createdAt ?: 0L }.thenByDescending { it.score })
            SortMode.VALUE -> list.sortedByDescending { it.valueRatio * it.relevance }
            SortMode.PRICE -> list.sortedBy { it.listing.buyerPrice }
        }
    }
}

/**
 * Decides whether a listing is a "Deal". Tuned to fire on genuinely exceptional prices (a real Ralph Lauren knit at
 * 10 €) and stay silent on ordinary cheap items (a 3 € fast-fashion tee) or likely fakes (a 20 € Gucci bag).
 */
object DealPolicy {
    /** Max price / expected value ratio for a deal, per tier (before sensitivity). */
    fun threshold(tier: Tier?): Double = when (tier) {
        Tier.LUXURY -> 0.40
        Tier.PREMIUM -> 0.42
        Tier.MID -> 0.40
        Tier.FAST -> 0.33
        null -> 0.35
    }

    /** Below this ratio the price is too good to be true (likely counterfeit or bait). */
    fun fakeFloor(tier: Tier?): Double = when (tier) {
        Tier.LUXURY -> 0.15
        Tier.PREMIUM -> 0.09
        Tier.MID -> 0.06
        else -> 0.0
    }

    const val MIN_SAVING_EUR = 8.0
    /** Robust z-score (median/MAD) the price must reach below the comparables. */
    const val Z_MIN = 2.4

    fun assess(
        priceEur: Double, expected: Double, relevance: Double, condition: Condition, brand: Brand?, brandDeclared: Boolean,
        suspicious: Boolean, comps: List<Double>, compMedian: Double, sensitivity: Sensitivity,
    ): DealVerdict {
        val ratio = priceEur / expected
        val saving = expected - priceEur
        val tier = brand?.tier
        val n = comps.size
        val confidence = (0.5 * relevance + 0.3 * min(1.0, n / 15.0) + 0.2 * condition.score).coerceIn(0.0, 1.0)
        fun no(reason: String, fake: Boolean = false) = DealVerdict(false, ratio, expected, saving, confidence, reason, fake)

        if (ratio < fakeFloor(tier)) return no("Prix anormalement bas : possible contrefaçon", fake = true)
        if (suspicious) return no("Annonce « style / inspiré »")
        if (relevance < 0.7) return no("Pas assez proche de tes réfs")
        if (!condition.atLeastGood) return no("État insuffisant")
        if (tier == Tier.FAST) return no("Fast fashion : pas de vraie bonne affaire")
        if (brand == null && n < 10) return no("Pas assez de comparables")
        if (brand != null && !brandDeclared && tier != null && tier >= Tier.PREMIUM) return no("Marque non déclarée par le vendeur")
        val limit = threshold(tier) * sensitivity.factor
        if (ratio > limit) return no("Prix correct, pas exceptionnel")
        if (saving < max(MIN_SAVING_EUR, 0.2 * expected)) return no("Économie trop faible")
        if (n >= 8 && !compMedian.isNaN()) {
            // robust z-score in log space: prices are multiplicative (a 10 € gap means more on a 20 € item)
            val logs = comps.filter { it > 0 }.map { kotlin.math.ln(it) }
            val logMad = Stats.mad(logs)
            val z = (kotlin.math.ln(compMedian) - kotlin.math.ln(priceEur)) / (1.4826 * logMad + 0.05)
            if (z < Z_MIN) return no("Dans la dispersion normale des prix")
        }
        return DealVerdict(true, ratio, expected, saving, confidence, "≈ ${(100 * (1 - ratio)).toInt()} % sous la valeur estimée")
    }
}
