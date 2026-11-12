package com.target.engine

import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test

/**
 * End-to-end check of the beauty / category judgement on real Vinted photos (crops of the user's screenshots),
 * embedded with MobileCLIP-S2 exactly like the phone does.
 */
class TasteTest {
    companion object {
        lateinit var model: TasteModel
        lateinit var photos: Map<String, PhotoSignals>

        @BeforeClass @JvmStatic fun load() {
            model = TasteModel.parse(res("taste_s2.json"))
            val emb = VintedJson.json.parseToJsonElement(res("s2_listing_photos.json")) as kotlinx.serialization.json.JsonObject
            photos = emb.mapValues { (_, v) ->
                model.analyze((v as kotlinx.serialization.json.JsonArray).map { it.toString().toFloat() }.toFloatArray())
            }
        }
        private fun res(name: String) = TasteTest::class.java.classLoader.getResource(name)!!.readText()
    }

    private var id = 1L
    private fun l(title: String, brand: String?, cond: Condition = Condition.VERY_GOOD, price: Double = 20.0) =
        Listing(id++, title, price, brand = brand, condition = cond, url = "u")

    /** The listings behind the photos, with realistic titles. */
    private val listings = mapOf(
        "A_red_rl_cable_knit" to l("Pull torsadé Ralph Lauren", "Ralph Lauren", price = 13.0),
        "A_black_rl_knit" to l("Ralph Lauren pull noir col rond", "Polo Ralph Lauren", price = 27.0),
        "A_brown_rl_knit" to l("Pull Polo Ralph Lauren laine d'agneau", "Polo Ralph Lauren", price = 30.0),
        "A_navy_rl_big_print_sweatshirt" to l("Mooie sweater", "Polo Ralph Lauren", price = 15.0),
        "A_nike_white_hoodie" to l("Nike Vintage Mens White Hoodie", "Nike", Condition.NEW, price = 8.99),
        "A_carhartt_anorak" to l("Veste Carhartt WIP anorak", "Carhartt", price = 30.0),
        "B_uniqlo_cardigan" to l("Jersey de lana de botones", "Uniqlo", Condition.NEW, price = 7.0),
        "B_scarf_jersey" to l("Foulard jersey premium", "pas de marque", Condition.NEW, price = 2.0),
        "B_pink_knit" to l("Jersey", null, price = 3.0),
        "B_puma_hoodie" to l("Pulls à capuche homme", "Puma", price = 4.5),
    )

    @Test fun visionModelRecognisesGarments() {
        assertEquals("knit", photos["A_red_rl_cable_knit"]!!.top()!!.first)
        assertEquals("cardigan", photos["B_uniqlo_cardigan"]!!.top()!!.first)
        assertEquals("windbreaker", photos["A_carhartt_anorak"]!!.top()!!.first)
        assertEquals("sweatshirt", photos["A_navy_rl_big_print_sweatshirt"]!!.top()!!.first)
        assertTrue(photos["A_navy_rl_big_print_sweatshirt"]!!.probe("big_logo") > 0.85)
        assertTrue(photos["A_red_rl_cable_knit"]!!.probe("big_logo") < 0.2)
    }

    @Test fun knitSearchKeepsOnlyKnitwear() {
        val fit = listings.mapValues { (k, l) ->
            val (items, weak) = Lexicon.itemEvidence(Text.tokens(l.title))
            Taste.categoryFit(listOf("knit"), items.map { it.id }.toSet(), weak, photos[k]!!)
        }
        val kept = fit.filterValues { it != CategoryFit.MISMATCH }.keys
        assertEquals(setOf("A_red_rl_cable_knit", "A_black_rl_knit", "A_brown_rl_knit", "B_uniqlo_cardigan", "B_pink_knit"), kept)
    }

    @Test fun beautyFollowsTheRules() {
        val batch = Taste.Batch(photos.values)
        val score = listings.mapValues { (k, l) ->
            val brand = Brands.fromBrandField(l.brand)
            val items = Lexicon.detectItems(Text.tokens(l.title)).map { it.id }.toSet()
            Taste.judge(l, brand, items, photos[k], batch)
        }
        score.forEach { (k, b) -> println("%-34s %4.1f  %s".format(k, b.score, b.reasons.joinToString(" | "))) }
        // beautiful: Ralph Lauren knitwear, a Carhartt jacket
        for (k in listOf("A_red_rl_cable_knit", "A_black_rl_knit", "A_brown_rl_knit", "A_carhartt_anorak")) assertTrue("$k ${score[k]}", score[k]!!.score >= Taste.SHOW_MIN_PHOTO)
        // not shown: big printed logo, no-name scarf/knit, hoodies sold as "pull"
        for (k in listOf("A_navy_rl_big_print_sweatshirt", "B_scarf_jersey", "B_pink_knit", "B_puma_hoodie")) assertTrue("$k ${score[k]}", score[k]!!.score < Taste.SHOW_MIN_PHOTO)
        // the cable knit is the most beautiful
        assertEquals("A_red_rl_cable_knit", score.maxBy { it.value.score }.key)
    }

    @Test fun textRulesWithoutPhoto() {
        fun b(t: String, brand: String?, cond: Condition = Condition.VERY_GOOD) =
            Taste.judge(l(t, brand, cond), Brands.fromBrandField(brand), Lexicon.detectItems(Text.tokens(t)).map { it.id }.toSet(), null, null).score
        for ((t, br) in listOf("Pull mohair blanc" to "Acne Studios", "Veste Carhartt Detroit délavée" to "Carhartt", "Pull cachemire bouloché" to "Ralph Lauren",
                "Veste The North Face fluo" to "The North Face", "Sweat Adidas gros logo" to "Adidas", "T-shirt Stüssy 8 ball" to "Stüssy", "Robe" to "Shein"))
            println("%-34s %4.1f".format(t, b(t, br)))
        assertTrue(b("Pull mohair blanc", "Acne Studios") >= 6.5)
        assertTrue(b("Veste Carhartt Detroit délavée", "Carhartt") >= 7.0)     // patina is beautiful on workwear
        assertTrue(b("Pull cachemire bouloché", "Ralph Lauren") < 5.5)          // and ruins fine knitwear
        assertTrue(b("Veste The North Face fluo", "The North Face") < 5.0)
        assertTrue(b("Sweat Adidas gros logo", "Adidas") < 5.0)
        assertTrue(b("T-shirt Stüssy 8 ball", "Stüssy") >= 6.5)
        assertTrue(b("Robe", "Shein") < 4.0)
    }
}
