package com.target.engine

import org.junit.Assert.*
import org.junit.Test

class RankerTest {
    private val now = 1_800_000_000L
    private var nextId = 1L

    private fun l(
        title: String, price: Double, brand: String? = null, cond: Condition = Condition.VERY_GOOD,
        ageH: Double = 30.0, favs: Int = 0, currency: String = "EUR",
    ) = Listing(
        id = nextId++, title = title, price = price, currency = currency, brand = brand, condition = cond,
        url = "https://www.vinted.fr/items/$nextId", createdAt = now - (ageH * 3600).toLong(), favourites = favs,
    )

    /** A realistic market of Ralph Lauren knits. */
    private fun rlMarket() = listOf(25.0, 28.0, 30.0, 32.0, 35.0, 35.0, 38.0, 40.0, 42.0, 45.0, 50.0, 55.0, 60.0, 30.0, 33.0)
        .map { l("Pull Ralph Lauren col rond", it, "Ralph Lauren") }

    private fun ranker(q: String, s: Sensitivity = Sensitivity.NORMAL) = Ranker(Planner.targetsForSearch(q), s, now)

    @Test fun realRalphLaurenKnitAtTenEurosIsADeal() {
        val deal = l("Pull Ralph Lauren torsadé", 10.0, "Polo Ralph Lauren", ageH = 0.5)
        val ranked = ranker("knit ralph lauren").rank(rlMarket() + deal)
        val r = ranked.first { it.listing.id == deal.id }
        assertTrue("verdict=${r.deal}", r.deal!!.isDeal)
        assertEquals(deal.id, ranked.first().listing.id)
        assertTrue(r.discountPct > 50)
    }

    @Test fun normalPricesAreNotDeals() {
        val market = rlMarket()
        val ranked = ranker("knit ralph lauren").rank(market + l("Pull Ralph Lauren", 20.0, "Ralph Lauren") + l("Pull Ralph Lauren", 17.0, "Ralph Lauren"))
        assertTrue(ranked.none { it.deal!!.isDeal })
    }

    @Test fun sensitivityMovesTheThreshold() {
        val item = l("Pull Ralph Lauren", 15.0, "Ralph Lauren")
        val strictOrNormal = ranker("knit ralph lauren").rank(rlMarket() + item).first { it.listing.id == item.id }
        val wide = ranker("knit ralph lauren", Sensitivity.WIDE).rank(rlMarket() + item).first { it.listing.id == item.id }
        assertFalse(strictOrNormal.deal!!.isDeal)
        assertTrue(wide.deal!!.toString(), wide.deal!!.isDeal)
    }

    @Test fun fakesAndLookalikesAreNeverDeals() {
        val styleWord = l("Pull style Ralph Lauren", 8.0, "Zara")
        val undeclared = l("Pull ralph lauren", 9.0, null)
        val damaged = l("Pull Ralph Lauren troué", 9.0, "Ralph Lauren", cond = Condition.SATISFACTORY)
        val ranked = ranker("knit ralph lauren").rank(rlMarket() + listOf(styleWord, undeclared, damaged))
        for (x in listOf(styleWord, undeclared, damaged)) {
            val r = ranked.firstOrNull { it.listing.id == x.id } ?: continue
            assertFalse("${x.title}: ${r.deal}", r.deal!!.isDeal)
        }
        val gucci = l("Sac Gucci", 20.0, "Gucci")
        val g = ranker("sac gucci").rank(listOf(gucci) + List(6) { l("Sac Gucci Marmont", 300.0 + it * 40, "Gucci") }).first { it.listing.id == gucci.id }
        assertFalse(g.deal!!.isDeal)
        assertTrue(g.deal!!.suspectedFake)
    }

    @Test fun cheapFastFashionIsNotADeal() {
        val market = List(12) { l("T-shirt Zara basique", 4.0 + it, "Zara") }
        val cheap = l("T-shirt Zara", 1.0, "Zara")
        val r = ranker("t-shirt zara").rank(market + cheap).first { it.listing.id == cheap.id }
        assertFalse(r.deal!!.toString(), r.deal!!.isDeal)
    }

    @Test fun smallSampleFallsBackOnBrandPrior() {
        // only two comparables: the prior (~34 €) must drive the estimate
        val few = listOf(l("Pull Ralph Lauren", 90.0, "Ralph Lauren"), l("Pull Ralph Lauren", 95.0, "Ralph Lauren"))
        val target = l("Pull Ralph Lauren", 30.0, "Ralph Lauren")
        val r = ranker("knit ralph lauren").rank(few + target).first { it.listing.id == target.id }
        assertTrue(r.expectedPrice in 40.0..80.0)
        assertFalse(r.deal!!.isDeal)
    }

    @Test fun relevanceFiltersOutOtherBrandsAndPieces() {
        val ranked = ranker("knit ralph lauren").rank(rlMarket() + listOf(
            l("Jean Levi's 501", 15.0, "Levi's"),
            l("Polo Ralph Lauren rouge", 20.0, "Ralph Lauren"),   // right brand, wrong piece
            l("Pull Tommy Hilfiger", 20.0, "Tommy Hilfiger"),
            l("Strickpullover Ralph Lauren navy", 30.0, "Ralph Lauren"),
        ))
        assertTrue(ranked.none { it.listing.title.startsWith("Jean Levi") })
        val de = ranked.first { it.listing.title.startsWith("Strickpullover") }
        val polo = ranked.firstOrNull { it.listing.title.startsWith("Polo Ralph Lauren rouge") }
        val tommy = ranked.firstOrNull { it.listing.title.startsWith("Pull Tommy") }
        assertTrue(de.relevance > 0.9)
        if (polo != null) assertTrue(polo.relevance < de.relevance)
        if (tommy != null) assertTrue(tommy.relevance < 0.5)
    }

    @Test fun freshCheapItemsRankAboveOldExpensiveOnes() {
        val fresh = l("Pull Ralph Lauren", 22.0, "Ralph Lauren", ageH = 0.3)
        val old = l("Pull Ralph Lauren", 22.0, "Ralph Lauren", ageH = 200.0)
        val pricey = l("Pull Ralph Lauren", 70.0, "Ralph Lauren", ageH = 0.3)
        val ranked = ranker("knit ralph lauren").rank(rlMarket() + listOf(fresh, old, pricey))
        val pos = { x: Listing -> ranked.indexOfFirst { it.listing.id == x.id } }
        assertTrue(pos(fresh) < pos(old))
        assertTrue(pos(fresh) < pos(pricey))
        assertEquals(fresh.id, Ranker.sort(ranked, SortMode.NEWEST).first { it.listing.price == 22.0 }.listing.id)
        val byPrice = Ranker.sort(ranked, SortMode.PRICE).map { it.listing.buyerPrice }
        assertEquals(byPrice.sorted(), byPrice)
    }

    @Test fun filtersApply() {
        val a = l("Pull Ralph Lauren", 20.0, "Ralph Lauren").copy(size = "M / 38 / 10")
        val b = l("Pull Ralph Lauren", 20.0, "Ralph Lauren").copy(size = "XL")
        val c = l("Pull Ralph Lauren", 90.0, "Ralph Lauren").copy(size = "M")
        val ranked = ranker("knit ralph lauren").rank(listOf(a, b, c), filters = Filters(maxPrice = 50.0, sizes = listOf("M")))
        assertEquals(listOf(a.id), ranked.map { it.listing.id })
    }

    @Test fun duplicatesAreMergedAndForeignCurrenciesConverted() {
        val pln = l("Sweter Ralph Lauren", 100.0, "Ralph Lauren", currency = "PLN") // ≈ 25 €
        val dup = pln.copy()
        val ranked = ranker("knit ralph lauren").rank(rlMarket() + listOf(pln, dup), hits = mapOf(pln.id to 3))
        assertEquals(1, ranked.count { it.listing.id == pln.id })
        val r = ranked.first { it.listing.id == pln.id }
        assertEquals(3, r.hits)
        assertTrue(r.valueRatio > 1.0)
    }

    @Test fun visualSimilarityBoostsLookalikes() {
        val a = l("Pull col rond", 15.0, null)
        val b = l("Pull col rond", 15.0, null)
        val targets = listOf(Target(items = listOf("knit"), origin = "image:x"))
        val ranked = Ranker(targets, nowSeconds = now).rank(listOf(a, b), visual = mapOf(a.id to 0.85, b.id to 0.3))
        assertEquals(a.id, ranked.first().listing.id)
    }
}
