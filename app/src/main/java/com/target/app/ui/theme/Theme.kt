package com.target.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

object Palette {
    val Bg = Color(0xFF09090C)
    val Surface = Color(0xFF121217)
    val Surface2 = Color(0xFF1A1A21)
    val Surface3 = Color(0xFF23232C)
    val Outline = Color(0xFF2B2B35)
    val Text = Color(0xFFF5F5F7)
    val Text2 = Color(0xFFA3A3AE)
    val Text3 = Color(0xFF6E6E7A)
    val Accent = Color(0xFFFF3D5A)
    val Accent2 = Color(0xFFFF8A3D)
    val Deal = Color(0xFF22E3A0)
    val Info = Color(0xFF6C8CFF)
    val Warn = Color(0xFFFFC145)
    val Style = Color(0xFFB48CFF)
    val Brand = Color(0xFFFF6B81)
    val Item = Color(0xFF4FD1FF)
    val Material = Color(0xFFFFB86B)
    val ColorTag = Color(0xFF7CE38B)

    val AccentBrush = Brush.linearGradient(listOf(Accent, Accent2))
    val DealBrush = Brush.linearGradient(listOf(Color(0xFF22E3A0), Color(0xFF17B8FF)))
    fun hueColor(hue: Float, s: Float = 0.75f, v: Float = 1f) = Color.hsv(((hue % 360f) + 360f) % 360f, s, v)
}

private val scheme = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Color.White,
    secondary = Palette.Accent2,
    tertiary = Palette.Deal,
    background = Palette.Bg,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Surface2,
    onSurfaceVariant = Palette.Text2,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.Surface2,
    surfaceContainerHighest = Palette.Surface3,
    surfaceContainerLow = Palette.Surface,
    outline = Palette.Outline,
    outlineVariant = Palette.Outline,
    error = Color(0xFFFF5D5D),
)

private val base = FontFamily.Default
private val typography = Typography(
    displaySmall = TextStyle(fontFamily = base, fontWeight = FontWeight.Black, fontSize = 34.sp, letterSpacing = (-0.03).em, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = base, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, letterSpacing = (-0.02).em),
    headlineSmall = TextStyle(fontFamily = base, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = (-0.015).em),
    titleLarge = TextStyle(fontFamily = base, fontWeight = FontWeight.Bold, fontSize = 19.sp, letterSpacing = (-0.01).em),
    titleMedium = TextStyle(fontFamily = base, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = base, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = base, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = base, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = base, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = base, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.01.em),
    labelMedium = TextStyle(fontFamily = base, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = base, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.06.em),
)

@Composable
fun TargetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
