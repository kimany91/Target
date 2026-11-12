package com.target.app.pinterest

import com.target.engine.PinPage
import com.target.engine.PinterestJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/** Native Pinterest search (no account, no web page): the JSON endpoint pinterest.com itself uses. */
class PinterestClient(base: OkHttpClient) {
    private val jar = object : CookieJar {
        private val store = ConcurrentHashMap<String, Cookie>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { cookies.forEach { store[it.name] = it } }
        override fun loadForRequest(url: HttpUrl) = store.values.filter { it.matches(url) }
    }
    private val http = base.newBuilder().cookieJar(jar).build()

    suspend fun search(query: String, bookmark: String?): PinPage = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(PinterestJson.searchUrl(query, bookmark))
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("X-Pinterest-PWS-Handler", "www/search/[scope].js")
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("Pinterest ne répond pas (${r.code})")
            PinterestJson.parse(r.body?.string().orEmpty())
        }
    }
}
