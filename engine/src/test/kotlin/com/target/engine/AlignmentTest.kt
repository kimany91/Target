package com.target.engine

import org.junit.Assert.*
import org.junit.Test

/** Cases taken from real results: the search must stay on what was asked. */
class AlignmentTest {
    private val now = 1_800_000_000L
    private var id = 1L
    private fun l(title: String, price: Double, brand: String?, cond: Condition = Condition.VERY_GOOD) =
        Listing(id++, title, price, brand = brand, condition = cond, url = "u", createdAt = now - 600)

    @Test fun piecesAreDetectedPrecisely() {
        fun items(t: String) = Lexicon.detectItems(Text.tokens(t)).map { it.id }
        assertEquals("hoodie", items("Pulls à capuche homme marron").first())
        assertFalse(items("Pulls à capuche homme marron").contains("knit"))
        assertEquals(listOf("scarf"), items("Foulard jersey premium"))
        assertEquals(listOf("knit"), items("Jersey de lana"))
        assertEquals(listOf("knit"), items("Pull col rond"))
        assertTrue(items("Sweat à capuche Nike").contains("hoodie"))
    }

    @Test fun styleAndPieceMeanAStreetwearKnit() {
        val targets = Planner.targetsForProfile(listOf("knit", "Streetwear"), emptyList())
        assertTrue("no generic 'any knit' target", targets.none { it.origin == "keywords" })
        val t = targets.single()
        assertEquals(listOf("knit"), t.items)
        assertTrue("stussy" in t.brands && "nike" in t.brands)

        val ranker = Ranker(targets, nowSeconds = now)
        val good = l("Pull en maille Stüssy", 45.0, "Stüssy")
        val ranked = ranker.rank(listOf(
            good,
            l("Jersey de lana de botones", 7.0, "Uniqlo"),
            l("Foulard jersey premium", 2.0, "pas de marque"),
            l("Jersey", 3.0, null),
            l("Pulls à capuche homme", 4.5, "Puma"),
            l("Hoodie Nike Tech", 20.0, "Nike"),
        ))
        assertEquals(listOf(good.id), ranked.map { it.listing.id })
    }

    @Test fun cheapOffStyleItemsAreNeverDeals() {
        val targets = Planner.targetsForProfile(listOf("knit", "Streetwear"), emptyList())
        val market = List(12) { l("Sweat à capuche Puma", 12.0 + it, "Puma") }
        val r = Ranker(targets, nowSeconds = now).score(market + l("Pulls à capuche homme", 4.5, "Puma"))
        assertTrue(r.none { it.ranked.deal?.isDeal == true })
    }

    @Test fun brandedKnitStealIsStillADeal() {
        val targets = Planner.targetsForProfile(listOf("knit", "Ralph Lauren"), emptyList())
        val market = List(14) { l("Pull Ralph Lauren", 30.0 + it * 3, "Ralph Lauren") }
        val steal = l("Pull torsadé Ralph Lauren", 10.0, "Polo Ralph Lauren")
        val r = Ranker(targets, nowSeconds = now).rank(market + steal).first { it.listing.id == steal.id }
        assertTrue(r.deal.toString(), r.deal!!.isDeal)
    }

    @Test fun lookAlikesWinForPhotoRefs() {
        val targets = listOf(Target(items = listOf("knit"), origin = "image:r1"))
        val twin = l("Pull", 20.0, null)
        val other = l("Pull col rond Zara", 20.0, "Zara")
        val ranked = Ranker(targets, nowSeconds = now).rank(listOf(twin, other), visual = mapOf(twin.id to 0.95, other.id to 0.10), filters = Filters(minBeauty = 0.0))
        assertEquals(twin.id, ranked.first().listing.id)
        assertTrue(ranked.size == 1 || ranked[1].relevance < 0.55)
    }
}
