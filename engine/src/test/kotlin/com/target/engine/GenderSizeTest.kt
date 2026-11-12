package com.target.engine

import org.junit.Assert.*
import org.junit.Test

class GenderSizeTest {
    private val now = 1_800_000_000L
    private var id = 1L
    private fun l(title: String, size: String?, brand: String = "Ralph Lauren") =
        Listing(id++, title, 30.0, brand = brand, size = size, condition = Condition.VERY_GOOD, url = "u", createdAt = now)

    @Test fun parserReadsGenderAndSizes() {
        val p = QueryParser.parse("pull ralph lauren homme S")
        assertEquals(Gender.MEN, p.gender)
        assertEquals(listOf("S"), p.sizes)
        assertEquals(listOf("knit"), p.items.map { it.id })
        assertTrue(p.free.isEmpty())
        assertEquals(listOf("42"), QueryParser.parse("jean levis taille 42").sizes)
        assertEquals(Gender.WOMEN, QueryParser.parse("Femme").gender)
        assertEquals(listOf("tshirt"), QueryParser.parse("t-shirt stussy").items.map { it.id })
    }

    @Test fun sizesAreStrict() {
        assertTrue(Ranker.sizeMatches("S", listOf("s")))
        assertTrue(Ranker.sizeMatches("S / 36 / 8", listOf("s")))
        assertFalse(Ranker.sizeMatches("XS / 34 / 6", listOf("s")))
        assertFalse(Ranker.sizeMatches("M", listOf("s")))
        assertFalse(Ranker.sizeMatches(null, listOf("s")))
        assertTrue(Ranker.sizeMatches("L/XL", listOf("xl")))
        val targets = Planner.targetsForSearch("pull torsadé ralph lauren S")
        val ranked = Ranker(targets, nowSeconds = now).rank(listOf(l("Pull torsadé", "S"), l("Pull torsadé", "M"), l("Pull torsadé", null), l("Pull torsadé", "XS")),
            filters = Filters(minBeauty = 0.0))
        assertEquals(listOf("S"), ranked.map { it.listing.size })
    }

    @Test fun menKeepsMenAndUnisex() {
        val targets = Planner.targetsForProfile(listOf("knit", "Ralph Lauren", "Homme"), emptyList())
        assertTrue(targets.all { it.gender == Gender.MEN })
        val men = l("Pull torsadé Ralph Lauren homme", "M")
        val women = l("Pull torsadé Ralph Lauren femme", "M")
        val neutral = l("Pull torsadé Ralph Lauren", "M")
        val womenPhoto = l("Pull torsadé Ralph Lauren", "M")
        val photos = mapOf(womenPhoto.id to PhotoSignals(mapOf("knit" to 0.9), mapOf("menswear" to 0.05), mapOf("menswear" to -3.0), emptyMap()))
        val ranked = Ranker(targets, nowSeconds = now).rank(listOf(men, women, neutral, womenPhoto), photos = photos, filters = Filters(minBeauty = 0.0))
        assertEquals(setOf(men.id, neutral.id), ranked.map { it.listing.id }.toSet())
    }
}
