package com.target.engine

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test from the live run on vinted.fr (search "knit" + Stüssy, 130 real listings, photos embedded with
 * MobileCLIP-S2, each hand-labelled from its photo as a knit or a sweatshirt / hoodie). Before the "knitwear" probe,
 * 14 sweats and hoodies were shown in a knit search because the category softmax calls jacquard knits "sweatshirt".
 */
class LiveKnitTest {
    private fun res(name: String) = javaClass.classLoader.getResource(name)!!.readText()
    private val model = TasteModel.parse(res("taste_s2.json"))

    private data class Case(val title: String, val knit: Boolean, val photo: PhotoSignals)

    private val cases = (VintedJson.json.parseToJsonElement(res("s2_knit_vs_sweat.json")) as JsonArray).map { e ->
        val o = e as JsonObject
        Case(
            (o["title"] as JsonPrimitive).content,
            (o["label"] as JsonPrimitive).content == "knit",
            model.analyze((o["emb"] as JsonArray).map { (it as JsonPrimitive).content.toFloat() }.toFloatArray()),
        )
    }

    private fun fit(c: Case, requested: String): CategoryFit {
        val (items, weak) = Lexicon.itemEvidence(Text.tokens(c.title))
        return Taste.categoryFit(listOf(requested), items.map { it.id }.toSet(), weak, c.photo)
    }

    @Test fun knitSearchKeepsKnitsAndDropsSweats() {
        val knits = cases.filter { it.knit }
        val sweats = cases.filter { !it.knit }
        val keptKnits = knits.count { fit(it, "knit") != CategoryFit.MISMATCH }
        val leakedSweats = sweats.filter { fit(it, "knit") != CategoryFit.MISMATCH }
        println("knit search: ${keptKnits}/${knits.size} knits kept, ${leakedSweats.size}/${sweats.size} sweats leaked ${leakedSweats.map { it.title }}")
        assertTrue("knits kept $keptKnits/${knits.size}", keptKnits >= (knits.size * 0.9).toInt())
        assertTrue("sweats leaked ${leakedSweats.size}/${sweats.size}: ${leakedSweats.map { it.title }}", leakedSweats.size <= sweats.size / 12)
    }

    @Test fun sweatSearchDropsKnits() {
        val sweats = cases.filter { !it.knit }
        val knits = cases.filter { it.knit }
        val keptSweats = sweats.count { fit(it, "sweatshirt") != CategoryFit.MISMATCH }
        val leakedKnits = knits.filter { fit(it, "sweatshirt") != CategoryFit.MISMATCH }
        println("sweat search: $keptSweats/${sweats.size} sweats kept, ${leakedKnits.size}/${knits.size} knits leaked ${leakedKnits.map { it.title }}")
        assertTrue("sweats kept $keptSweats/${sweats.size}", keptSweats >= (sweats.size * 0.85).toInt())
        assertTrue("knits leaked ${leakedKnits.size}/${knits.size}", leakedKnits.size <= knits.size / 6)
    }
}
