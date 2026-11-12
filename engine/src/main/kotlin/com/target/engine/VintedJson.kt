package com.target.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** Tolerant parser for Vinted's web API (field shapes have changed several times over the years). */
object VintedJson {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseCatalog(body: String, host: String): List<Listing> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return emptyList()
        val items = (root["items"] ?: root["catalog_items"]) as? JsonArray ?: return emptyList()
        return items.mapNotNull { (it as? JsonObject)?.let { o -> parseItem(o, host) } }
    }

    /** Parses /api/v2/items/{id}/details or /api/v2/items/{id} (object under "item"). */
    fun parseDetails(body: String, host: String): Listing? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        val item = (root["item"] as? JsonObject) ?: root
        return parseItem(item, host)
    }

    /** Same parser for the legacy /api/v2 shape and the 2026 /svc-catalogue shape (brand/size/condition in item_box). */
    fun parseItem(o: JsonObject, host: String): Listing? {
        val id = o.long("id") ?: (o["item_box"] as? JsonObject)?.long("item_id") ?: return null
        val box = o["item_box"] as? JsonObject
        val accessibility = box?.str("accessibility_label")
        val title = o.str("title") ?: accessibility?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val (price, cur) = money(o["price"], o.str("currency") ?: o.str("price_currency"))
        val (fallbackPrice, fallbackCur) = if (price == null) moneyFromLabel(accessibility) else null to null
        val finalPrice = price ?: fallbackPrice ?: return null
        val currency = cur ?: fallbackCur ?: "EUR"
        val (total, _) = money(o["total_item_price"], currency)
        val photo = o["photo"] as? JsonObject
        val photos = (o["photos"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let(::photoUrl) }.orEmpty()
        val mainPhoto = photo?.let(::photoUrl) ?: photos.firstOrNull()
        val ts = (photo?.get("high_resolution") as? JsonObject)?.long("timestamp")
            ?: ((o["photos"] as? JsonArray)?.firstOrNull() as? JsonObject)?.let { (it["high_resolution"] as? JsonObject)?.long("timestamp") }
            ?: o.long("created_at_ts")
        val user = o["user"] as? JsonObject
        val path = o.str("path")
        val rawUrl = o.str("url") ?: path ?: "/items/$id"
        val url = if (rawUrl.startsWith("http")) rawUrl else "https://$host${if (rawUrl.startsWith("/")) "" else "/"}$rawUrl"
        // item_box: first_line = brand (when it is not the title), second_line = "size · condition" or "condition"
        val firstLine = box?.str("first_line")?.takeIf { !it.equals(title, ignoreCase = true) }
        val second = box?.str("second_line")?.split(" · ", " • ", " | ")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val statusLabel = o.str("status") ?: second.lastOrNull()
        val condition = Condition.fromId(o.int("status_id")) ?: Condition.parse(statusLabel)
        val brand = o.str("brand_title") ?: (o["brand_dto"] as? JsonObject)?.str("title") ?: (o["brand"] as? JsonObject)?.str("title")
            ?: o.str("brand") ?: firstLine
        val size = o.str("size_title") ?: o.str("size") ?: second.takeIf { it.size >= 2 }?.first()
        return Listing(
            id = id, title = title, price = finalPrice, currency = currency, totalPrice = total,
            brand = brand?.takeIf { it.isNotBlank() }, size = size,
            condition = condition, statusLabel = statusLabel, photoUrl = mainPhoto,
            photos = (listOfNotNull(mainPhoto) + photos).distinct(), thumbUrl = photo?.let(::thumbUrl), url = url, host = host, createdAt = ts,
            favourites = o.int("favourite_count") ?: 0, views = o.int("view_count") ?: 0,
            seller = user?.str("login"), sellerRating = user?.double("feedback_reputation")?.let { it * 5 },
            description = o.str("description"), promoted = o.bool("promoted") ?: false,
        )
    }

    private val MONEY = Regex("""(£|€|\$)?\s?(\d{1,6}(?:[.,\u00A0 ]\d{3})*(?:[.,]\d{1,2})?)\s?(€|£|zł|kr|Kč|Ft|lei|\$)?""")

    /** "Pull, Ralph Lauren, 10,00 €, 11,20 € inclut…" → (10.0, EUR). */
    fun moneyFromLabel(label: String?): Pair<Double?, String?> {
        if (label.isNullOrBlank()) return null to null
        for (part in label.split(", ")) {
            val m = MONEY.matchEntire(part.trim()) ?: continue
            val sym = m.groupValues[1].ifEmpty { m.groupValues[3] }
            if (sym.isEmpty()) continue
            return parseAmount(m.groupValues[2]) to symbolToCurrency(sym)
        }
        return null to null
    }

    /** Money at the start of a fragment: "11,20 € inclut la protection" → (11.2, EUR). */
    fun moneyPrefix(part: String): Pair<Double, String>? {
        val m = MONEY.find(part.trim()) ?: return null
        if (m.range.first != 0) return null
        val sym = m.groupValues[1].ifEmpty { m.groupValues[3] }
        if (sym.isEmpty()) return null
        return (parseAmount(m.groupValues[2]) ?: return null) to symbolToCurrency(sym)
    }

    fun parseAmount(raw: String): Double? {
        val t = raw.replace("\u00A0", "").replace(" ", "")
        val lastSep = maxOf(t.lastIndexOf(','), t.lastIndexOf('.'))
        return if (lastSep >= 0 && t.length - lastSep - 1 in 1..2) {
            (t.substring(0, lastSep).replace(",", "").replace(".", "") + "." + t.substring(lastSep + 1)).toDoubleOrNull()
        } else t.replace(",", "").replace(".", "").toDoubleOrNull()
    }

    fun symbolToCurrency(sym: String) = when (sym) {
        "€" -> "EUR"; "£" -> "GBP"; "zł" -> "PLN"; "Kč" -> "CZK"; "Ft" -> "HUF"; "lei" -> "RON"; "$" -> "USD"; "kr" -> "SEK"; else -> "EUR"
    }

    private fun thumbUrl(p: JsonObject): String? {
        val thumbs = (p["thumbnails"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        return (thumbs.firstOrNull { it.str("type")?.contains("310") == true }
            ?: thumbs.filter { (it.int("width") ?: 0) >= 300 }.minByOrNull { it.int("width") ?: Int.MAX_VALUE })?.str("url")
    }

    private fun photoUrl(p: JsonObject): String? =
        p.str("full_size_url") ?: p.str("url") ?: ((p["thumbnails"] as? JsonArray)?.lastOrNull() as? JsonObject)?.str("url")

    /** Price can be "12.0", 12.0, or {"amount":"12.0","currency_code":"EUR"}. */
    private fun money(e: JsonElement?, fallbackCur: String?): Pair<Double?, String?> = when (e) {
        null, JsonNull -> null to fallbackCur
        is JsonPrimitive -> e.content.replace(',', '.').toDoubleOrNull() to fallbackCur
        is JsonObject -> (e.str("amount")?.replace(',', '.')?.toDoubleOrNull()) to (e.str("currency_code") ?: fallbackCur)
        else -> null to fallbackCur
    }

    private fun JsonObject.prim(k: String) = this[k] as? JsonPrimitive
    private fun JsonObject.str(k: String): String? = prim(k)?.takeUnless { it is JsonNull }?.content?.takeIf { it.isNotEmpty() && it != "null" }
    private fun JsonObject.long(k: String): Long? = prim(k)?.longOrNull ?: prim(k)?.content?.toLongOrNull()
    private fun JsonObject.int(k: String): Int? = prim(k)?.intOrNull ?: prim(k)?.content?.toIntOrNull()
    private fun JsonObject.double(k: String): Double? = prim(k)?.doubleOrNull
    private fun JsonObject.bool(k: String): Boolean? = prim(k)?.booleanOrNull
}
