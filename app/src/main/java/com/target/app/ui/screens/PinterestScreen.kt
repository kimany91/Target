package com.target.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.target.app.ui.AppViewModel
import com.target.app.ui.components.GradientButton
import com.target.app.ui.components.bouncyClick
import com.target.app.ui.theme.LocalHaptics
import com.target.app.ui.theme.Palette
import com.target.engine.Pin

/** Pinterest images are served by i.pinimg.com in several sizes: /236x/, /474x/, /736x/, /originals/. */
private val PIN_SIZE = Regex("""^https://i\.pinimg\.com/(\d+x\d*|originals)/""")
fun pinterestLarge(url: String): String? = if (PIN_SIZE.containsMatchIn(url)) url.replace(PIN_SIZE, "https://i.pinimg.com/736x/") else null

private const val MAX_PICK = 12
private val PinRed = Color(0xFFE60023)

/**
 * Pinterest search built into the app, like the Vinted search: a search bar, a grid of pictures that grows while
 * scrolling, a tap to select (pink frame + check), and "Ajouter N réfs". No account and no web page needed.
 */
@Composable
fun PinterestBrowser(vm: AppViewModel, initialQuery: String, onAdd: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val h = LocalHaptics.current
    val focus = LocalFocusManager.current
    val st by vm.pinterest.collectAsState()
    var query by remember { mutableStateOf(initialQuery) }
    val selected = remember { mutableStateListOf<String>() }
    val grid = rememberLazyStaggeredGridState()

    LaunchedEffect(Unit) { if (st.query != initialQuery.trim() || st.pins.isEmpty()) vm.pinterestSearch(initialQuery) }
    // load the next page when the end of the grid comes into view
    val nearEnd by remember { derivedStateOf { (grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= grid.layoutInfo.totalItemsCount - 8 } }
    LaunchedEffect(nearEnd, st.pins.size) { if (nearEnd && st.pins.isNotEmpty()) vm.pinterestMore() }
    BackHandler(onBack = onDismiss)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        PinterestContent(vm, query, { query = it }, selected, grid, onAdd, onDismiss)
    }
}

@Composable
internal fun PinterestContent(
    vm: AppViewModel, query: String, onQuery: (String) -> Unit, selected: MutableList<String>,
    grid: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState, onAdd: (List<String>) -> Unit, onDismiss: () -> Unit,
) {
    val h = LocalHaptics.current
    val focus = LocalFocusManager.current
    val st by vm.pinterest.collectAsState()
    Box(Modifier.fillMaxSize().background(Palette.Bg)) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            state = grid,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalItemSpacing = 10.dp,
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Column(Modifier.statusBarsPadding().padding(top = 10.dp, bottom = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(PinRed), contentAlignment = Alignment.Center) {
                            Text("P", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black), color = Color.White)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Pinterest", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = Palette.Text)
                            Text("Touche les images à ajouter à tes réfs", style = MaterialTheme.typography.bodySmall, color = Palette.Text2)
                        }
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Palette.Surface3).bouncyClick(onClick = onDismiss), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Close, "Fermer", tint = Palette.Text)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(18.dp)).background(Palette.Surface)
                            .border(1.dp, Palette.Outline, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Search, null, tint = Palette.Text2)
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) Text("Outfit, knit, old money…", style = MaterialTheme.typography.bodyLarge, color = Palette.Text3)
                            BasicTextField(
                                value = query, onValueChange = onQuery, singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Text),
                                cursorBrush = SolidColor(Palette.Accent),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { h.confirm(); focus.clearFocus(); vm.pinterestSearch(query) }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (query.isNotEmpty()) Icon(Icons.Rounded.Close, "Effacer", tint = Palette.Text3, modifier = Modifier.size(20.dp).bouncyClick { onQuery("") })
                    }
                    val err = st.error
                    if (err != null && st.pins.isEmpty() && !st.loading) Text(err, style = MaterialTheme.typography.bodyMedium, color = Palette.Text2, modifier = Modifier.padding(top = 24.dp))
                }
            }
            items(st.pins, key = { it.imageUrl }) { pin ->
                val on = pin.imageUrl in selected
                PinCard(pin, on) {
                    if (on) { selected -= pin.imageUrl; h.toggle(false) }
                    else if (selected.size < MAX_PICK) { selected += pin.imageUrl; h.toggle(true) } else h.reject()
                }
            }
            if (st.loading) item(span = StaggeredGridItemSpan.FullLine) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PinRed, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                }
            }
        }
        AnimatedVisibility(
            selected.isNotEmpty(), modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
        ) {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                GradientButton("Ajouter ${selected.size} réf${if (selected.size > 1) "s" else ""}", Modifier.fillMaxWidth(), height = 54.dp) {
                    h.confirm(); onAdd(selected.toList()); onDismiss()
                }
            }
        }
    }
}

@Composable
private fun PinCard(pin: Pin, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 0.94f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "pin")
    val placeholder = remember(pin.color) { runCatching { Color(android.graphics.Color.parseColor(pin.color)) }.getOrDefault(Palette.Surface2) }
    Box(
        Modifier.scale(scale).clip(RoundedCornerShape(18.dp)).background(placeholder)
            .border(if (selected) 3.dp else 0.dp, if (selected) Palette.Accent else Color.Transparent, RoundedCornerShape(18.dp))
            .bouncyClick(haptic = false, onClick = onClick)
    ) {
        AsyncImage(
            pin.thumbUrl, pin.title, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio((pin.width.toFloat() / pin.height.coerceAtLeast(1)).coerceIn(0.45f, 1.6f)),
        )
        val alpha by animateFloatAsState(if (selected) 1f else 0f, tween(180), label = "sel")
        if (alpha > 0f) Box(Modifier.matchParentSize().background(Palette.Accent.copy(alpha = 0.18f * alpha)))
        AnimatedVisibility(selected, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp), enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Palette.Accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        if (pin.title.isNotBlank()) Text(
            pin.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
