package com.target.engine

import kotlinx.serialization.Serializable

@Serializable
enum class Condition(val score: Double, val priceFactor: Double, val label: String) {
    NEW_WITH_TAGS(1.0, 1.15, "Neuf avec étiquette"),
    NEW(0.95, 1.08, "Neuf sans étiquette"),
    VERY_GOOD(0.85, 1.0, "Très bon état"),
    GOOD(0.7, 0.85, "Bon état"),
    SATISFACTORY(0.45, 0.6, "Satisfaisant"),
    UNKNOWN(0.7, 0.9, "État non précisé");

    val atLeastGood: Boolean get() = this != SATISFACTORY && this != UNKNOWN

    companion object {
        private val TAG_WORDS = listOf("etiquet", "tag", "etikett", "cartellin", "prijskaart", "metk", "visack", "etiket", "visacko", "lapel", "etichet", "cimke", "oznak", "lappar", "merkki", "maerk", "ετικετ")
        private val NEW_WORDS = listOf("neuf", "new", "neu", "nuovo", "nuevo", "nieuw", "nowy", "novo", "nove", "novy", "nauj", "uj", "nou", "ny", "uusi", "nov", "καινουρ")
        private val VERY_GOOD_WORDS = listOf("tres bon", "very good", "sehr gut", "ottim", "muy buen", "zeer goed", "bardzo dobr", "muito bom", "velmi dobr",
            "labai ger", "foarte bun", "nagyon jo", "vrlo dobr", "mycket bra", "erittain hyva", "meget god", "πολυ καλ")
        private val SATISF_WORDS = listOf("satisf", "zufrieden", "discret", "redelijk", "zadowal", "uspokoj", "patenk", "uspokoj", "elfogad", "zadovolj",
            "tillfreds", "tyydyt", "tilfreds", "ικανοποι", "aceptable", "acceptabil")
        private val WITHOUT = listOf("sans", "without", "ohne", "senza", "sin", "zonder", "bez", "sem", "be", "fara", "nelkul", "utan", "ilman", "uden", "χωρις")
        private val GOOD_WORDS = listOf("bon", "good", "gut", "buon", "buen", "goed", "dobr", "bom", "ger", "bun", "jo", "bra", "hyva", "god", "καλ")

        /** Vinted status_id: 6 new w/ tags, 1 new, 2 very good, 3 good, 4 satisfactory. */
        fun fromId(id: Int?): Condition? = when (id) {
            6 -> NEW_WITH_TAGS
            1 -> NEW
            2 -> VERY_GOOD
            3 -> GOOD
            4 -> SATISFACTORY
            else -> null
        }

        /** Parse a localised Vinted status label in any of the marketplace languages. */
        fun parse(label: String?): Condition {
            if (label.isNullOrBlank()) return UNKNOWN
            val s = Text.normalize(label)
            val words = s.split(' ')
            fun hasPrefix(list: List<String>) = list.any { p -> if (p.contains(' ')) s.contains(p) else words.any { it.startsWith(p) } }
            return when {
                hasPrefix(TAG_WORDS) && hasPrefix(NEW_WORDS) && words.none { it in WITHOUT } -> NEW_WITH_TAGS
                hasPrefix(VERY_GOOD_WORDS) -> VERY_GOOD
                hasPrefix(SATISF_WORDS) -> SATISFACTORY
                hasPrefix(NEW_WORDS) -> NEW
                hasPrefix(GOOD_WORDS) -> GOOD
                else -> UNKNOWN
            }
        }
    }
}

/** One Vinted listing, as returned by the catalogue API. Prices are in the listing currency. */
@Serializable
data class Listing(
    val id: Long,
    val title: String,
    val price: Double,
    val currency: String = "EUR",
    /** Price paid by the buyer including Vinted's buyer protection fee, when known. */
    val totalPrice: Double? = null,
    val brand: String? = null,
    val size: String? = null,
    val condition: Condition = Condition.UNKNOWN,
    val statusLabel: String? = null,
    val photoUrl: String? = null,
    val photos: List<String> = emptyList(),
    /** Small thumbnail (≈310 px) used for fast visual comparison. */
    val thumbUrl: String? = null,
    val url: String,
    val host: String = "www.vinted.fr",
    /** Unix seconds of upload (from the main photo high-resolution timestamp), when known. */
    val createdAt: Long? = null,
    /** True when [createdAt] is estimated from the listing id (Vinted stopped exposing upload times in 2026). */
    val ageEstimated: Boolean = false,
    val favourites: Int = 0,
    val views: Int = 0,
    val seller: String? = null,
    val sellerRating: Double? = null,
    val description: String? = null,
    val promoted: Boolean = false,
) {
    /** Buyer price used for value comparisons. Falls back to Vinted's public fee formula (0,70 € + 5 %). */
    val buyerPrice: Double get() = totalPrice?.takeIf { it >= price } ?: (price + 0.70 + price * 0.05)
}

/** What a user is hunting for: one coherent combination (brand × item × attributes). */
@Serializable
data class Target(
    val brands: List<String> = emptyList(),      // brand ids
    val items: List<String> = emptyList(),       // concept ids (ITEM)
    val materials: List<String> = emptyList(),   // concept ids (MATERIAL/PATTERN)
    val colors: List<String> = emptyList(),      // concept ids (COLOR)
    val keywords: List<String> = emptyList(),    // free words that must be searched as-is ("vintage", "harrington")
    val weight: Double = 1.0,
    val origin: String = "",
) {
    val isEmpty: Boolean get() = brands.isEmpty() && items.isEmpty() && materials.isEmpty() && colors.isEmpty() && keywords.isEmpty()
}

/** One search request sent to Vinted. */
data class PlannedQuery(val text: String, val lang: Lang?, val weight: Double, val targetIndex: Int)

/** A scored listing as displayed in the app. */
@Serializable
data class RankedListing(
    val listing: Listing,
    val relevance: Double,
    val expectedPrice: Double,
    val valueRatio: Double,
    val recency: Double,
    val score: Double,
    val matchedBrand: String? = null,
    val matchedItem: String? = null,
    val hits: Int = 1,
    val visual: Double? = null,
    val suspicious: Boolean = false,
    val deal: DealVerdict? = null,
) {
    /** Percentage below the estimated value (positive = cheaper than the market). */
    val discountPct: Int get() = (((expectedPrice - listing.buyerPrice) / expectedPrice) * 100).toInt()
}

@Serializable
data class DealVerdict(
    val isDeal: Boolean,
    val ratio: Double,
    val expected: Double,
    val saving: Double,
    val confidence: Double,
    val reason: String,
    val suspectedFake: Boolean = false,
)
