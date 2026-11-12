package com.target.app.vinted

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import com.target.engine.Listing
import com.target.engine.VintedJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class VintedException(message: String) : IOException(message)

/** Shares cookies between OkHttp and the WebView (login + anti-bot session). */
private class WebViewCookieJar : CookieJar {
    private val cm get() = CookieManager.getInstance()
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cm.setCookie(url.toString(), it.toString()) }
    }
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val raw = cm.getCookie(url.toString()) ?: return emptyList()
        return raw.split(';').mapNotNull { Cookie.parse(url, it.trim()) }
    }
}

/**
 * Client for Vinted's public web API (the one the website itself uses). Strategy, most efficient first:
 * 1. OkHttp with the browser's cookies and user agent;
 * 2. on 401/403, refresh the session by loading Vinted in a hidden WebView;
 * 3. still blocked → run the request as a same-origin fetch() inside that WebView.
 */
class VintedClient(context: Context) {
    val userAgent: String = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrElse {
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
    }
    val web = WebSession(context)
    private val sessions = ConcurrentHashMap<String, Long>()
    private val sessionLocks = ConcurrentHashMap<String, Mutex>()
    private val webOnly = ConcurrentHashMap<String, Boolean>()

    val http: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(WebViewCookieJar())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", userAgent)
                    .header("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.8")
                    .build()
            )
        }
        .build()

    fun hasToken(host: String): Boolean =
        CookieManager.getInstance().getCookie("https://$host/")?.contains("access_token_web") == true

    private suspend fun ensureSession(host: String, force: Boolean = false) {
        val lock = sessionLocks.getOrPut(host) { Mutex() }
        lock.withLock {
            val last = sessions[host] ?: 0L
            if (!force && hasToken(host) && System.currentTimeMillis() - last < 30 * 60_000) return
            if (force) CookieManager.getInstance().setCookie("https://$host/", "access_token_web=; Max-Age=0; Path=/")
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    http.newCall(Request.Builder().url("https://$host/").header("Accept", "text/html").build()).execute().use { it.code in 200..399 }
                }.getOrDefault(false)
            }
            if (!ok || !hasToken(host)) web.warmUp(host) { hasToken(host) }
            sessions[host] = System.currentTimeMillis()
        }
    }

    /** GET a Vinted API URL with automatic session repair and WebView fallback. */
    private suspend fun getJson(host: String, url: String): String {
        if (webOnly[host] == true) return web.fetch(host, url) ?: throw VintedException("Vinted ne répond pas")
        ensureSession(host)
        var attempt = 0
        while (true) {
            attempt++
            val (code, body) = withContext(Dispatchers.IO) {
                runCatching {
                    http.newCall(Request.Builder().url(url).header("Accept", "application/json, text/plain, */*").build()).execute().use { r ->
                        r.code to (r.body?.string() ?: "")
                    }
                }.getOrElse { -1 to "" }
            }
            when {
                code == 200 && body.trimStart().startsWith("{") -> return body
                code == 429 && attempt < 3 -> delay(1500L * attempt)
                (code == 401 || code == 403 || (code == 200)) && attempt == 1 -> ensureSession(host, force = true)
                code == -1 && attempt < 2 -> delay(800)
                else -> {
                    // Last resort: same-origin fetch from the real browser.
                    val viaWeb = web.fetch(host, url)
                    if (viaWeb != null && viaWeb.trimStart().startsWith("{")) {
                        webOnly[host] = true
                        return viaWeb
                    }
                    throw VintedException(if (code == -1) "Pas de connexion" else "Vinted a refusé la requête ($code)")
                }
            }
        }
    }

    suspend fun search(host: String, text: String, page: Int = 1, perPage: Int = 96, priceTo: Double? = null, currency: String = "EUR"): List<Listing> {
        val url = "https://$host/api/v2/catalog/items".toHttpUrl().newBuilder()
            .addQueryParameter("search_text", text)
            .addQueryParameter("order", "newest_first")
            .addQueryParameter("per_page", perPage.toString())
            .addQueryParameter("page", page.toString())
            .apply {
                if (priceTo != null && currency == "EUR") {
                    addQueryParameter("price_to", priceTo.toInt().toString())
                    addQueryParameter("currency", "EUR")
                }
            }
            .build().toString()
        val body = getJson(host, url)
        return withContext(Dispatchers.Default) { VintedJson.parseCatalog(body, host) }
    }

    /** Full listing (description, all photos). Tries the current and the legacy endpoints. */
    suspend fun details(listing: Listing): Listing? {
        val host = listing.host
        for (path in listOf("/api/v2/items/${listing.id}/details", "/api/v2/items/${listing.id}")) {
            val body = runCatching { getJson(host, "https://$host$path") }.getOrNull() ?: continue
            val d = VintedJson.parseDetails(body, host) ?: continue
            return listing.copy(
                description = d.description ?: listing.description,
                photos = d.photos.ifEmpty { listing.photos },
                seller = d.seller ?: listing.seller,
                sellerRating = d.sellerRating ?: listing.sellerRating,
                favourites = maxOf(d.favourites, listing.favourites),
                views = maxOf(d.views, listing.views),
                size = d.size ?: listing.size,
            )
        }
        return null
    }

    /** Login name of the connected Vinted account, or null when browsing anonymously. */
    suspend fun currentUser(host: String): String? = runCatching {
        val body = getJson(host, "https://$host/api/v2/users/current")
        val o = VintedJson.json.parseToJsonElement(body) as? JsonObject
        ((o?.get("user") as? JsonObject)?.get("login") as? JsonPrimitive)?.content
    }.getOrNull()

    fun logout() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        sessions.clear(); webOnly.clear()
    }
}
