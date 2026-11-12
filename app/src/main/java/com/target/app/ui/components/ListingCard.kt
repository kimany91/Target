package com.target.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.target.app.ui.theme.Palette
import com.target.engine.RankedListing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Staggered entrance (fade + rise + scale) driven by the item position. */
@Composable
fun Modifier.entrance(key: Any, index: Int): Modifier {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay((index % 12) * 35L)
        a.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
    }
    return graphicsLayer {
        alpha = a.value.coerceIn(0f, 1f)
        translationY = (1f - a.value) * 60f
        val s = 0.92f + 0.08f * a.value
        scaleX = s; scaleY = s
    }
}

@Composable
fun ListingCard(r: RankedListing, index: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val l = r.listing
    val isDeal = r.deal?.isDeal == true
    val ctx = LocalContext.current
    Column(
        modifier
            .entrance(l.id, index)
            .bouncyClick(scale = 0.96f, onClick = onClick)
            .clip(RoundedCornerShape(22.dp))
            .background(Palette.Surface)
            .border(1.dp, if (isDeal) Palette.Deal.copy(alpha = 0.55f) else Palette.Outline.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.78f)) {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(l.photoUrl).crossfade(250).build(),
                contentDescription = l.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().sharedBounds("img-${l.id}").clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).shimmerIfNull(l.photoUrl),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f))
                )
            )
            Row(Modifier.padding(8.dp).align(Alignment.TopStart), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                when {
                    isDeal -> DealBadge()
                    r.suspicious || r.deal?.suspectedFake == true -> Pill("⚠ suspect", Palette.Warn.copy(alpha = 0.9f), Color.Black)
                    r.discountPct >= 25 -> Pill("-${r.discountPct}%", Palette.Accent)
                }
            }
            val age = relativeTime(l.createdAt)
            val fresh = l.createdAt != null && System.currentTimeMillis() / 1000 - l.createdAt!! < 3600
            if (age != null) Row(
                Modifier.padding(8.dp).align(Alignment.TopEnd).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (fresh) { LiveDot(); Spacer(Modifier.width(4.dp)) }
                Text((if (l.ageEstimated) "~" else "") + age.removePrefix("il y a "), style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
            Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                Text(price(l.price, l.currency), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, fontSize = 21.sp), color = Color.White)
                if (r.expectedPrice > l.buyerPrice * 1.15)
                    Text("~${price(Math.round(r.expectedPrice).toDouble())}", style = MaterialTheme.typography.labelMedium.copy(textDecoration = TextDecoration.LineThrough), color = Color.White.copy(alpha = 0.6f))
            }
            Column(Modifier.align(Alignment.BottomEnd).padding(10.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                r.visual?.let { v ->
                    Text(
                        "◉ ${(((v - 0.6) / 0.3).coerceIn(0.0, 1.0) * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall, color = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Palette.Info.copy(alpha = 0.85f)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                BeautyPill(r.beauty)
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(l.brand ?: r.matchedBrand ?: "Sans marque", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(l.title, style = MaterialTheme.typography.bodySmall, color = Palette.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!l.size.isNullOrBlank()) {
                    Text(l.size!!, style = MaterialTheme.typography.labelMedium, color = Palette.Text, maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Palette.Surface3).padding(horizontal = 6.dp, vertical = 2.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(l.condition.label, style = MaterialTheme.typography.labelMedium, color = Palette.Text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun DealBadge(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "deal")
    val glow by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glow")
    Box(
        modifier
            .drawBehind { drawRoundRect(Palette.Deal.copy(alpha = 0.35f * glow), cornerRadius = androidx.compose.ui.geometry.CornerRadius(40f), size = size.copy(width = size.width + 6f, height = size.height + 6f), topLeft = androidx.compose.ui.geometry.Offset(-3f, -3f)) }
            .clip(RoundedCornerShape(50))
            .background(Palette.DealBrush)
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) { Text("🔥 DEAL", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black), color = Color.Black) }
}

@Composable
fun LiveDot() {
    val t = rememberInfiniteTransition(label = "live")
    val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(6.dp).graphicsLayer { alpha = a }.clip(CircleShape).background(Palette.Deal))
}

@Composable
private fun Modifier.shimmerIfNull(url: String?): Modifier = if (url == null) this.shimmer() else this.background(Palette.Surface2)

/** Beauty score /10 ("★ 8,4"), green when the piece is really desirable. */
@Composable
fun BeautyPill(beauty: Double, modifier: Modifier = Modifier) {
    val c = when { beauty >= 8 -> Palette.Deal; beauty >= 7 -> Palette.ColorTag; beauty >= 6 -> Palette.Warn; else -> Palette.Text3 }
    Text(
        "★ " + String.format(java.util.Locale.FRANCE, "%.1f", beauty),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black), color = Color.Black,
        modifier = modifier.clip(RoundedCornerShape(50)).background(c).padding(horizontal = 7.dp, vertical = 2.dp),
    )
}
