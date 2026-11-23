package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.model.NexoraThemeMode

@Immutable
data class NexoraExtendedColors(
    val isDark: Boolean,
    val glassSurface: Color,
    val glassBorder: Color,
    val cyanGlow: Color,
    val violetAccent: Color,
    val codeBackground: Color,
    val codeHeaderBackground: Color,
    val userBubbleBg: Color,
    val aiBubbleBg: Color
)

val LocalNexoraColors = staticCompositionLocalOf {
    NexoraExtendedColors(
        isDark = true,
        glassSurface = MidnightSurface.copy(alpha = 0.78f),
        glassBorder = NeonCyan.copy(alpha = 0.24f),
        cyanGlow = NeonCyan,
        violetAccent = SubtleViolet,
        codeBackground = Color(0xFF060C1C),
        codeHeaderBackground = Color(0xFF0E1833),
        userBubbleBg = ElectricBlueDeep.copy(alpha = 0.35f),
        aiBubbleBg = MidnightSurface.copy(alpha = 0.85f)
    )
}

private val NexoraDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = DeepBlack,
    primaryContainer = ElectricBlueDeep.copy(alpha = 0.35f),
    onPrimaryContainer = ElectricBlueBright,
    secondary = ElectricBlue,
    onSecondary = Color.White,
    secondaryContainer = MidnightElevated,
    onSecondaryContainer = SlateTextPrimary,
    tertiary = SubtleViolet,
    onTertiary = Color.White,
    tertiaryContainer = SubtleVioletDeep.copy(alpha = 0.3f),
    onTertiaryContainer = Color(0xFFDDD6FE),
    background = DeepBlack,
    onBackground = SlateTextPrimary,
    surface = MidnightNavy,
    onSurface = SlateTextPrimary,
    surfaceVariant = MidnightSurface,
    onSurfaceVariant = SlateTextSecondary,
    outline = SlateBorderDark,
    outlineVariant = SlateBorderDark.copy(alpha = 0.6f),
    error = CrimsonError,
    onError = Color.White,
    errorContainer = CrimsonErrorContainer,
    onErrorContainer = Color(0xFFFECACA)
)

private val NexoraLightColorScheme = lightColorScheme(
    primary = ElectricBlueDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = NeonCyanDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF164E63),
    tertiary = SubtleVioletDeep,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF4C1D95),
    background = DaylightBg,
    onBackground = DaylightTextPrimary,
    surface = DaylightSurface,
    onSurface = DaylightTextPrimary,
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = DaylightTextSecondary,
    outline = DaylightBorder,
    outlineVariant = DaylightBorder.copy(alpha = 0.6f),
    error = CrimsonError,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val NexoraShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NexoraTheme(
    themeMode: NexoraThemeMode = NexoraThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        NexoraThemeMode.DARK -> true
        NexoraThemeMode.LIGHT -> false
        NexoraThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDark) NexoraDarkColorScheme else NexoraLightColorScheme
    val extendedColors = if (useDark) {
        NexoraExtendedColors(
            isDark = true,
            glassSurface = MidnightSurface.copy(alpha = 0.78f),
            glassBorder = NeonCyan.copy(alpha = 0.24f),
            cyanGlow = NeonCyan,
            violetAccent = SubtleViolet,
            codeBackground = Color(0xFF060C1C),
            codeHeaderBackground = Color(0xFF0E1833),
            userBubbleBg = Color(0xFF162C5B),
            aiBubbleBg = MidnightSurface.copy(alpha = 0.85f)
        )
    } else {
        NexoraExtendedColors(
            isDark = false,
            glassSurface = DaylightSurface.copy(alpha = 0.92f),
            glassBorder = ElectricBlue.copy(alpha = 0.22f),
            cyanGlow = NeonCyanDeep,
            violetAccent = SubtleVioletDeep,
            codeBackground = Color(0xFF0B1328),
            codeHeaderBackground = Color(0xFF152244),
            userBubbleBg = Color(0xFFDBEAFE),
            aiBubbleBg = DaylightSurface
        )
    }

    CompositionLocalProvider(LocalNexoraColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NexoraTypography,
            shapes = NexoraShapes,
            content = content
        )
    }
}
