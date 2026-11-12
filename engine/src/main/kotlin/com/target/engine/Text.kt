package com.target.engine

import java.text.Normalizer

/** Text utilities shared by the whole engine: normalisation, tokenisation, fuzzy matching. */
object Text {
    private val MARKS = Regex("\\p{Mn}+")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

    /** Lowercase, strip accents, map special letters, keep only letters/digits separated by single spaces. */
    fun normalize(input: String): String {
        if (input.isEmpty()) return ""
        var s = input.lowercase()
            .replace("ß", "ss").replace("ø", "o").replace("æ", "ae").replace("œ", "oe")
            .replace("ł", "l").replace("đ", "d").replace("ı", "i").replace("&", " and ")
            .replace("'", "").replace("’", "")
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(MARKS, "")
        // "t-shirt" -> "t shirt" ; collapse everything else that is not a letter or digit
        return s.replace(NON_WORD, " ").trim().replace(Regex(" +"), " ")
    }

    fun tokens(input: String): List<String> {
        val n = normalize(input)
        return if (n.isEmpty()) emptyList() else n.split(' ')
    }

    private val PLURAL_SUFFIXES = setOf("s", "es", "x", "n", "en", "i")

    /** Light, language-agnostic plural folding: "pulls"~"pull", "sweaters"~"sweater", "jeans"~"jean", "jacken"~"jacke". */
    fun tokenEquals(a: String, b: String): Boolean {
        if (a == b) return true
        val (s, l) = if (a.length <= b.length) a to b else b to a
        if (s.length < 3 || !l.startsWith(s)) return false
        return l.substring(s.length) in PLURAL_SUFFIXES
    }

    /** True when [phrase] tokens appear consecutively inside [hay] (with light stemming). */
    fun containsPhrase(hay: List<String>, phrase: List<String>): Boolean {
        if (phrase.isEmpty() || phrase.size > hay.size) return false
        outer@ for (i in 0..hay.size - phrase.size) {
            for (j in phrase.indices) if (!tokenEquals(hay[i + j], phrase[j])) continue@outer
            return true
        }
        // also accept glued forms: "tshirt" vs "t shirt", "northface" vs "north face"
        if (phrase.size > 1) {
            val glued = phrase.joinToString("")
            if (hay.any { it == glued }) return true
        }
        return false
    }

    /** Damerau-Levenshtein (optimal string alignment) distance, early-exit when above [max]. */
    fun distance(a: String, b: String, max: Int = Int.MAX_VALUE): Int {
        if (a == b) return 0
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        val n = a.length
        val m = b.length
        if (n == 0) return m
        if (m == 0) return n
        var prev2 = IntArray(m + 1)
        var prev = IntArray(m + 1) { it }
        var cur = IntArray(m + 1)
        for (i in 1..n) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, prev2[j - 2] + 1)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > max) return max + 1
            val t = prev2; prev2 = prev; prev = cur; cur = t
        }
        return prev[m]
    }

    /** Allowed typo budget for a word of the given length. */
    fun typoBudget(length: Int): Int = when {
        length < 5 -> 0
        length < 9 -> 1
        else -> 2
    }
}
