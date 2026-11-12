package com.target.app.vinted

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import com.target.engine.HtmlCards
import com.target.engine.Listing
import com.target.engine.Session
import com.target.engine.VintedDomain
import com.target.engine.VintedJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
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

/** How a catalogue search is performed. The first one that works is remembered per site. */
enum class Transport(val label: String) {
    SVC("API catalogue"), SVC_WEB("API via navigateur"), LEGACY("ancienne API"), PAGE("page web")
}

/**
 * Client for the API used by Vinted's own website.
 * Since September 2026 the catalogue lives on api.vinted.xx/svc-catalogue/items (the old www.vinted.xx/api/v2/catalog
 * endpoint answers 404). Several transports are tried in order and the working one is remembered:
 * 1. svc-catalogue with OkHttp (browser cookies + headers),
 * 2. the same request run by fetch() inside a hidden WebView on Vinted (passes anti-bot checks),
 * 3. the legacy endpoint (in case a market still serves it),
 * 4. the catalogue web page itself, read from the WebView DOM.
 */
class VintedClient(context: Context) {
    val userAgent: String = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrElse {
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
    }
    val web = WebSession(context)
    private val prefs = context.getSharedPreferences("vinted", Context.MODE_PRIVATE)
    private val anonId: String = prefs.getString("anon_id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("anon_id", it).apply() }
    private val sessions = ConcurrentHashMap<String, Long>()
    private val sessionLocks = ConcurrentHashMap<String, Mutex>()
    private val working = ConcurrentHashMap<String, Transport>()
    private val noOrder = ConcurrentHashMap<String, Boolean>()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    /** Recent network events, shown in Settings → Diagnostic. */
    val log: StateFlow<List<String>> = _log
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.FRANCE)
    private fun note(msg: String) = _log.update { (it + "${clock.format(Date())}  $msg").takeLast(40) }

    val http: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(WebViewCookieJar())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val req = chain.request()
            chain.proceed(req.newBuilder().header("User-Agent", userAgent).apply {
                if (req.header("Accept-Language") == null) header("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.8")
            }.build())
        }
        .build()

    // ---------- session ----------

    private fun cookies(host: String): String? = CookieManager.getInstance().getCookie("https://$host/")
    fun token(host: String): String? = Session.cookie(cookies(host), "access_token_web")
    fun hasToken(host: String): Boolean = token(host) != null

    /** True when the user is logged into a Vinted account (not just browsing anonymously). */
    fun isMember(host: String): Boolean =
        Session.isMember(token(host)) || Session.cookie(cookies(host), "v_uid")?.toLongOrNull()?.let { it > 0 } == true

    private suspend fun ensureSession(host: String, force: Boolean = false) {
        val lock = sessionLocks.getOrPut(host) { Mutex() }
        lock.withLock {
            val last = sessions[host] ?: 0L
            if (!force && hasToken(host) && System.currentTimeMillis() - last < 30 * 60_000) return
            val before = token(host)
            val code = withContext(Dispatchers.IO) {
                runCatching {
                    http.newCall(Request.Builder().url("https://$host/").header("Accept", "text/html,application/xhtml+xml").build()).execute().use { it.code }
                }.getOrDefault(-1)
            }
            note("Session $host → $code${if (hasToken(host)) " (jeton ok)" else " (pas de jeton)"}")
            // A real browser refreshes expired or missing tokens and passes anti-bot challenges.
            if (code !in 200..399 || !hasToken(host) || (force && token(host) == before)) {
                val ok = web.warmUp(host) { hasToken(host) && (!force || token(host) != before || !isMember(host)) }
                note("Session navigateur $host → ${if (ok) "ok" else "échec"}")
            }
            sessions[host] = System.currentTimeMillis()
        }
    }

    private fun apiHeaders(b: Request.Builder, domain: VintedDomain?, host: String, bearer: Boolean): Request.Builder {
        val locale = domain?.locale ?: "fr-FR"
        b.header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "$locale,${locale.substringBefore('-')};q=0.9,en;q=0.7")
            .header("Locale", locale)
            .header("Platform", "web")
            .header("X-Next-App", "marketplace-web")
            .header("Origin", "https://$host")
            .header("Referer", "https://$host/")
            .header("Sec-Fetch-Dest", "empty").header("Sec-Fetch-Mode", "cors").header("Sec-Fetch-Site", "same-site")
            .header("Cache-Control", "no-cache")
            .header("X-Anon-Id", Session.cookie(cookies(host), "anon_id") ?: anonId)
        if (bearer) token(host)?.let { b.header("Authorization", "Bearer $it") }
        return b
    }

    private data class Resp(val code: Int, val body: String)

    private suspend fun get(url: String, domain: VintedDomain?, host: String, bearer: Boolean): Resp = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(apiHeaders(Request.Builder().url(url), domain, host, bearer).build()).execute().use { r -> Resp(r.code, r.body?.string() ?: "") }
        }.getOrElse { Resp(-1, it.message ?: "") }
    }

    private fun hasItems(body: String): Boolean {
        val o = runCatching { VintedJson.json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return false
        return (o["items"] ?: o["catalog_items"]) is JsonArray
    }

    private fun snippet(body: String) = body.replace(Regex("\\s+"), " ").take(90)

    // ---------- search ----------

    /**
     * @param catalogId Vinted catalogue to search in (5 = men, 1904 = women). If a market answers nothing with it,
     *                  the search is repeated without it (the gender is then checked on titles and photos).
     */
    suspend fun search(host: String, text: String, page: Int = 1, perPage: Int = 96, priceTo: Double? = null, currency: String = "EUR", catalogId: Int? = null): List<Listing> {
        if (catalogId != null && noCatalog[host] != true) {
            val withCatalog = searchOnce(host, text, page, perPage, priceTo, currency, catalogId)
            if (withCatalog.isNotEmpty()) return withCatalog
            noCatalogMisses.merge(host, 1, Int::plus)
            if ((noCatalogMisses[host] ?: 0) >= 3) { noCatalog[host] = true; note("Filtre de rayon ignoré par $host") }
        }
        return searchOnce(host, text, page, perPage, priceTo, currency, null)
    }

    private val noCatalog = ConcurrentHashMap<String, Boolean>()
    private val noCatalogMisses = ConcurrentHashMap<String, Int>()

    private suspend fun searchOnce(host: String, text: String, page: Int, perPage: Int, priceTo: Double?, currency: String, catalogId: Int?): List<Listing> {
        val domain = VintedDomain.fromHost(host)
        ensureSession(host)
        val order = Transport.entries.sortedBy { if (it == working[host]) -1 else it.ordinal }
        val problems = ArrayList<String>()
        for (t in order) {
            val result = runCatching { attempt(t, host, domain, text, page, perPage, priceTo, currency, catalogId) }.getOrElse { problems += "${t.label}: ${it.message}"; null }
            if (result != null) {
                if (working[host] != t) note("✓ ${t.label} utilisée pour $host")
                working[host] = t
                return result
            }
        }
        note("✗ « $text » : ${problems.joinToString(" | ")}")
        throw VintedException(problems.lastOrNull { "403" in it || "401" in it }?.let { "Vinted bloque la recherche. Connecte ton compte Vinted dans Réglages." }
            ?: "Vinted ne répond pas (${problems.firstOrNull() ?: "erreur inconnue"})")
    }

    /** Returns listings, or null when this transport does not work (the caller tries the next one). */
    private suspend fun attempt(t: Transport, host: String, domain: VintedDomain?, text: String, page: Int, perPage: Int, priceTo: Double?, currency: String, catalogId: Int?): List<Listing>? {
        val apiHost = VintedDomain.apiHostFor(host)
        fun svcUrl(withOrder: Boolean) = "https://$apiHost/svc-catalogue/items".toHttpUrl().newBuilder()
            .addQueryParameter("search_text", text)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", perPage.toString())
            .apply {
                if (withOrder) addQueryParameter("order", "newest_first")
                if (priceTo != null && currency == "EUR") addQueryParameter("price_to", priceTo.toInt().toString())
                if (catalogId != null) addQueryParameter("attribute_ids[catalog]", catalogId.toString())
            }.build().toString()

        when (t) {
            Transport.SVC -> {
                var withOrder = noOrder[host] != true
                for (attemptNo in 1..4) {
                    val r = get(svcUrl(withOrder), domain, host, bearer = attemptNo % 2 == 1)
                    when {
                        r.code == 200 && hasItems(r.body) -> return parse(r.body, host)
                        r.code == 400 && withOrder -> { noOrder[host] = true; withOrder = false; note("API : paramètre de tri refusé, on s'en passe") }
                        r.code == 401 || r.code == 403 -> { if (attemptNo == 2) ensureSession(host, force = true); if (attemptNo >= 3) throw VintedException("${r.code} ${snippet(r.body)}") }
                        r.code == 429 -> delay(1500L * attemptNo)
                        else -> throw VintedException("${r.code} ${snippet(r.body)}")
                    }
                }
                return null
            }
            Transport.SVC_WEB -> {
                val headers = mapOf(
                    "Accept" to "application/json, text/plain, */*", "Locale" to (domain?.locale ?: "fr-FR"),
                    "Platform" to "web", "X-Next-App" to "marketplace-web", "X-Anon-Id" to (Session.cookie(cookies(host), "anon_id") ?: anonId),
                ) + (token(host)?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap())
                val body = web.fetch(host, svcUrl(noOrder[host] != true), headers) ?: throw VintedException("pas de réponse")
                if (!hasItems(body)) throw VintedException(snippet(body))
                return parse(body, host)
            }
            Transport.LEGACY -> {
                val url = "https://$host/api/v2/catalog/items".toHttpUrl().newBuilder()
                    .addQueryParameter("search_text", text).addQueryParameter("order", "newest_first")
                    .addQueryParameter("per_page", perPage.toString()).addQueryParameter("page", page.toString())
                    .apply { if (catalogId != null) addQueryParameter("catalog_ids[]", catalogId.toString()) }
                    .build().toString()
                val r = get(url, domain, host, bearer = false)
                if (r.code == 200 && hasItems(r.body)) return parse(r.body, host)
                throw VintedException("${r.code}")
            }
            Transport.PAGE -> {
                val url = "https://$host/catalog".toHttpUrl().newBuilder()
                    .addQueryParameter("search_text", text).addQueryParameter("order", "newest_first")
                    .apply { if (page > 1) addQueryParameter("page", page.toString()); if (catalogId != null) addQueryParameter("catalog[]", catalogId.toString()) }
                    .build().toString()
                val cards = web.catalogCards(host, url) ?: throw VintedException("page vide")
                val items = withContext(Dispatchers.Default) { HtmlCards.parse(cards, host) }
                if (items.isEmpty()) throw VintedException("aucune carte lisible")
                return items
            }
        }
    }

    private suspend fun parse(body: String, host: String) = withContext(Dispatchers.Default) { VintedJson.parseCatalog(body, host) }

    /** Simple end-to-end check shown in Settings. */
    suspend fun selfTest(host: String): String {
        working.remove(host)
        return runCatching {
            val n = search(host, "nike", perPage = 20).size
            "✓ $n annonces reçues via ${working[host]?.label ?: "?"}"
        }.getOrElse { "✗ ${it.message}" }
    }

    // ---------- item details ----------

    /** Full listing (description, all photos): legacy JSON endpoints, then the public item page. */
    suspend fun details(listing: Listing): Listing? {
        val host = listing.host
        val domain = VintedDomain.fromHost(host)
        for (path in listOf("/api/v2/items/${listing.id}/details", "/api/v2/items/${listing.id}")) {
            val r = get("https://$host$path", domain, host, bearer = true)
            if (r.code != 200) continue
            val d = VintedJson.parseDetails(r.body, host) ?: continue
            return merge(listing, d.description, d.photos, d.size, d.seller)
        }
        // public HTML page: OpenGraph tags carry the description and the main photos
        val html = withContext(Dispatchers.IO) {
            runCatching {
                http.newCall(Request.Builder().url(listing.url).header("Accept", "text/html").build()).execute().use { if (it.isSuccessful) it.body?.string() else null }
            }.getOrNull()
        } ?: return null
        val desc = meta(html, "og:description") ?: meta(html, "description")
        val photos = Regex("""<meta[^>]+property="og:image"[^>]+content="([^"]+)"""").findAll(html).map { unescape(it.groupValues[1]) }.toList()
        return merge(listing, desc, photos, null, null)
    }

    private fun merge(l: Listing, desc: String?, photos: List<String>, size: String?, seller: String?) = l.copy(
        description = desc?.takeIf { it.isNotBlank() } ?: l.description,
        photos = (l.photos + photos).distinct(),
        size = l.size ?: size,
        seller = l.seller ?: seller,
    )

    private fun meta(html: String, name: String): String? =
        Regex("""<meta[^>]+(?:property|name)="${Regex.escape(name)}"[^>]+content="([^"]*)"""").find(html)?.groupValues?.get(1)?.let(::unescape)

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&#x2F;", "/")

    // ---------- account ----------

    /** Login name of the connected account when the API exposes it, otherwise a generic label. */
    suspend fun currentUser(host: String): String? {
        if (!isMember(host)) return null
        val r = get("https://$host/api/v2/users/current", VintedDomain.fromHost(host), host, bearer = true)
        val login = runCatching {
            ((VintedJson.json.parseToJsonElement(r.body) as? JsonObject)?.get("user") as? JsonObject)?.get("login") as? JsonPrimitive
        }.getOrNull()?.content
        return login ?: "compte Vinted"
    }

    fun logout() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        sessions.clear(); working.clear()
        note("Déconnecté")
    }
}
