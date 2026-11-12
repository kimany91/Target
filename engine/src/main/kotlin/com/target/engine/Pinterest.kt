package com.target.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.URLEncoder

/** One Pinterest picture found by the in-app Pinterest search. */
@Serializable
data class Pin(
    val id: String,
    /** 736 px wide: what is saved as a ref. */
    val imageUrl: String,
    /** 474 px wide: grid thumbnail. */
    val thumbUrl: String,
    val width: Int,
    val height: Int,
    val title: String = "",
    /** "#9e7d62": placeholder while the picture loads. */
    val color: String? = null,
)

data class PinPage(val pins: List<Pin>, val bookmark: String?)

/**
 * Pinterest's own web search endpoint (the one pinterest.com calls), readable without an account: the first page
 * is short, the next ones (with the bookmark) hold 25 pins each.
 */
object PinterestJson {
    fun searchUrl(query: String, bookmark: String?): String {
        val data = buildJsonObject {
            putJsonObject("options") {
                put("query", query); put("scope", "pins"); put("page_size", 25)
                putJsonArray("bookmarks") { if (bookmark != null) add(JsonPrimitive(bookmark)) }
            }
            putJsonObject("context") {}
        }.toString()
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        return "https://www.pinterest.com/resource/BaseSearchResource/get/?source_url=" + enc("/search/pins/?q=" + enc(query)) + "&data=" + enc(data)
    }

    fun parse(body: String): PinPage {
        val root = runCatching { VintedJson.json.parseToJsonElement(body) as JsonObject }.getOrNull() ?: return PinPage(emptyList(), null)
        val rr = root["resource_response"] as? JsonObject ?: return PinPage(emptyList(), null)
        val data = rr["data"]
        val results = when (data) {
            is JsonObject -> data["results"] as? JsonArray
            is JsonArray -> data
            else -> null
        }.orEmpty()
        val pins = results.mapNotNull { pin(it) }.filter { it.imageUrl.startsWith("https://i.pinimg.com/") }.distinctBy { it.imageUrl }
        val bookmark = (rr["bookmark"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "-end-" && it != "null" }
        return PinPage(pins, bookmark)
    }

    private fun str(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun int(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.content?.toIntOrNull()

    private fun pin(e: JsonElement): Pin? {
        val o = e as? JsonObject ?: return null
        if (str(o, "type") != "pin") return null
        if ((o["is_video"] as? JsonPrimitive)?.content == "true") return null
        val images = o["images"] as? JsonObject ?: return null
        val big = (images["736x"] ?: images["orig"]) as? JsonObject ?: return null
        val thumb = (images["474x"] ?: images["236x"] ?: big) as? JsonObject ?: big
        val url = str(big, "url") ?: return null
        val w = int(thumb, "width") ?: int(big, "width") ?: 1
        val h = int(thumb, "height") ?: int(big, "height") ?: 1
        val title = listOfNotNull(str(o, "grid_title"), str(o, "title"), str(o, "description")).firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return Pin(str(o, "id") ?: url, url, str(thumb, "url") ?: url, w, h, title.take(120), str(o, "dominant_color"))
    }
}
