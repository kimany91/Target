package com.target.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.target.app.ui.components.GradientButton
import com.target.app.ui.components.bouncyClick
import com.target.app.ui.theme.LocalHaptics
import com.target.app.ui.theme.Palette
import java.net.URLEncoder

/** Pinterest images are served by i.pinimg.com in several sizes: /236x/, /474x/, /736x/, /originals/. */
private val PIN_SIZE = Regex("""^https://i\.pinimg\.com/(\d+x\d*|originals)/""")
fun pinterestLarge(url: String): String? = if (PIN_SIZE.containsMatchIn(url)) url.replace(PIN_SIZE, "https://i.pinimg.com/736x/") else null

private fun allowedHost(host: String?) = host != null && (host == "pinterest.com" || host.endsWith(".pinterest.com") ||
    Regex("""(^|\.)pinterest\.[a-z.]{2,6}$""").containsMatchIn(host) || host == "pin.it" || host.endsWith("pinimg.com") ||
    host == "accounts.google.com")

/**
 * In-app Pinterest: search for outfits, tap pictures to select them, and add them to the profile's refs in one go.
 * A tap on a pin selects it instead of opening it (switch "Sélection" off to browse normally).
 */
private const val PICK_JS = """
(function(){
  if (window.__tgt) return; window.__tgt = 1; window.__tgtSelect = true; window.__tgtSel = new Set();
  function big(u){ return (u||'').replace(/^https:\/\/i\.pinimg\.com\/(\d+x\d*|originals)\//, 'https://i.pinimg.com/736x/'); }
  function ok(el){ var u = el && (el.currentSrc || el.src) || ''; return /^https:\/\/i\.pinimg\.com\//.test(u) && el.getBoundingClientRect().width >= 90; }
  function imgOf(t){
    if (t.tagName === 'IMG' && ok(t)) return t;
    var card = t.closest && (t.closest('[data-grid-item]') || t.closest('[data-test-id="pin"]') || t.closest('[data-test-id*="pin-rep"]') ||
      t.closest('[data-test-id*="closeup"]') || t.closest('[data-test-id*="pin-image"]'));
    var scope = card || t;
    if (!scope.querySelectorAll) return null;
    var best = null, area = 0;
    scope.querySelectorAll('img').forEach(function(el){ if (!ok(el)) return; var r = el.getBoundingClientRect(); if (r.width * r.height > area) { area = r.width * r.height; best = el; } });
    return best;
  }
  function mark(){
    document.querySelectorAll('img[src*="i.pinimg.com"]').forEach(function(el){
      var on = window.__tgtSel.has(big(el.currentSrc || el.src));
      el.style.outline = on ? '5px solid #FF3D6E' : ''; el.style.outlineOffset = on ? '-5px' : '';
      el.style.opacity = on ? '0.72' : '';
    });
  }
  document.addEventListener('click', function(e){
    if (!window.__tgtSelect) return;
    var el = imgOf(e.target); if (!el) return;
    e.preventDefault(); e.stopPropagation();
    var u = big(el.currentSrc || el.src);
    if (TargetBridge.toggle(u)) window.__tgtSel.add(u); else window.__tgtSel.delete(u);
    mark();
  }, true);
  setInterval(mark, 900);
})();
"""

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PinterestBrowser(initialQuery: String, onAdd: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val h = LocalHaptics.current
    val selected = remember { mutableStateListOf<String>() }
    var query by remember { mutableStateOf(initialQuery) }
    var selectMode by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }
    var web by remember { mutableStateOf<WebView?>(null) }
    fun searchUrl(q: String) = "https://www.pinterest.com/search/pins/?q=" + URLEncoder.encode(q.ifBlank { "outfit" }, "UTF-8")

    BackHandler { if (web?.canGoBack() == true) web?.goBack() else onDismiss() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Palette.Bg)) {
            Column(Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Palette.Surface3).bouncyClick(onClick = onDismiss), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, "Fermer", tint = Palette.Text)
                    }
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.weight(1f),
                        placeholder = { Text("Chercher sur Pinterest", color = Palette.Text3) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Palette.Text2) },
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { h.confirm(); web?.loadUrl(searchUrl(query)) }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.Outline,
                            focusedContainerColor = Palette.Surface2, unfocusedContainerColor = Palette.Surface2, cursorColor = Palette.Accent,
                        ),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val modeText = if (selectMode) "✓ Sélection : touche une image pour l'ajouter" else "Navigation : touche une image pour l'ouvrir"
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(if (selectMode) Palette.Accent.copy(alpha = 0.18f) else Palette.Surface)
                            .border(1.dp, if (selectMode) Palette.Accent else Palette.Outline, RoundedCornerShape(50))
                            .bouncyClick {
                                selectMode = !selectMode; h.toggle(selectMode)
                                web?.evaluateJavascript("window.__tgtSelect = $selectMode;", null)
                            }.padding(horizontal = 12.dp, vertical = 7.dp)
                    ) { Text(modeText, style = MaterialTheme.typography.labelMedium, color = Palette.Text) }
                }
                if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(2.dp), color = Palette.Accent)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            setBackgroundColor(android.graphics.Color.BLACK)
                            addJavascriptInterface(object {
                                /** Called by the page script; returns whether the image is now selected. */
                                @JavascriptInterface
                                fun toggle(url: String): Boolean {
                                    val big = pinterestLarge(url) ?: return false
                                    val on = big !in selected
                                    post { if (on) { if (selected.size < 12) selected += big } else selected -= big; h.toggle(on) }
                                    return on && selected.size < 12
                                }
                            }, "TargetBridge")
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                    !allowedHost(request.url.host)   // stay on Pinterest (the bridge is only for its pages)
                                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { progress = 10 }
                                override fun onPageFinished(view: WebView, url: String?) {
                                    progress = 100
                                    view.evaluateJavascript(PICK_JS, null)
                                    view.evaluateJavascript("window.__tgtSelect = $selectMode;", null)
                                }
                                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                    view.evaluateJavascript(PICK_JS, null)   // single-page navigation keeps the script, this is a safety net
                                }
                            }
                            loadUrl(searchUrl(initialQuery))
                            web = this
                        }
                    },
                    onRelease = { it.destroy() },
                )
            }
            AnimatedVisibility(selected.isNotEmpty(), enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                Box(Modifier.fillMaxWidth().background(Palette.Bg).navigationBarsPadding().padding(12.dp)) {
                    GradientButton("Ajouter ${selected.size} réf${if (selected.size > 1) "s" else ""}", Modifier.fillMaxWidth(), height = 52.dp) {
                        h.confirm(); onAdd(selected.toList()); onDismiss()
                    }
                }
            }
            if (selected.isEmpty()) Spacer(Modifier.navigationBarsPadding())
        }
    }
}

