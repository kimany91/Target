package com.target.engine

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The user's own labelled examples (10 ugly, 12 beautiful Vinted listings + 3 Ralph Lauren knits), with their real
 * titles and photos (MobileCLIP-S2 embeddings): every one must land on the right side of the display threshold.
 */
class UserExamplesTest {
    private fun res(name: String) = javaClass.classLoader.getResource(name)!!.readText()
    private val model = TasteModel.parse(res("taste_s2.json"))
    private val photos = (VintedJson.json.parseToJsonElement(res("s2_taste_examples.json")) as JsonObject).mapValues { (_, v) ->
        model.analyze((v as JsonArray).map { it.toString().toFloat() }.toFloatArray())
    }

    private data class Ex(val key: String, val title: String, val brand: String, val beautiful: Boolean, val cond: Condition = Condition.VERY_GOOD)

    private val examples = listOf(
        Ex("bad__carhartt_orange_script_crew", "Sweat Carhartt WIP S orange logo brodé crewneck streetwear workwear", "Carhartt", false),
        Ex("bad__carhartt_nordic_jacquard_knit", "Pull épais en laine col rond type Jacquard Nordique Bleu Marine taille S Carhartt WIP Pearson", "Carhartt", false),
        Ex("bad__nike_allover_comic_zip_hoodie", "Veste à capuche Nike brode vintage 00's gris sweat streetwear S", "Nike", false),
        Ex("bad__jordan_kids_zip_hoodie_taping", "Vintage Jordan Felpa Full Zip Hoodie Nero Rosso Logo Air Jordan Streetwear Y2K Retro 13/15 Anni", "Jordan", false),
        Ex("bad__aape_layered_hoodie_sleeve_print", "Sweat à capuche Streetwear manches longues 100% coton noir Homme AAPE by A Bathing Ape Bape taille S", "AAPE by A Bathing Ape", false),
        Ex("bad__tnf_tan_hoodie_camo_backprint", "Felpa con Cappuccio The North Face Marrone / Camouflage Taglia S Uomo", "The North Face", false),
        Ex("bad__palace_pachinko_jersey", "Palace Pachinko jersey", "Palace", false),
        Ex("bad__jordan_psg_paris_hoodie", "Felpa con cappuccio Jordan Paris – PSG", "Jordan", false),
        Ex("bad__jordan_jumpman_red_hoodie", "felpa JORDAN jumpman", "Jordan", false),
        Ex("bad__tnf_colorblock_windbreaker", "Coupe-Vent The North Face S – Bleu Noir Colorblock – Veste à Capuche Outdoor Streetwear", "The North Face", false),
        Ex("good__nike_vintage_gold_embroidered_crew", "Felpa Nike Sportswear girocollo blu navy ricamo oro swoosh vintage y2k S", "Nike", true),
        Ex("good__corteiz_cstar_mohair_knit", "Pull Mohair Corteiz Cstar homme", "Corteiz", true),
        Ex("good__offwhite_arrows_mohair_knit", "Off-White Sweater", "Off-White", true),
        Ex("good__stussy_stock_intarsia_knit", "Stüssy – Stock Sweater Black", "Stüssy", true),
        Ex("good__supreme_yohji_666_knit", "Supreme x Y's by Yohji Yamamoto 666 Sweater, S", "Supreme", true),
        Ex("good__rl_harrington_beige_vintage", "Vintage Polo Golf Ralph Lauren Harrington Jacket", "Ralph Lauren", true),
        Ex("good__corteiz_tonal_zip_hoodie", "Gilet zippé / Sweat à capuche Corteiz", "Corteiz", true),
        Ex("good__supreme_pile_crew_cream", "Felpa Supreme Pile Originale Colore Bianco Panna", "Supreme", true),
        Ex("good__nike_vintage_track_jacket_navy", "Vintage Nike Track Jacket Navy Blue White Piping Full Zip 90s Y2K Sportswear", "Nike", true),
        Ex("good__carhartt_og_active_jacket", "OG jacket | carhartt | beige | neuve | matelassée", "Carhartt", true, Condition.NEW_WITH_TAGS),
        Ex("good__lee_vintage_trucker_denim", "Veste Jean Lee Vintage Femme XL Bleu Délavé Denim Trucker Années 90 Y2K", "Lee", true),
        Ex("good__rl_harrington_light_blue", "Polo Ralph Lauren Harrington Jacke / Baumwolljacke (Hellblau)", "Ralph Lauren", true),
        Ex("A_red_rl_cable_knit", "Pull torsadé Ralph Lauren", "Ralph Lauren", true),
        Ex("A_black_rl_knit", "Ralph Lauren pull noir col rond", "Polo Ralph Lauren", true),
        Ex("A_brown_rl_knit", "Pull Polo Ralph Lauren laine d'agneau", "Polo Ralph Lauren", true),
    )

    @Test fun everyExampleIsJudgedLikeTheUser() {
        val batch = Taste.Batch(photos.values)
        val wrong = ArrayList<String>()
        for (e in examples) {
            val l = Listing(1, e.title, 30.0, brand = e.brand, condition = e.cond, url = "u")
            val items = Lexicon.detectItems(Text.tokens(e.title)).map { it.id }.toSet()
            val b = Taste.judge(l, Brands.fromBrandField(e.brand), items, photos[e.key]!!, batch)
            val ok = if (e.beautiful) b.score >= Taste.SHOW_MIN_PHOTO else b.score < Taste.SHOW_MIN_PHOTO
            println("%s %-44s %4.1f  P(beau photo)=%.2f  %s".format(if (ok) "✓" else "✗", e.key, b.score, photos[e.key]!!.beauty, b.reasons.joinToString(" | ")))
            if (!ok) wrong += e.key
        }
        assertTrue("misjudged: $wrong", wrong.isEmpty())
    }

    @Test fun headIsShipped() {
        assertTrue(photos.values.all { it.beauty != null })
    }
}
