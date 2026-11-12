package com.target.engine

import org.junit.Assert.*
import org.junit.Test

class ParserTest {
    private fun brands(q: String) = QueryParser.parse(q).brands.map { it.name }
    private fun items(q: String) = QueryParser.parse(q).items.map { it.id }

    @Test fun normalizeStripsAccentsAndSymbols() {
        assertEquals("t shirt levis", Text.normalize("T-Shirt Levi's"))
        assertEquals("weiss strickpullover", Text.normalize("Weiß  Strickpullover!"))
        assertEquals("h and m", Text.normalize("H&M"))
    }

    @Test fun distanceIsDamerau() {
        assertEquals(1, Text.distance("laurent", "lauren"))
        assertEquals(1, Text.distance("nkie", "nike"))
        assertEquals(0, Text.distance("abc", "abc"))
        assertTrue(Text.distance("knit", "zara", 1) > 1)
    }

    @Test fun typoInBrandIsCorrected() {
        val p = QueryParser.parse("Knit ralph laurent")
        assertEquals(listOf("Ralph Lauren"), p.brands.map { it.name })
        assertEquals(listOf("knit"), p.items.map { it.id })
        assertTrue(p.free.isEmpty())
        assertTrue(p.recognized.first { it.kind == KeywordKind.BRAND }.corrected)
    }

    @Test fun commonBrandsAndAliases() {
        assertEquals(listOf("Stüssy"), brands("stussy hoodie"))
        assertEquals(listOf("hoodie"), items("stussy hoodie"))
        assertEquals(listOf("Carhartt"), brands("veste carhart"))
        assertEquals(listOf("The North Face"), brands("doudoune northface"))
        assertEquals(listOf("The North Face"), brands("doudoune tnf"))
        assertEquals(listOf("Ralph Lauren"), brands("Polo Ralph Lauren"))
        assertEquals(listOf("Levi's"), brands("jean levis 501"))
        assertEquals(listOf("H&M"), brands("pull h&m"))
        assertEquals(listOf("Arc'teryx"), brands("arcteryx"))
    }

    @Test fun realWordsAreNotMistakenForBrands() {
        assertTrue(brands("pull nice").isEmpty())
        assertTrue(brands("bottes cuir").isEmpty())
        assertTrue(brands("chemise lin blanche").isEmpty())
        assertTrue(brands("jupe plissée").isEmpty())
    }

    @Test fun multilingualTermsAreRecognised() {
        assertEquals(listOf("knit"), items("Strickpullover"))
        assertEquals(listOf("knit"), items("maglione"))
        assertEquals(listOf("puffer"), items("doudoune"))
        assertEquals(listOf("jeans"), items("spijkerbroek"))
        assertEquals(listOf("knit"), items("sweatter"))   // typo
        assertEquals(listOf("tshirt"), items("t-shirt"))
        assertEquals(listOf("tshirt"), items("tshirt"))
    }

    @Test fun stylesColorsMaterialsAndFreeWords() {
        val p = QueryParser.parse("Old money pull cachemire bleu marine")
        assertEquals(listOf("oldmoney"), p.styles.map { it.id })
        assertEquals(listOf("knit"), p.items.map { it.id })
        assertEquals(listOf("cashmere"), p.materials.map { it.id })
        assertEquals(listOf("navy"), p.colors.map { it.id })
        val q = QueryParser.parse("veste harrington baracuta")
        assertEquals(listOf("jacket"), q.items.map { it.id })
        assertTrue(q.free.containsAll(listOf("harrington", "baracuta")))
        assertEquals(listOf("streetwear"), QueryParser.parse("Streetwear").styles.map { it.id })
    }

    @Test fun conditionLabelsInManyLanguages() {
        assertEquals(Condition.NEW_WITH_TAGS, Condition.parse("Neuf avec étiquette"))
        assertEquals(Condition.NEW, Condition.parse("Neuf sans étiquette"))
        assertEquals(Condition.VERY_GOOD, Condition.parse("Très bon état"))
        assertEquals(Condition.GOOD, Condition.parse("Bon état"))
        assertEquals(Condition.SATISFACTORY, Condition.parse("Satisfaisant"))
        assertEquals(Condition.NEW_WITH_TAGS, Condition.parse("New with tags"))
        assertEquals(Condition.NEW, Condition.parse("New without tags"))
        assertEquals(Condition.VERY_GOOD, Condition.parse("Sehr gut"))
        assertEquals(Condition.GOOD, Condition.parse("Gut"))
        assertEquals(Condition.NEW_WITH_TAGS, Condition.parse("Nuevo con etiquetas"))
        assertEquals(Condition.VERY_GOOD, Condition.parse("Ottime condizioni"))
        assertEquals(Condition.SATISFACTORY, Condition.parse("Zufriedenstellend"))
        assertEquals(Condition.UNKNOWN, Condition.parse(null))
    }

    @Test fun imageLabelsMapToConcepts() {
        assertTrue(Lexicon.conceptsForLabel("Sweater").any { it.id == "knit" })
        assertTrue(Lexicon.conceptsForLabel("Jeans").any { it.id == "jeans" })
        assertEquals("navy", Lexicon.nearestColor(0x1B2440).id)
        assertEquals("black", Lexicon.nearestColor(0x0A0A0A).id)
        assertEquals("beige", Lexicon.nearestColor(0xCFAF86).id)
    }
}
