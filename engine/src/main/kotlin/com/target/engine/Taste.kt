package com.target.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * What the on-device vision model (MobileCLIP) sees in a listing photo, expressed with texts encoded offline:
 * garment category probabilities, aesthetic probes and style affinities.
 */
@Serializable
data class PhotoSignals(
    /** Softmax over garment categories (concept ids). */
    val category: Map<String, Double>,
    /** P(positive description) for each aesthetic probe (big_logo, neon, wrinkled, damaged, badphoto, noble, patina, stylish). */
    val probes: Map<String, Double>,
    /** Raw positive-minus-negative logits, used for calibration inside a result set. */
    val logits: Map<String, Double>,
    /** Cosine similarity to each fashion style. */
    val styles: Map<String, Double>,
    /** P(beautiful) from the head learnt on the user's examples (null if the model has no head). */
    val beauty: Double? = null,
) {
    fun top(): Pair<String, Double>? = category.maxByOrNull { it.value }?.toPair()
    fun probe(name: String) = probes[name] ?: 0.0
}

/** Text side of the vision model, generated offline (tools/taste) and shipped as JSON. */
class TasteModel(
    val name: String,
    val modelUrl: String,
    val sha256: String,
    val bytes: Long,
    val inputSize: Int,
    private val logitScale: Double,
    private val categories: Map<String, FloatArray>,
    private val probes: Map<String, Pair<FloatArray, FloatArray>>,
    private val styles: Map<String, FloatArray>,
    private val head: Head? = null,
) {
    /** Logistic regression on probe logits, fitted on labelled beautiful / ugly examples (tools/taste/fit_head.py). */
    class Head(val features: List<String>, val mean: DoubleArray, val std: DoubleArray, val w: DoubleArray, val b: Double) {
        fun predict(logits: Map<String, Double>): Double? {
            var z = b
            for (i in features.indices) z += w[i] * (((logits[features[i]] ?: return null) - mean[i]) / std[i])
            return 1.0 / (1.0 + exp(-z))
        }
    }

    fun analyze(embedding: FloatArray): PhotoSignals {
        val e = normalize(embedding)
        val catLogits = categories.mapValues { logitScale * dot(e, it.value) }
        val m = catLogits.values.maxOrNull() ?: 0.0
        val exps = catLogits.mapValues { exp(it.value - m) }
        val sum = exps.values.sum()
        val probs = exps.mapValues { it.value / sum }
        val logits = probes.mapValues { (_, pn) -> logitScale * (dot(e, pn.first) - dot(e, pn.second)) }
        return PhotoSignals(
            category = probs,
            probes = logits.mapValues { 1.0 / (1.0 + exp(-it.value)) },
            logits = logits,
            styles = styles.mapValues { dot(e, it.value) },
            beauty = head?.predict(logits),
        )
    }

    companion object {
        fun parse(json: String): TasteModel {
            val o = VintedJson.json.parseToJsonElement(json) as JsonObject
            fun vec(e: Any?): FloatArray = (e as JsonArray).map { (it as JsonPrimitive).doubleOrNull!!.toFloat() }.toFloatArray()
            fun s(k: String) = (o[k] as JsonPrimitive).content
            return TasteModel(
                name = s("name"), modelUrl = s("url"), sha256 = s("sha256"),
                bytes = (o["bytes"] as JsonPrimitive).longOrNull ?: 0L,
                inputSize = (o["size"] as JsonPrimitive).content.toInt(),
                logitScale = (o["logitScale"] as JsonPrimitive).doubleOrNull ?: 100.0,
                categories = (o["categories"] as JsonObject).mapValues { vec(it.value) },
                probes = (o["probes"] as JsonObject).mapValues { (_, v) -> (v as JsonObject).let { vec(it["pos"]) to vec(it["neg"]) } },
                styles = (o["styles"] as JsonObject).mapValues { vec(it.value) },
                head = (o["head"] as? JsonObject)?.let { h ->
                    fun arr(k: String) = (h[k] as JsonArray).map { (it as JsonPrimitive).doubleOrNull!! }.toDoubleArray()
                    Head((h["features"] as JsonArray).map { (it as JsonPrimitive).content }, arr("mean"), arr("std"), arr("w"), (h["b"] as JsonPrimitive).doubleOrNull!!)
                },
            )
        }

        fun dot(a: FloatArray, b: FloatArray): Double { var s = 0.0; for (i in a.indices) s += a[i] * b[i]; return s }
        fun normalize(a: FloatArray): FloatArray {
            var n = 0.0; for (x in a) n += x * x
            val d = sqrt(n).toFloat().takeIf { it > 0 } ?: 1f
            return FloatArray(a.size) { a[it] / d }
        }
        fun cosine(a: FloatArray, b: FloatArray): Double = dot(normalize(a), normalize(b))
    }
}

@Serializable
data class Beauty(val score: Double, val reasons: List<String>, val fromPhoto: Boolean)

enum class CategoryFit { CONFIRMED, PLAUSIBLE, MISMATCH }

/**
 * "What is a beautiful garment" — rules written with the user and refined on their own labelled examples
 * (10 ugly + 15 beautiful Vinted listings, see UserExamplesTest):
 *  1. material & texture: noble fibres, chunky / fuzzy / mohair knits, canvas, washed denim
 *  2. classic cuts: Harrington, trucker, work jacket, vintage track jacket, crewneck
 *  3. palette: one or two quiet colours (navy, black, cream, beige, khaki, washed blue) — never loud saturated
 *     clashes (bright orange, red, purple sleeves, colourblock + yellow zip)
 *  4. graphics: a big logo is fine when it is part of the piece (monochrome knit intarsia, gold embroidery, tonal
 *     stitching); it is ugly when it is merch printed on a cotton hoodie, or a busy all-over / camo / Nordic pattern
 *  5. iconic pieces of each brand; the same brand can be beautiful or ugly (Carhartt OG jacket vs orange script sweat)
 *  6. make & age: vintage and patina are a plus on workwear / denim, damage ruins fine knitwear; kids sizes and
 *     club merch are out
 *  7. presentation of the photo, 8. coherence with what is searched (category check)
 * The photo part is a logistic head on MobileCLIP probes fitted on those examples (tools/taste/fit_head.py).
 */
object Taste {
    const val SHOW_MIN_PHOTO = 6.0
    const val SHOW_MIN_TEXT = 5.5
    const val DEAL_MIN = 7.0

    /** Garment families used to check that the photo shows what was asked. */
    val FAMILY: Map<String, String> = buildMap {
        listOf("knit", "cardigan", "quarterzip").forEach { put(it, "knit") }
        listOf("sweatshirt", "hoodie", "fleece", "tracksuit", "joggers").forEach { put(it, "sweat") }
        listOf("tshirt", "polo", "rugby", "shirt", "top", "jersey").forEach { put(it, "top") }
        listOf("jacket", "coat", "puffer", "bodywarmer", "windbreaker", "blazer", "leatherjacket", "bomber", "overshirt", "suit").forEach { put(it, "outer") }
        listOf("jeans", "trousers", "chinos", "cargo", "shorts", "skirt").forEach { put(it, "bottom") }
        put("dress", "dress")
        listOf("sneakers", "boots", "loafers", "shoes").forEach { put(it, "shoes") }
        listOf("bag", "cap", "beanie", "scarf", "belt", "sunglasses", "watch").forEach { put(it, "acc") }
    }
    /** Families that are easily confused on a small photo (a plain fine knit looks like a sweatshirt). */
    private val SISTERS = setOf("knit|sweat", "sweat|knit", "top|knit", "knit|top", "outer|sweat", "sweat|outer")

    /**
     * Does the photo show the requested kind of piece?
     * The title is trusted unless the photo strongly says otherwise; a vague title needs the photo to agree.
     */
    fun categoryFit(requested: Collection<String>, titleItems: Set<String>, titleOnlyWeak: Boolean, photo: PhotoSignals): CategoryFit {
        if (requested.isEmpty()) return CategoryFit.PLAUSIBLE
        val fams = requested.mapNotNull { FAMILY[it] }.toSet()
        val famP = photo.category.entries.filter { FAMILY[it.key] in fams }.sumOf { it.value }
        val (topCat, topP) = photo.top() ?: return CategoryFit.PLAUSIBLE
        val topFam = FAMILY[topCat]
        val titleSays = titleItems.any { FAMILY[it] in fams }
        if (topFam in fams || famP >= 0.5) return CategoryFit.CONFIRMED
        val sister = fams.any { "$it|$topFam" in SISTERS }
        return when {
            titleSays && !titleOnlyWeak -> if (topP >= 0.95 && !sister) CategoryFit.MISMATCH else CategoryFit.PLAUSIBLE
            titleSays -> if ((sister && topP >= 0.9) || (!sister && topP >= 0.6)) CategoryFit.MISMATCH else CategoryFit.PLAUSIBLE
            famP >= 0.2 -> CategoryFit.PLAUSIBLE
            else -> CategoryFit.MISMATCH
        }
    }

    /** Mean and standard deviation of each probe logit over a result set (relative calibration). */
    class Batch(photos: Collection<PhotoSignals>) {
        private val stats: Map<String, Pair<Double, Double>> = photos.flatMap { it.logits.entries }.groupBy({ it.key }, { it.value }).mapValues { (_, v) ->
            val m = v.average(); val sd = sqrt(v.sumOf { (it - m) * (it - m) } / max(1, v.size - 1))
            m to sd.coerceAtLeast(0.5)
        }
        val size = photos.size
        fun z(p: PhotoSignals, probe: String): Double? {
            if (size < 8) return null
            val (m, sd) = stats[probe] ?: return null
            return ((p.logits[probe] ?: return null) - m) / sd
        }
    }

    // ---- knowledge written in advance -------------------------------------------------------------------------

    private fun words(vararg w: String) = w.map { Text.tokens(it) }
    private val NOBLE = words("cachemire", "cashmere", "kaschmir", "cachemira", "mohair", "merino", "mérinos", "laine", "wool", "wolle", "lana",
        "lambswool", "laine d agneau", "alpaga", "alpaca", "soie", "silk", "seide", "lin", "linen", "leinen", "cuir", "leather", "leder", "pelle",
        "daim", "suede", "wildleder", "velours cotele", "corduroy", "cord", "tweed", "shetland", "harris tweed", "canvas", "duck canvas", "selvedge", "raw denim")
    private val TEXTURE = words("torsade", "torsadee", "torsades", "cable", "cable knit", "zopfmuster", "trecce", "ochos", "aran", "fisherman", "chunky", "grosse maille",
        "cotele", "ribbed", "irlandais", "waffle", "intarsia", "mohair")
    /** Nordic / ski / Christmas jacquards: busy patterns the user finds ugly even in wool. */
    private val NORDIC = words("nordique", "nordic", "norvegien", "norwegian", "fair isle", "jacquard nordique", "flocon", "flocons", "snowflake",
        "noel", "christmas", "ugly sweater", "motif ski", "islandais", "icelandic")
    private val BUSY = words("all over", "allover", "all over print", "camouflage", "camo", "colorblock", "color block", "colourblock", "multicolore",
        "multicolor", "multicolour", "tie dye", "tie and dye", "patchwork")
    private val EMBROIDERED = words("brode", "brodee", "embroidered", "ricamo", "ricamato", "ricamata", "bestickt", "bordado", "geborduurd")
    private val LOUD_COLORS = words("orange", "rouge", "red", "rosso", "rot", "rojo", "violet", "purple", "viola", "jaune", "yellow", "giallo",
        "turquoise", "fuchsia", "fluo", "electrique")
    private val MUTED_COLORS = words("marine", "navy", "noir", "black", "nero", "schwarz", "blanc", "white", "bianco", "creme", "ecru", "panna",
        "beige", "camel", "kaki", "khaki", "olive", "gris", "grey", "gray", "grigio", "marron", "brown", "marrone", "chocolat", "delave", "taupe", "sable")
    /** Children's sizes: never what an adult hunts for. */
    private val KIDS = words("enfant", "enfants", "kids", "kid", "junior", "boys", "girls", "bebe", "baby", "bambino", "bambina", "nino", "nina")
    /** "13/15 anni", "12 ans", "10-12 years" (ages up to 16; "anni 90" is a decade, not an age). */
    private val KID_AGE = Regex("""(?<![\d])(1[0-6]|[2-9])\s*([/-]\s*(1[0-6]|[2-9])\s*)?(ans|anni|jahre|years|yrs|y)\b""")
    private val MERCH = words("psg", "paris saint germain", "real madrid", "jordan paris")
    private val HERITAGE = words("vintage", "made in usa", "made in italy", "made in scotland", "made in england", "made in japan", "made in uk",
        "single stitch", "deadstock", "archive", "90s", "80s", "annees 90", "retro")
    private val NEON = words("fluo", "fluorescent", "neon", "néon", "flashy")
    private val BIG_LOGO = words("gros logo", "big logo", "logo xxl", "maxi logo", "grand logo", "logo imprime", "printed logo", "spell out")
    private val DAMAGE = words("tache", "taches", "tachee", "stain", "stained", "trou", "troue", "hole", "holes", "bouloche", "bouloches", "pilling",
        "abime", "abimee", "damaged", "defaut", "defauts", "defect", "dechire", "dechiree", "ripped", "feutre", "feutree", "felted", "decolore", "decoloree", "boulochee")
    private val WEAR = words("delave", "delavee", "faded", "washed", "use", "usee", "worn", "worn in", "patine", "patinee", "patina", "distressed", "thrashed", "sun faded")
    private val SYNTHETIC = words("polyester", "acrylique", "acrylic", "polyamide", "elasthanne")

    /** Iconic models (+) and pieces to avoid (−) per brand id. */
    private val BRAND_KB: Map<String, Pair<List<List<String>>, List<List<String>>>> = mapOf(
        "carhartt" to (words("detroit", "active jacket", "chore", "michigan", "santa fe", "double knee", "og", "duck", "sherpa", "carpenter", "single knee", "nimbus", "anorak", "detroit jacket", "og jacket") to words()),
        "ralphlauren" to (words("cable", "torsade", "polo bear", "rugby", "oxford", "harrington", "cardigan", "quarter zip", "half zip", "rrl", "cashmere", "golf") to words("big pony")),
        "stussy" to (words("8 ball", "8ball", "world tour", "stock", "dice", "basic", "sherpa", "knit", "sweater") to words()),
        "thenorthface" to (words("nuptse", "1996", "retro", "denali", "mountain jacket", "himalayan", "steep tech", "purple label") to words("fluo")),
        "arcteryx" to (words("beta", "alpha", "atom", "gamma", "veilance", "covert") to words()),
        "barbour" to (words("bedale", "beaufort", "border", "transport", "ashby", "wax", "waxed") to words("gilet matelasse")),
        "levis" to (words("501", "505", "type 3", "trucker", "big e", "orange tab", "silver tab", "made in usa") to words()),
        "lacoste" to (words("vintage", "chemise lacoste", "l 12 12", "made in france") to words()),
        "patagonia" to (words("retro x", "retro pile", "synchilla", "snap t", "baggies", "torrentshell", "fleece") to words()),
        "stoneisland" to (words("badge", "ghost", "shadow project", "nylon metal", "membrana", "ice jacket") to words()),
        "cpcompany" to (words("goggle", "lens", "mille miglia") to words()),
        "drmartens" to (words("1460", "1461", "made in england", "vintage") to words()),
        "moncler" to (words("maya", "grenoble", "genius") to words()),
        "supreme" to (words("box logo", "bogo", "small box", "pile", "yohji", "sweater") to words()),
        "nike" to (words("acg", "center swoosh", "track jacket", "dunk", "air max 1", "air force 1", "cortez") to words("tech fleece")),
        "jordan" to (words() to words("jumpman", "paris", "psg")),
        "corteiz" to (words("cstar", "mohair", "alcatraz", "rtw", "zip", "zippe") to words()),
        "offwhite" to (words("arrows", "mohair", "diag") to words()),
        "lee" to (words("trucker", "101", "storm rider", "rider") to words()),
        "bape" to (words("shark", "camo hoodie") to words()),
        "adidas" to (words("firebird", "samba", "gazelle", "spezial", "vintage", "track top", "beckenbauer") to words()),
        "newbalance" to (words("990", "991", "992", "993", "2002r", "made in usa", "made in uk") to words()),
        "salomon" to (words("xt 6", "xt6", "acs pro", "speedcross") to words()),
        "fredperry" to (words("twin tipped", "m12", "harrington") to words()),
        "schott" to (words("perfecto", "618", "613", "peacoat") to words()),
        "dickies" to (words("874", "eisenhower", "double knee", "carpenter") to words()),
        "saintjames" to (words("mariniere", "binic", "guildo", "matelot") to words()),
        "acnestudios" to (words("face", "mohair", "jeans") to words()),
        "apc" to (words("petit standard", "new standard", "denim") to words()),
        "kapital" to (words("boro", "century denim", "smiley", "ring coat") to words()),
    )
    private val NO_BRAND = setOf("pas de marque", "sans marque", "no brand", "unbranded", "keine marke", "senza marca", "sin marca", "autre", "autres", "other")
    private val LOGO_CULTURE = setOf("supreme", "stussy", "palace", "carhartt", "bape", "corteiz", "thrasher", "champion")
    private val WORKWEAR_BRANDS = setOf("carhartt", "dickies", "levis", "redwing", "lee", "wrangler", "schott", "barbour", "belstaff", "kapital", "evisu", "patagonia")
    private val WORKWEAR_ITEMS = setOf("jeans", "leatherjacket", "boots", "overshirt")
    private val DELICATE_ITEMS = setOf("knit", "cardigan", "quarterzip")

    private fun hits(tokens: List<String>, list: List<List<String>>) = list.count { it.isNotEmpty() && Text.containsPhrase(tokens, it) }

    /** Beauty score in [0, 10] with human-readable reasons ("+ matière noble", "− gros logo imprimé"...). */
    fun judge(listing: Listing, brand: Brand?, items: Set<String>, photo: PhotoSignals?, batch: Batch?): Beauty {
        val t = Text.tokens(listing.title + " " + (listing.description ?: "").take(300))
        val reasons = ArrayList<String>()
        var s = 5.0
        fun add(v: Double, why: String) { s += v; if (abs(v) >= 0.3) reasons += (if (v > 0) "+ " else "− ") + why }

        val workwear = brand?.id in WORKWEAR_BRANDS || items.any { it in WORKWEAR_ITEMS }
        val delicate = items.any { it in DELICATE_ITEMS }

        // brand & iconic pieces
        when (brand?.tier) {
            Tier.LUXURY -> add(0.6, "marque de créateur")
            Tier.PREMIUM -> add(0.5, "marque de qualité")
            Tier.MID -> Unit
            Tier.FAST -> add(-1.5, "fast fashion")
            null -> if (listing.brand != null && Text.normalize(listing.brand) in NO_BRAND) add(-1.5, "sans marque") else add(-1.0, "marque inconnue")
        }
        BRAND_KB[brand?.id]?.let { (iconic, avoid) ->
            if (hits(t, iconic) > 0) add(1.0, "pièce iconique de ${brand!!.name}")
            if (hits(t, avoid) > 0) add(-1.0, "modèle peu désirable")
        }
        // 1. material & texture
        val noble = hits(t, NOBLE); val texture = hits(t, TEXTURE)
        if (noble > 0) add(min(1.5, 1.0 + 0.25 * (noble - 1)), "matière noble")
        if (texture > 0) add(0.7, "belle texture (torsades, côtes…)")
        if (hits(t, SYNTHETIC) > 0 && noble == 0) add(-0.6, "matière synthétique")
        // 3. colour, 4. logo
        if (hits(t, NEON) > 0) add(-2.0, "couleur fluo")
        if (hits(t, BIG_LOGO) > 0) add(-1.0, "gros logo")
        if (hits(t, NORDIC) > 0) add(-3.5, "motif nordique / chargé")
        if (hits(t, BUSY) > 0) add(-1.0, "imprimé ou colorblock chargé")
        if (hits(t, EMBROIDERED) > 0) add(0.5, "logo brodé")
        if (hits(t, KIDS) > 0 || KID_AGE.containsMatchIn(listing.title.lowercase())) add(-3.0, "taille enfant")
        if (hits(t, MERCH) > 0 && "jersey" !in items) add(-1.0, "merch de club")
        val loud = hits(t, LOUD_COLORS) > 0; val muted = hits(t, MUTED_COLORS) > 0
        if (loud && !muted) add(-0.4, "couleur vive") else if (muted && !loud) add(0.3, "couleur sobre")
        // 6. make, age, wear (context dependent)
        if (hits(t, HERITAGE) > 0) add(0.5, "vintage / fabrication d'origine")
        val wear = hits(t, WEAR) > 0
        val damage = hits(t, DAMAGE) > 0
        if (wear) add(if (workwear) 0.6 else if (delicate) -0.8 else -0.3, if (workwear) "belle patine" else "usure visible")
        if (damage) add(if (delicate) -2.0 else if (workwear) -0.6 else -1.5, "défauts (taches, trous, bouloches)")
        when (listing.condition) {
            Condition.NEW_WITH_TAGS -> add(0.2, "neuf avec étiquette")
            Condition.SATISFACTORY -> add(if (workwear) -0.4 else -1.0, "état moyen")
            else -> Unit
        }

        // what the photo shows
        if (photo != null) {
            val head = photo.beauty
            if (head != null) {
                // learnt on the user's own beautiful / ugly examples: palette, print, classic cut, knit texture...
                // the head was trained on garments: on accessories its opinion counts half
                val weight = if (photo.top()?.first?.let { FAMILY[it] } == "acc") 2.5 else 5.0
                add((head - 0.5) * weight, if (head >= 0.5) "allure qui correspond à tes exemples de beaux vêtements" else "ressemble à tes exemples de vêtements moches")
                fun lg(n: String) = photo.logits[n] ?: 0.0
                if (lg("tonal") >= 4.5 && lg("loud_colours") < 2.0) reasons += "+ palette sobre (photo)"
                if (lg("loud_colours") >= 6.5) reasons += "− couleurs criardes (photo)"
                if (lg("printed_hoodie") >= 6.0) reasons += "− gros logo imprimé façon merch (photo)"
                if (lg("busy_print") >= 3.0) reasons += "− imprimé chargé (photo)"
                if (lg("classic_piece") >= 5.0) reasons += "+ coupe classique intemporelle (photo)"
                if (lg("intarsia_knit") >= 5.0) reasons += "+ belle maille (photo)"
            } else {
                val stylishZ = batch?.z(photo, "stylish")
                val styl = stylishZ?.let { (0.6 * it).coerceIn(-1.0, 1.2) } ?: ((photo.probe("stylish") - 0.3) * 2.5).coerceIn(-1.0, 1.2)
                add(styl, if (styl >= 0) "coupe et allure soignées (photo)" else "allure peu flatteuse (photo)")
                if (photo.probe("big_logo") >= 0.85) add(if (brand?.id in LOGO_CULTURE) -0.6 else -1.5, "gros logo imprimé (photo)")
            }
            if (photo.probe("neon") >= 0.95 && !delicate) add(-1.5, "couleurs fluo (photo)")
            if (photo.probe("wrinkled") >= 0.8) add(-0.5, "froissé (photo)")
            if (photo.probe("damaged") >= 0.8) add(if (workwear) -0.3 else -1.0, "semble abîmé (photo)")
            if ((batch?.z(photo, "badphoto") ?: 0.0) >= 1.8) add(-0.4, "photo de mauvaise qualité")
        }
        return Beauty(s.coerceIn(0.0, 10.0), reasons, photo != null)
    }

    /** How much a photo looks like the given styles, relative to the average style affinity (≈ −1 … +1). */
    fun styleAffinity(photo: PhotoSignals, styleIds: Collection<String>): Double? {
        if (styleIds.isEmpty() || photo.styles.isEmpty()) return null
        val mean = photo.styles.values.average()
        val best = styleIds.mapNotNull { photo.styles[it] }.maxOrNull() ?: return null
        return ((best - mean) * 25).coerceIn(-1.0, 1.0)
    }
}
