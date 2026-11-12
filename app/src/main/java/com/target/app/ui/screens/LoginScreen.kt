package com.target.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.target.app.ui.components.bouncyClick
import com.target.app.ui.theme.Palette

/**
 * Vinted login inside a WebView. Credentials are typed on vinted.* itself; Target never sees them and only keeps the
 * resulting session cookie (shared with its HTTP client). Navigation is restricted to Vinted domains.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(host: String, isMember: () -> Boolean = { false }, onDone: () -> Unit) {
    var progress by remember { mutableStateOf(0f) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var finished by remember { mutableStateOf(false) }
    val h = com.target.app.ui.theme.LocalHaptics.current
    BackHandler { if (webView?.canGoBack() == true) webView?.goBack() else onDone() }
    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, tint = Palette.Deal, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Connexion Vinted", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                Text(host, style = MaterialTheme.typography.bodySmall, color = Palette.Text3)
            }
            Box(Modifier.size(40.dp).clip(CircleShape).background(Palette.Accent).bouncyClick { CookieManager.getInstance().flush(); onDone() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, "Terminé", tint = androidx.compose.ui.graphics.Color.White)
            }
        }
        if (progress in 0.01f..0.99f) LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(2.dp), color = Palette.Accent, trackColor = Palette.Bg)
        AndroidView(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    CookieManager.getInstance().setAcceptCookie(true)
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress / 100f }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val h = request.url.host.orEmpty()
                            val allowed = request.url.scheme == "https" && (h.contains("vinted.") || h.endsWith("cloudflare.com") ||
                                h.endsWith("facebook.com") || h.endsWith("apple.com") || h.endsWith("google.com"))
                            return !allowed
                        }
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {}
                        override fun onPageFinished(view: WebView?, url: String?) {
                            CookieManager.getInstance().flush()
                            // close automatically as soon as Vinted has issued a member session
                            if (!finished && isMember()) { finished = true; h.success(); onDone() }
                        }
                    }
                    loadUrl("https://$host/member/signup/select_type?ref_url=%2F")
                    webView = this
                }
            },
        )
    }
}
