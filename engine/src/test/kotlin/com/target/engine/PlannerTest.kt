package com.target.engine

import org.junit.Assert.*
import org.junit.Test

class PlannerTest {
    @Test fun searchIsExpandedInEveryLanguage() {
        val targets = Planner.targetsForSearch("Knit ralph laurent")
        assertEquals(1, targets.size)
        val qs = Planner.plan(targets, Lang.FR, 40).map { it.text }
        assertEquals("Ralph Lauren pull", qs.first())
        for (expected in listOf("Ralph Lauren sweater", "Ralph Lauren pullover", "Ralph Lauren maglione", "Ralph Lauren jersey", "Ralph Lauren trui", "Ralph Lauren sweter"))
            assertTrue("missing $expected in $qs", qs.contains(expected))
        assertEquals("no duplicates", qs.size, qs.map { Text.normalize(it) }.toSet().size)
        assertTrue(qs.none { it.contains("laurent", ignoreCase = true) })
    }

    @Test fun budgetIsRespectedAndCombinationsInterleaved() {
        val targets = Planner.targetsForSearch("old money")
        assertTrue(targets.isNotEmpty())
        val qs = Planner.plan(targets, Lang.FR, 12)
        assertEquals(12, qs.size)
        // several different brands must appear within a small budget
        val brandsSeen = qs.mapNotNull { q -> Brands.all.firstOrNull { q.text.startsWith(it.name) }?.name }.toSet()
        assertTrue("brands: $brandsSeen", brandsSeen.size >= 3)
    }

    @Test fun profileCombinesKeywordsStylesAndImages() {
        val img = ImageSignal("r1", items = listOf("cardigan"), colors = listOf("navy"), materials = emptyList(), brands = emptyList())
        val targets = Planner.targetsForProfile(listOf("Ralph Lauren", "Knit", "Streetwear"), listOf(img))
        assertTrue(targets.any { it.origin == "keywords" && it.brands == listOf("ralphlauren") && it.items == listOf("knit") })
        assertTrue(targets.any { it.origin == "style:streetwear" && "nike" in it.brands })
        val imageTarget = targets.first { it.origin == "image:r1" }
        assertEquals(listOf("ralphlauren"), imageTarget.brands)   // image without logo inherits the profile brands
        assertEquals(listOf("cardigan"), imageTarget.items)
        val qs = Planner.plan(targets, Lang.FR, 30).map { it.text }
        // the cardigan from the photo is searched (each brand × piece starts in another market's language)
        val cardigan = Lexicon.concept("cardigan")!!
        assertTrue(qs.toString(), qs.any { q -> q.startsWith("Ralph Lauren") && Lexicon.detectItems(Text.tokens(q)).contains(cardigan) })
    }

    /** Live test: with many brands the plan was all "pull" then all "pullover" (French and German sellers only). */
    @Test fun manyBrandsStillReachEveryBigMarket() {
        for (search in listOf("knit streetwear", "jacket old money", "jeans streetwear")) {
            val qs = Planner.plan(Planner.targetsForSearch(search), Lang.FR, 20)
            val langs = qs.mapNotNull { it.lang }.toSet()
            assertTrue("$search → $langs", langs.containsAll(listOf(Lang.FR, Lang.DE, Lang.IT, Lang.ES, Lang.NL, Lang.PL)))
        }
    }

    @Test fun brandOnlyAndEmpty() {
        val qs = Planner.plan(Planner.targetsForSearch("Carhartt"), Lang.DE, 10).map { it.text }
        assertEquals("Carhartt", qs.first())
        assertTrue(Planner.plan(Planner.targetsForSearch("   "), Lang.FR, 10).isEmpty())
    }
}
