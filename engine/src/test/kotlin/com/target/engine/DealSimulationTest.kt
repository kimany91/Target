package com.target.engine

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp
import kotlin.random.Random

/**
 * Monte-Carlo check of the deal threshold on realistic markets: many brands × pieces, log-normal asking prices,
 * mixed conditions. The threshold must stay quiet on ordinary listings and fire on genuinely exceptional prices.
 */
class DealSimulationTest {
    private val rnd = Random(42)
    private val now = 1_800_000_000L
    private var id = 1L

    private val cases = listOf(
        "Ralph Lauren" to "knit", "Lacoste" to "polo", "Carhartt" to "jacket", "The North Face" to "puffer",
        "Nike" to "sneakers", "Levi's" to "jeans", "Stüssy" to "hoodie", "Arc'teryx" to "windbreaker",
        "Zara" to "dress", "H&M" to "tshirt", "Moncler" to "puffer", "Barbour" to "jacket", "Adidas" to "tracksuit",
        "Tommy Hilfiger" to "shirt", "Dr. Martens" to "boots", "Sézane" to "cardigan",
    )

    private fun title(brand: String, item: String) = "${Lexicon.concept(item)!!.primary(Lang.FR)} $brand"

    private fun gaussian() = java.util.Random(rnd.nextLong()).nextGaussian()

    private fun market(brand: String, item: String, n: Int, sigma: Double = 0.35): List<Listing> {
        val b = Brands.byName(brand)!!
        val center = b.base * Lexicon.concept(item)!!.group!!.multiplier
        return List(n) {
            val cond = listOf(Condition.VERY_GOOD, Condition.VERY_GOOD, Condition.GOOD, Condition.NEW, Condition.NEW_WITH_TAGS).random(rnd)
            // log-normal spread (σ≈0.35) around a centre that varies ±30 % between real markets and the prior
            val p = center * 0.85 * exp(sigma * gaussian()) * cond.priceFactor
            Listing(id++, title(brand, item), Math.round(p * 2) / 2.0, brand = brand, condition = cond,
                url = "u", createdAt = now - rnd.nextLong(0, 72 * 3600))
        }
    }

    @Test fun ordinaryMarketsProduceAlmostNoDeals() = falsePositives(0.35, 0.005)

    @Test fun dispersedMarketsStayQuiet() = falsePositives(0.55, 0.012)

    private fun falsePositives(sigma: Double, maxRate: Double) {
        var total = 0
        var deals = 0
        repeat(6) {
            for ((brand, item) in cases) {
                val ranker = Ranker(Planner.targetsForSearch("$brand ${Lexicon.concept(item)!!.primary(Lang.FR)}"), nowSeconds = now)
                val ranked = ranker.rank(market(brand, item, 40, sigma))
                total += ranked.size
                deals += ranked.count { it.deal!!.isDeal }
            }
        }
        val rate = deals.toDouble() / total
        println("false-positive rate (sigma=$sigma): ${"%.2f".format(rate * 100)} % ($deals / $total)")
        assertTrue("too many deals on normal markets: $rate", rate < maxRate)
    }

    @Test fun exceptionalPricesAreCaught() {
        var injected = 0
        var caught = 0
        val missed = ArrayList<String>()
        for ((brand, item) in cases) {
            val b = Brands.byName(brand)!!
            if (b.tier == Tier.FAST) continue // fast fashion never has meaningful deals
            repeat(4) {
                val m = market(brand, item, 30)
                val median = Stats.median(m.map { it.buyerPrice })
                val steal = Listing(id++, title(brand, item), Math.round(median * 0.22).toDouble().coerceAtLeast(3.0), brand = brand,
                    condition = Condition.VERY_GOOD, url = "u", createdAt = now - 600)
                val ranked = Ranker(Planner.targetsForSearch("$brand ${Lexicon.concept(item)!!.primary(Lang.FR)}"), nowSeconds = now).rank(m + steal)
                val r = ranked.first { it.listing.id == steal.id }
                injected++
                if (r.deal!!.isDeal) caught++ else missed += "$brand $item ${steal.price}€ vs ~${median.toInt()}€: ${r.deal!!.reason}"
            }
        }
        println("recall on injected steals: $caught / $injected")
        missed.take(10).forEach { println("  missed: $it") }
        assertTrue("recall too low: $caught/$injected", caught >= injected * 0.85)
    }

    @Test fun fastFashionIsNeverFlagged() {
        for (brand in listOf("Zara", "H&M", "Shein", "Primark")) {
            val m = List(30) { Listing(id++, "Robe $brand", 4.0 + rnd.nextInt(0, 12), brand = brand, condition = Condition.VERY_GOOD, url = "u", createdAt = now) }
            val cheap = Listing(id++, "Robe $brand", 1.0, brand = brand, condition = Condition.NEW_WITH_TAGS, url = "u", createdAt = now)
            val r = Ranker(Planner.targetsForSearch("robe $brand"), nowSeconds = now).rank(m + cheap)
            assertTrue(r.none { it.deal!!.isDeal })
        }
    }
}
