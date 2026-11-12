package com.target.app.vinted

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONTokener
import java.util.UUID

/**
 * A hidden, real browser (WebView) living on Vinted's origin. It passes the anti-bot checks a plain HTTP client can
 * fail, shares its cookies with OkHttp, and can run same-origin fetch() calls as a last-resort transport.
 * No JavaScript interface is exposed: results are polled with evaluateJavascript.
 */
class WebSession(private val context: Context) {
    private var webView: WebView? = null
    private var loadedHost: String? = null
    private val mutex = Mutex()

    @SuppressLint("SetJavaScriptEnabled")
    private fun view(): WebView = webView ?: WebView(context.applicationContext).also { wv ->
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            blockNetworkImage = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, false)
        webView = wv
    }

    private suspend fun load(host: String): Boolean = withContext(Dispatchers.Main) {
        val done = CompletableDeferred<Boolean>()
        val wv = view()
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !(request.url.host.orEmpty().endsWith("vinted.${host.substringAfter("vinted.")}") || request.url.host.orEmpty().contains("challenges.cloudflare.com"))
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {}
            override fun onPageFinished(view: WebView, url: String) { if (!done.isCompleted) done.complete(true) }
        }
        wv.loadUrl("https://$host/")
        val ok = withTimeoutOrNull(20_000) { done.await() } ?: false
        if (ok) loadedHost = host
        ok
    }

    /** Opens Vinted's home page in the hidden browser so that session cookies are issued. */
    suspend fun warmUp(host: String, hasToken: () -> Boolean): Boolean = mutex.withLock {
        if (!load(host)) return@withLock false
        // Anti-bot challenges can take a few seconds and reload the page.
        repeat(30) {
            if (hasToken()) { CookieManager.getInstance().flush(); return@withLock true }
            delay(400)
        }
        hasToken()
    }

    /** Runs fetch(url) inside the Vinted page and returns the response body (or null). */
    suspend fun fetch(host: String, url: String): String? = mutex.withLock {
        if (loadedHost != host && !load(host)) return@withLock null
        val key = UUID.randomUUID().toString().replace("-", "")
        val jsUrl = JsonPrimitive(url).toString()
        val start = "(function(){window.__tg=window.__tg||{};fetch($jsUrl,{credentials:'include',headers:{'Accept':'application/json, text/plain, */*'}})" +
            ".then(function(r){return r.text();}).then(function(t){window.__tg['$key']=t;}).catch(function(e){window.__tg['$key']='';});return 1;})()"
        val poll = "(function(){var v=window.__tg&&window.__tg['$key'];if(v!==undefined){delete window.__tg['$key'];return v;}return null;})()"
        eval(start)
        repeat(100) {
            delay(200)
            val raw = eval(poll)
            if (raw != null && raw != "null") {
                val s = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
                return@withLock s?.takeIf { it.isNotEmpty() }
            }
        }
        null
    }

    private suspend fun eval(js: String): String? = withContext(Dispatchers.Main) {
        val d = CompletableDeferred<String?>()
        view().evaluateJavascript(js) { d.complete(it) }
        withTimeoutOrNull(5_000) { d.await() }
    }
}
