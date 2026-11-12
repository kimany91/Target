package com.target.engine

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Last-resort parser for the catalogue web page rendered in a WebView. Each card is extracted by JavaScript as
 * {id, href, img, alt}; the image alt text carries everything, e.g.
 * "Pull torsadé, marque: Ralph Lauren, état: Très bon état, taille: M, 10,00 €, 11,20 € inclut la Protection acheteurs".
 */
object HtmlCards {
    private val BRAND_KEYS = setOf("marque", "brand", "marke", "marca", "merk", "marka", "znacka", "prekes zenklas", "zenklas", "marka", "marka", "brand")
    private val CONDITION_KEYS = setOf("etat", "condition", "zustand", "stato", "estado", "staat", "stan", "stav", "bukle", "bukl", "allapot", "stare", "stanje", "skick", "kunto", "stand", "κατασταση")
    private val SIZE_KEYS = setOf("taille", "size", "grosse", "taglia", "talla", "maat", "rozmiar", "tamanho", "velikost", "dydis", "velkost", "meret", "marime", "velicina", "storlek", "koko", "storrelse", "μεγεθος")

    fun parse(cardsJson: String, host: String): List<Listing> {
        val arr = runCatching { VintedJson.json.parseToJsonElement(cardsJson) as? JsonArray }.getOrNull() ?: return emptyList()
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.content?.takeIf { it.isNotEmpty() && it != "null" }
            val id = s("id")?.toLongOrNull() ?: return@mapNotNull null
            val alt = s("alt")
            val text = s("text")
            (alt?.let { parseAlt(id, it, s("href"), s("img"), host) }
                ?: listOfNotNull(alt, text).joinToString(", ").takeIf { it.isNotBlank() }?.let { parseAlt(id, it, s("href"), s("img"), host) })
        }.distinctBy { it.id }
    }

    fun parseAlt(id: Long, alt: String, href: String?, img: String?, host: String): Listing? {
        val parts = alt.split(", ").map { it.trim() }
        var brand: String? = null
        var cond: String? = null
        var size: String? = null
        val title = StringBuilder()
        val amounts = ArrayList<Pair<Double, String>>()
        var seenKey = false
        for (p in parts) {
            val key = Text.normalize(p.substringBefore(':', ""))
            val value = p.substringAfter(':', "").trim()
            val money = VintedJson.moneyPrefix(p)?.let { it.first to it.second } ?: (null to null)
            when {
                p.contains(':') && key in BRAND_KEYS -> { brand = value; seenKey = true }
                p.contains(':') && key in CONDITION_KEYS -> { cond = value; seenKey = true }
                p.contains(':') && key in SIZE_KEYS -> { size = value; seenKey = true }
                money.first != null -> { amounts += money.first!! to money.second!!; seenKey = true }
                !seenKey -> { if (title.isNotEmpty()) title.append(", "); title.append(p) }
            }
        }
        val price = amounts.firstOrNull() ?: return null
        val url = href?.takeIf { it.startsWith("http") } ?: "https://$host/items/$id"
        return Listing(
            id = id, title = title.toString().ifBlank { brand ?: "Article" }, price = price.first, currency = price.second,
            totalPrice = amounts.getOrNull(1)?.first?.takeIf { it >= price.first }, brand = brand, size = size,
            condition = Condition.parse(cond), statusLabel = cond, photoUrl = img, photos = listOfNotNull(img), url = url, host = host,
        )
    }
}
