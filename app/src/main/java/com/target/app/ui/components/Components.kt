package com.target.app.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.target.app.ui.theme.LocalHaptics
import com.target.app.ui.theme.Palette
import com.target.engine.KeywordKind
import java.util.Locale
import kotlin.math.min

// ---------- shared element plumbing ----------

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedBounds(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val anim = LocalNavAnimatedScope.current ?: return this
    return with(shared) { this@sharedBounds.sharedElement(rememberSharedContentState(key), anim) }
}

@Composable
fun ProvideNavScope(scope: AnimatedVisibilityScope, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalNavAnimatedScope provides scope, content = content)

// ---------- motion ----------

/** Springy press feedback (scale down while pressed). */
fun Modifier.pressScale(source: MutableInteractionSource, pressed: Float = 0.95f): Modifier = composed {
    val isPressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (isPressed) pressed else 1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), label = "press")
    graphicsLayer { scaleX = s; scaleY = s }
}

/** Clickable with press-scale + haptic tick, no ripple. */
fun Modifier.bouncyClick(enabled: Boolean = true, scale: Float = 0.95f, haptic: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val h = LocalHaptics.current
    this.pressScale(source, scale).clickable(source, indication = null, enabled = enabled) {
        if (haptic) h.tick()
        onClick()
    }
}

/** Animated diagonal shimmer used as loading placeholder. */
fun Modifier.shimmer(): Modifier = composed {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x")
    drawWithCache {
        val w = size.width
        val brush = Brush.linearGradient(
            listOf(Palette.Surface2, Palette.Surface3, Palette.Surface2),
            start = Offset(w * x - w, 0f), end = Offset(w * x, size.height),
        )
        onDrawBehind { drawRect(brush) }
    }
}

// ---------- building blocks ----------

@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    brush: Brush = Palette.AccentBrush,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    onClick: () -> Unit,
) {
    val h = LocalHaptics.current
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) brush else SolidColor(Palette.Surface3))
            .bouncyClick(enabled, 0.96f, haptic = false) { h.click(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) { icon(); Spacer(Modifier.width(10.dp)) }
            Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.Outline.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.Text2)
            }
            trailing?.invoke(this)
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

fun kindColor(kind: KeywordKind): Color = when (kind) {
    KeywordKind.STYLE -> Palette.Style
    KeywordKind.BRAND -> Palette.Brand
    KeywordKind.ITEM -> Palette.Item
    KeywordKind.MATERIAL -> Palette.Material
    KeywordKind.COLOR -> Palette.ColorTag
    KeywordKind.FREE -> Palette.Text2
}

fun kindLabel(kind: KeywordKind): String = when (kind) {
    KeywordKind.STYLE -> "Style"
    KeywordKind.BRAND -> "Marque"
    KeywordKind.ITEM -> "Pièce"
    KeywordKind.MATERIAL -> "Matière"
    KeywordKind.COLOR -> "Couleur"
    KeywordKind.FREE -> "Mot-clé"
}

@Composable
fun TagChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    caption: String? = null,
    selected: Boolean = true,
    onRemove: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val bg = if (selected) color.copy(alpha = 0.16f) else Palette.Surface2
    val border = if (selected) color.copy(alpha = 0.45f) else Palette.Outline
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(50))
            .then(if (onClick != null) Modifier.bouncyClick(onClick = onClick) else Modifier)
            .padding(start = 12.dp, end = if (onRemove != null) 6.dp else 12.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(7.dp))
        Column {
            Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) Palette.Text else Palette.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (caption != null) Text(caption, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.9f))
        }
        if (onRemove != null) {
            Spacer(Modifier.width(4.dp))
            val h = LocalHaptics.current
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(Palette.Surface3).bouncyClick(haptic = false) { h.reject(); onRemove() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, null, tint = Palette.Text2, modifier = Modifier.size(14.dp)) }
        }
    }
}

@Composable
fun Pill(text: String, bg: Color, fg: Color = Color.White, modifier: Modifier = Modifier, brush: Brush? = null) {
    Box(
        modifier.clip(RoundedCornerShape(50)).then(if (brush != null) Modifier.background(brush) else Modifier.background(bg))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) { Text(text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = fg, maxLines = 1) }
}

/** Animated target / radar used while searching. */
@Composable
fun TargetRadar(modifier: Modifier = Modifier, active: Boolean = true) {
    val t = rememberInfiniteTransition(label = "radar")
    val sweep by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "sweep")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "pulse")
    Canvas(modifier) {
        val r = min(size.width, size.height) / 2f
        val c = center
        for (i in 1..3) drawCircle(Palette.Outline, radius = r * i / 3f, center = c, style = Stroke(1.2.dp.toPx()))
        drawLine(Palette.Outline, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.dp.toPx())
        drawLine(Palette.Outline, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.dp.toPx())
        if (active) {
            drawCircle(Palette.Accent.copy(alpha = (1f - pulse) * 0.55f), radius = r * pulse, center = c, style = Stroke(3.dp.toPx()))
            rotate(sweep, c) {
                drawCircle(
                    Brush.sweepGradient(listOf(Color.Transparent, Color.Transparent, Palette.Accent.copy(alpha = 0.05f), Palette.Accent.copy(alpha = 0.55f)), c),
                    radius = r, center = c,
                )
            }
        }
        drawCircle(Palette.Accent, radius = r * 0.09f, center = c)
        drawCircle(Color.White, radius = r * 0.035f, center = c)
    }
}

// ---------- formatting ----------

fun price(v: Double, currency: String = "EUR"): String {
    val sym = when (currency) { "EUR" -> "€"; "GBP" -> "£"; "PLN" -> "zł"; "CZK" -> "Kč"; "SEK", "DKK" -> "kr"; "HUF" -> "Ft"; "RON" -> "lei"; else -> currency }
    val n = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.FRANCE, "%.2f", v)
    return if (currency == "GBP") "$sym$n" else "$n $sym"
}

fun relativeTime(epochSeconds: Long?, now: Long = System.currentTimeMillis() / 1000): String? {
    epochSeconds ?: return null
    val d = (now - epochSeconds).coerceAtLeast(0)
    return when {
        d < 60 -> "à l'instant"
        d < 3600 -> "il y a ${d / 60} min"
        d < 86400 -> "il y a ${d / 3600} h"
        d < 86400 * 30 -> "il y a ${d / 86400} j"
        else -> "il y a ${d / (86400 * 30)} mois"
    }
}

@Composable
fun FullCenter(content: @Composable () -> Unit) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
