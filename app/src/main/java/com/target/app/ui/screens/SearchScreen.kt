package com.target.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.target.app.ui.AppViewModel
import com.target.app.ui.components.GradientButton
import com.target.app.ui.components.ListingCard
import com.target.app.ui.components.TargetRadar
import com.target.app.ui.components.bouncyClick
import com.target.app.ui.theme.LocalHaptics
import com.target.app.ui.theme.Palette
import com.target.engine.SortMode

@Composable
fun SearchScreen(vm: AppViewModel, onOpen: (Long) -> Unit, onLogin: () -> Unit = {}) {
    val state by vm.search.collectAsState()
    val query by vm.query.collectAsState()
    val sort by vm.sort.collectAsState()
    val label by vm.searchLabel.collectAsState()
    val profile by vm.active.collectAsState()
    val h = LocalHaptics.current
    val focus = LocalFocusManager.current
    val grid = rememberLazyGridState()
    val results = remember(state?.results, sort) { vm.sorted(state?.results.orEmpty(), sort) }
    val running = state?.running == true
    val deals = results.count { it.deal?.isDeal == true }

    // haptic feedback when a search completes
    var wasRunning by remember { mutableStateOf(false) }
    LaunchedEffect(running) {
        if (wasRunning && !running) { if (deals > 0) h.deal() else if (results.isNotEmpty()) h.success() else h.reject() }
        wasRunning = running
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = grid,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 130.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Column(Modifier.statusBarsPadding().padding(top = 18.dp)) {
                Text("Recherche", style = MaterialTheme.typography.displaySmall, color = Palette.Text, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.height(14.dp))
                SearchBar(query, onChange = { vm.query.value = it }, onSearch = { focus.clearFocus(); h.confirm(); vm.searchText() }, onClear = { vm.query.value = "" })
                Spacer(Modifier.height(10.dp))
                AnimatedVisibility(query.isBlank() && !running, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    GradientButton(
                        "Scanner mes réfs · ${profile?.emoji ?: ""} ${profile?.name ?: ""}",
                        Modifier.fillMaxWidth(), height = 50.dp,
                        icon = { Icon(Icons.Rounded.TrackChanges, null, tint = Color.White) },
                    ) { vm.searchProfile() }
                }
            }
        }
        item(span = { GridItemSpan(2) }) {
            AnimatedVisibility(running, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                state?.let { Progress(it.done, it.total, it.current, it.scanned, it.langs.size, it.visualPass, onCancel = { h.reject(); vm.cancelSearch() }) }
            }
        }
        if (state != null && (results.isNotEmpty() || !running)) item(span = { GridItemSpan(2) }) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                    Text(
                        "${results.size} annonces${if (deals > 0) " · $deals deal${if (deals > 1) "s" else ""} 🔥" else ""}",
                        style = MaterialTheme.typography.titleMedium, color = Palette.Text, modifier = Modifier.weight(1f),
                    )
                    label?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Palette.Text3, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(150.dp), textAlign = TextAlign.End) }
                }
                if (!running && state?.scanned != null && state!!.scanned > 0) Text(
                    "${state!!.total} recherches · ${state!!.langs.size} langues · ${state!!.scanned} annonces analysées",
                    style = MaterialTheme.typography.bodySmall, color = Palette.Text3, modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SortMode.entries) { m ->
                        val on = m == sort
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(if (on) Palette.Text else Palette.Surface)
                                .border(1.dp, if (on) Palette.Text else Palette.Outline, RoundedCornerShape(50))
                                .bouncyClick { vm.sort.value = m }.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) { Text(m.label, style = MaterialTheme.typography.labelLarge, color = if (on) Palette.Bg else Palette.Text2) }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        items(results, key = { it.listing.id }) { r ->
            ListingCard(r, results.indexOf(r), Modifier.animateItem()) { onOpen(r.listing.id) }
        }
        if (state == null) item(span = { GridItemSpan(2) }) { Idle() }
        else if (!running && results.isEmpty()) item(span = { GridItemSpan(2) }) {
            Empty(state?.error, showLogin = vm.loggedIn.collectAsState().value.not(), onLogin = onLogin)
        }
    }
}

@Composable
private fun SearchBar(value: String, onChange: (String) -> Unit, onSearch: () -> Unit, onClear: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val glow by animateFloatAsState(if (focused) 1f else 0f, tween(250), label = "glow")
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp)).background(Palette.Surface)
            .border(1.dp, androidx.compose.ui.graphics.lerp(Palette.Outline, Palette.Accent, glow), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = androidx.compose.ui.graphics.lerp(Palette.Text3, Palette.Accent, glow))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Knit Ralph Lauren, doudoune TNF…", color = Palette.Text3, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Text),
                cursorBrush = SolidColor(Palette.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            )
        }
        AnimatedVisibility(value.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Close, null, tint = Palette.Text3, modifier = Modifier.size(22.dp).bouncyClick(onClick = onClear))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(38.dp).clip(CircleShape).background(Palette.AccentBrush).bouncyClick(haptic = false, onClick = onSearch), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Search, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun Progress(done: Int, total: Int, current: String?, scanned: Int, langs: Int, visual: Boolean, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(22.dp)).background(Palette.Surface)
            .border(1.dp, Palette.Outline, RoundedCornerShape(22.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TargetRadar(Modifier.size(64.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (visual) "Comparaison visuelle…" else "Recherche $done / $total", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
            AnimatedContent(current, transitionSpec = { (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut()) }, label = "q") { q ->
                Text(q ?: "", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = Palette.Accent2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("$scanned annonces · $langs langues", style = MaterialTheme.typography.bodySmall, color = Palette.Text3)
            Spacer(Modifier.height(6.dp))
            val p by animateFloatAsState(if (total == 0) 0f else done / total.toFloat(), tween(400), label = "p")
            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)), color = Palette.Accent, trackColor = Palette.Surface3)
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Rounded.Close, "Annuler", tint = Palette.Text3, modifier = Modifier.size(24.dp).bouncyClick(onClick = onCancel))
    }
}

@Composable
private fun Idle() {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TargetRadar(Modifier.size(150.dp), active = false)
        Spacer(Modifier.height(18.dp))
        Text("Prêt à chasser", style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
        Text(
            "Chaque recherche est déclinée en dizaines de variantes, dans toutes les langues de Vinted, puis triée par rapport qualité-prix et fraîcheur.",
            style = MaterialTheme.typography.bodyMedium, color = Palette.Text2, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun Empty(error: String?, showLogin: Boolean, onLogin: () -> Unit) {
    val blocked = error != null && (error.startsWith("Vinted bloque") || error.startsWith("Vinted ne répond"))
    Column(Modifier.fillMaxWidth().padding(top = 40.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (blocked) "😵" else "🕳️", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(10.dp))
        Text(if (blocked) "Vinted ne répond pas" else "Rien trouvé", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = Palette.Text)
        Text(
            error ?: "Essaie d'élargir tes réfs (moins de filtres, autre formulation).",
            style = MaterialTheme.typography.bodyMedium, color = Palette.Text2, textAlign = TextAlign.Center,
        )
        if (blocked && showLogin) {
            Spacer(Modifier.height(16.dp))
            GradientButton("Connecter mon compte Vinted", Modifier.fillMaxWidth(), height = 50.dp) { onLogin() }
        }
    }
}
