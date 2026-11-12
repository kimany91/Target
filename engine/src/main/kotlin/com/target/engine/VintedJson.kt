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
        val items = root["items"] as? JsonArray ?: return emptyList()
        return items.mapNotNull { (it as? JsonObject)?.let { o -> parseItem(o, host) } }
    }

    /** Parses /api/v2/items/{id}/details or /api/v2/items/{id} (object under "item"). */
    fun parseDetails(body: String, host: String): Listing? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        val item = (root["item"] as? JsonObject) ?: root
        return parseItem(item, host)
    }

    fun parseItem(o: JsonObject, host: String): Listing? {
        val id = o.long("id") ?: return null
        val title = o.str("title") ?: return null
        val (price, cur) = money(o["price"], o.str("currency") ?: o.str("price_currency"))
        if (price == null) return null
        val (total, _) = money(o["total_item_price"], cur)
        val photo = o["photo"] as? JsonObject
        val photos = (o["photos"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let(::photoUrl) }.orEmpty()
        val mainPhoto = photo?.let(::photoUrl) ?: photos.firstOrNull()
        val ts = (photo?.get("high_resolution") as? JsonObject)?.long("timestamp")
            ?: ((o["photos"] as? JsonArray)?.firstOrNull() as? JsonObject)?.let { (it["high_resolution"] as? JsonObject)?.long("timestamp") }
            ?: o.long("created_at_ts")
        val user = o["user"] as? JsonObject
        val path = o.str("path")
        val url = o.str("url") ?: path?.let { "https://$host$it" } ?: "https://$host/items/$id"
        val statusLabel = o.str("status")
        val condition = Condition.fromId(o.int("status_id")) ?: Condition.parse(statusLabel)
        val brand = o.str("brand_title") ?: (o["brand_dto"] as? JsonObject)?.str("title") ?: (o["brand"] as? JsonObject)?.str("title") ?: o.str("brand")
        return Listing(
            id = id, title = title, price = price, currency = cur ?: "EUR", totalPrice = total,
            brand = brand?.takeIf { it.isNotBlank() }, size = o.str("size_title") ?: o.str("size"),
            condition = condition, statusLabel = statusLabel, photoUrl = mainPhoto,
            photos = (listOfNotNull(mainPhoto) + photos).distinct(), url = url, host = host, createdAt = ts,
            favourites = o.int("favourite_count") ?: 0, views = o.int("view_count") ?: 0,
            seller = user?.str("login"), sellerRating = user?.double("feedback_reputation")?.let { it * 5 },
            description = o.str("description"), promoted = o.bool("promoted") ?: false,
        )
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
