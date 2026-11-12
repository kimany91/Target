package com.target.engine

import org.junit.Assert.assertTrue
import org.junit.Test

/** The "Lens" sections shipped with the vision model must map to ids the search understands. */
class LensTest {
    private val json = javaClass.classLoader.getResource("taste_s2.json")!!.readText()
    private val model = TasteModel.parse(json)
    private val ids = { sec: String ->
        (VintedJson.json.parseToJsonElement(json) as kotlinx.serialization.json.JsonObject)[sec].let { (it as kotlinx.serialization.json.JsonObject).keys }
    }

    @Test fun sectionsAreShippedAndUsable() {
        assertTrue(model.hasLens)
        assertTrue(ids("colors").all { Lexicon.concept(it) != null })
        assertTrue(ids("materials").filter { it != "nylon" }.all { Lexicon.concept(it) != null })
        val unknown = ids("brands").filter { it != "none" && Brands.byId(it) == null }
        assertTrue("unknown brand ids $unknown", unknown.isEmpty())
    }

    @Test fun probabilitiesSumToOne() {
        val e = FloatArray(512) { ((it * 37) % 11 - 5) / 10f }
        for (p in listOf(model.colors(e), model.materials(e), model.brands(e))) assertTrue(kotlin.math.abs(p.sumOf { it.second } - 1.0) < 1e-6)
    }
}
