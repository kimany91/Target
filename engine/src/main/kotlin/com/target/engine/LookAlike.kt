package com.target.engine

import kotlin.math.sqrt

/**
 * "Does this listing look like my refs?" — measured so that it survives the difference between a Pinterest outfit
 * (a person in a mirror) and a Vinted photo (a sweater on a bed).
 *
 * Live check (3 Pinterest outfits × 141 Vinted photos): the raw image similarity is 0.38–0.70 (median 0.47), it was
 * cut at 0.6 so 90 % of listings got nothing, and its best matches were simply other mirror selfies. What the person
 * means by "like this" is the colours, the material / pattern and the graphics of the piece: those come from the
 * model's colour / material / aesthetic readings and dominate the score; the image similarity of the piece's crop
 * adds the shape. Both are made relative to the current result set (z-scores), then turned into a 0–1 rank.
 */
object LookAlike {
    private val PROBES = listOf("busy_print", "intarsia_knit", "tonal", "loud_colours", "big_logo", "printed_hoodie", "knitwear", "noble")
    const val ATTR_WEIGHT = 0.65

    /** Colour + material/pattern distributions (square roots, so the cosine is a Bhattacharyya overlap) + probes. */
    fun attributes(model: TasteModel, embedding: FloatArray): FloatArray {
        val out = ArrayList<Float>()
        for (dist in listOf(model.colors(embedding), model.materials(embedding))) {
            dist.sortedBy { it.first }.forEach { out += sqrt(it.second).toFloat() * 0.7f }
        }
        val probes = model.analyze(embedding).probes
        for (p in PROBES) out += ((probes[p] ?: 0.5) * 0.5).toFloat()
        return TasteModel.normalize(out.toFloatArray())
    }

    /** Best raw similarity of a listing to the refs: (image, attributes). */
    fun raw(refs: List<Pair<FloatArray, FloatArray>>, embedding: FloatArray, attrs: FloatArray): Pair<Double, Double>? {
        if (refs.isEmpty()) return null
        var img = -1.0; var att = -1.0
        for ((e, a) in refs) {
            if (e.size == embedding.size) img = maxOf(img, TasteModel.dot(TasteModel.normalize(e), embedding))
            if (a.size == attrs.size) att = maxOf(att, TasteModel.dot(a, attrs))
        }
        return img to att
    }

    /** Raw similarities of a result set → 0..1 (rank of the combined z-score; 1 = looks most like the refs). */
    fun normalize(raw: Map<Long, Pair<Double, Double>>): Map<Long, Double> {
        if (raw.isEmpty()) return emptyMap()
        if (raw.size < 5) return raw.mapValues { (_, v) -> ((v.second - 0.6) / 0.35).coerceIn(0.0, 1.0) }
        fun stats(xs: Collection<Double>): Pair<Double, Double> {
            val m = xs.average(); val sd = sqrt(xs.sumOf { (it - m) * (it - m) } / xs.size)
            return m to sd.coerceAtLeast(1e-6)
        }
        val (mi, si) = stats(raw.values.map { it.first })
        val (ma, sa) = stats(raw.values.map { it.second })
        val combined = raw.mapValues { (_, v) -> (1 - ATTR_WEIGHT) * (v.first - mi) / si + ATTR_WEIGHT * (v.second - ma) / sa }
        val order = combined.entries.sortedBy { it.value }
        val n = (order.size - 1).coerceAtLeast(1).toDouble()
        return order.withIndex().associate { (i, e) -> e.key to i / n }
    }
}
