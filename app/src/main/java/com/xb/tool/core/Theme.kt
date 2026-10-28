package com.xb.tool.core

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

data class ColorPreset(val key: String, val label: String, val color: Color)

val colorPresets = listOf(
    ColorPreset("teal", "青碧", Color(0xFF4DD0C0)),
    ColorPreset("blue", "星蓝", Color(0xFF7AB8FF)),
    ColorPreset("purple", "雾紫", Color(0xFFC9A7FF)),
    ColorPreset("green", "松绿", Color(0xFF86D98F)),
    ColorPreset("orange", "暖橙", Color(0xFFFFB86B)),
    ColorPreset("pink", "樱粉", Color(0xFFFF9FC3)),
    ColorPreset("red", "绯红", Color(0xFFFF8A80))
)

private fun contrastOn(background: Color): Color =
    if (background.luminance() > 0.5f) Color(0xFF0B1412) else Color(0xFFF2FFFC)

private fun schemeFor(seed: Color, dark: Boolean): androidx.compose.material3.ColorScheme {
    val background = if (dark) Color(0xFF08110F) else Color(0xFFF6FBF8)
    val surface = if (dark) Color(0xFF0F1A17) else Color(0xFFFFFFFF)
    val surfaceVariant = if (dark) lerp(surface, seed, 0.10f) else lerp(surface, seed, 0.07f)
    val primary = if (dark) seed else lerp(seed, Color(0xFF001815), 0.32f)
    val onPrimary = contrastOn(primary)
    val primaryContainer = if (dark) lerp(surface, seed, 0.24f) else lerp(surface, seed, 0.20f)
    val onPrimaryContainer = if (dark) lerp(Color.White, seed, 0.20f) else lerp(seed, Color.Black, 0.55f)
    val secondary = if (dark) lerp(seed, Color(0xFF9EBDBA), 0.55f) else lerp(seed, Color(0xFF3A615C), 0.55f)
    val tertiary = if (dark) lerp(seed, Color(0xFFFFC98A), 0.55f) else lerp(seed, Color(0xFF8A5A00), 0.55f)
    val onSurface = if (dark) Color(0xFFE2F0EC) else Color(0xFF15211E)
    val outline = lerp(onSurface, surface, 0.45f)
    val errorColor = if (dark) Color(0xFFFFB4AB) else Color(0xFFBA1A1A)
    if (dark) {
        return darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = contrastOn(secondary),
            secondaryContainer = lerp(surface, secondary, 0.22f),
            onSecondaryContainer = onSurface,
            tertiary = tertiary,
            onTertiary = contrastOn(tertiary),
            tertiaryContainer = lerp(surface, tertiary, 0.22f),
            onTertiaryContainer = onSurface,
            background = background,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = lerp(onSurface, surface, 0.22f),
            outline = outline,
            outlineVariant = lerp(outline, surface, 0.35f),
            error = errorColor,
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            scrim = Color.Black
        )
    }
    return lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = contrastOn(secondary),
        secondaryContainer = lerp(surface, secondary, 0.16f),
        onSecondaryContainer = onSurface,
        tertiary = tertiary,
        onTertiary = contrastOn(tertiary),
        tertiaryContainer = lerp(surface, tertiary, 0.16f),
        onTertiaryContainer = onSurface,
        background = background,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = lerp(onSurface, surface, 0.20f),
        outline = outline,
        outlineVariant = lerp(outline, surface, 0.35f),
        error = errorColor,
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        scrim = Color.Black
    )
}

private fun typographyWith(family: FontFamily): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family)
    )
}

private val XBShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun XBTheme(state: AppState, content: @Composable () -> Unit) {
    val config = state.theme
    // 纯色背景时，明暗跟随背景亮度自动切换：
    // 浅色背景用浅色主题（深色文字），深色背景用深色主题，避免文字看不清
    val solidLuminance = config.backgroundColor
        ?.let { hex -> runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull() }
        ?.luminance()
    // 背景图 / 背景视频上会盖一层 42%~58% 的黑色遮罩，
    // 实际显示底色一定是偏暗的。如果这时还跟随系统用浅色主题，
    // 深色文字会压在暗背景上（侧边栏、顶栏、卡片都会发黑看不清）。
    // 因此只要设置了媒体背景，就统一使用深色主题，保证对比度。
    val hasMediaBackground = config.backgroundPath != null || config.backgroundVideoPath != null
    val dark = when {
        solidLuminance != null -> solidLuminance < 0.5f
        hasMediaBackground -> true
        else -> when (config.darkMode) {
            "light" -> false
            "dark" -> true
            else -> isSystemInDarkTheme()
        }
    }
    val seed = colorPresets.firstOrNull { it.key == config.seed }?.color ?: colorPresets.first().color
    val family = when (config.fontFamily) {
        "serif" -> FontFamily.Serif
        "mono" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        else -> FontFamily.Default
    }
    val density = LocalDensity.current
    val scaledDensity = Density(
        density = density.density,
        fontScale = (density.fontScale * config.fontScale).coerceIn(0.7f, 2f)
    )
    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = schemeFor(seed, dark),
            typography = typographyWith(family),
            shapes = XBShapes,
            content = content
        )
    }
}

fun isDarkTheme(state: AppState): Boolean = when (state.theme.darkMode) {
    "light" -> false
    "dark" -> true
    else -> false
}
