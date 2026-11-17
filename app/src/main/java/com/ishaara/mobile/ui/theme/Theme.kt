package com.ishaara.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ishaara.mobile.R

/**
 * Neutral surfaces do the work; [primary] is the one action colour. The signal gradient is reserved
 * for Ishaara itself: the orb, the working glow and the active task ring.
 */
@Immutable
data class IshaaraColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val primary: Color,
    val onPrimary: Color,
    val success: Color,
    val danger: Color,
) {
    val signalColors: List<Color> get() = SignalColors
    val signal: Brush get() = Brush.linearGradient(SignalColors, Offset.Zero, Offset.Infinite)
}

val SignalColors = listOf(Color(0xFF5FFFC8), Color(0xFF17C3A2), Color(0xFF3A9BFF))
val BrandLight = Color(0xFF17453A)
val BrandDark = Color(0xFF0B211B)

val DarkColors = IshaaraColors(
    background = Color(0xFF0E0E11),
    surface = Color(0xFF1B1B20),
    surfaceRaised = Color(0xFF2A2A31),
    divider = Color(0x14FFFFFF),
    textPrimary = Color(0xFFF4F4F6),
    textSecondary = Color(0xFF9D9DA8),
    primary = Color(0xFFF4F4F6),
    onPrimary = Color(0xFF16161A),
    success = Color(0xFF4CCB95),
    danger = Color(0xFFFF6B6B),
)

val LightColors = IshaaraColors(
    background = Color(0xFFF4F4F7),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFEDEDF1),
    divider = Color(0x1416161A),
    textPrimary = Color(0xFF16161A),
    textSecondary = Color(0xFF6C6C78),
    primary = Color(0xFF16161A),
    onPrimary = Color(0xFFFFFFFF),
    success = Color(0xFF17935F),
    danger = Color(0xFFD6383F),
)

private val LocalColors = staticCompositionLocalOf { LightColors }

object Ishaara {
    val colors: IshaaraColors
        @Composable get() = LocalColors.current
}

@OptIn(ExperimentalTextApi::class)
private fun figtree(weight: Int) = Font(
    R.font.figtree,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private val Figtree = FontFamily(figtree(400), figtree(500), figtree(600), figtree(700))

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun IshaaraTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) DarkColors else LightColors
    val base = if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(
        colorScheme = base.copy(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.divider,
        ),
        typography = AppTypography,
        shapes = AppShapes,
    ) {
        CompositionLocalProvider(LocalColors provides colors, content = content)
    }
}
