package com.iktishafat.teleprompter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Premium dark studio palette: near-black canvas, graphite surfaces, gold accent. */
object StudioColors {
    val Canvas = Color(0xFF0B0E13)
    val Surface = Color(0xFF12161D)
    val SurfaceHigh = Color(0xFF1A1F28)
    val SurfaceHighest = Color(0xFF232935)
    val Outline = Color(0xFF2E3542)
    val Gold = Color(0xFFD4AF37)
    val GoldDim = Color(0xFF8C7424)
    val OnGold = Color(0xFF1A1405)
    val Navy = Color(0xFF14243D)
    val Text = Color(0xFFE9EDF2)
    val TextMuted = Color(0xFF8A93A0)
    val Live = Color(0xFFFF4D4D)
    val Listening = Color(0xFF6EE7A0)
}

private val colors = darkColorScheme(
    primary = StudioColors.Gold,
    onPrimary = StudioColors.OnGold,
    primaryContainer = StudioColors.GoldDim,
    onPrimaryContainer = Color(0xFFFFF1C2),
    secondary = Color(0xFF8FB3E8),
    onSecondary = Color(0xFF0B1A30),
    secondaryContainer = StudioColors.Navy,
    onSecondaryContainer = Color(0xFFD6E4FA),
    tertiary = StudioColors.Listening,
    background = StudioColors.Canvas,
    onBackground = StudioColors.Text,
    surface = StudioColors.Surface,
    onSurface = StudioColors.Text,
    surfaceVariant = StudioColors.SurfaceHigh,
    onSurfaceVariant = StudioColors.TextMuted,
    surfaceContainerLowest = StudioColors.Canvas,
    surfaceContainerLow = StudioColors.Surface,
    surfaceContainer = StudioColors.SurfaceHigh,
    surfaceContainerHigh = StudioColors.SurfaceHigh,
    surfaceContainerHighest = StudioColors.SurfaceHighest,
    outline = StudioColors.Outline,
    outlineVariant = Color(0xFF232935),
    error = Color(0xFFFF6B6B),
)

private val typography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Medium),
        labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    )
}

private val shapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun IktishafatTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}
