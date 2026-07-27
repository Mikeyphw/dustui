package com.mikeyphw.dustui.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BaseTypography = Typography()

private val DustTypography = Typography(
    displayLarge = BaseTypography.displayLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = BaseTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = BaseTypography.labelSmall.copy(letterSpacing = 0.sp),
    labelMedium = BaseTypography.labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    bodySmall = BaseTypography.bodySmall.copy(lineHeight = 18.sp, letterSpacing = 0.sp),
    bodyMedium = BaseTypography.bodyMedium.copy(letterSpacing = 0.sp),
)

object DustModernColors {
    val Background = Color(0xFF080A0C)
    val Surface = Color(0xFF101417)
    val SurfaceHigh = Color(0xFF171C20)
    val SurfaceHighest = Color(0xFF20272D)
    val OnSurface = Color(0xFFF4F7F8)
    val OnSurfaceMuted = Color(0xFF9AA4AD)
    val OnSurfaceDim = Color(0xFF68727C)
    val Accent = Color(0xFFC9FF73)
    val OnAccent = Color(0xFF142006)
    val AccentContainer = Color(0xFF243318)
    val Cyan = Color(0xFF76EADF)
    val CyanContainer = Color(0xFF163532)
    val Warning = Color(0xFFFFC56D)
    val WarningContainer = Color(0xFF382818)
    val Danger = Color(0xFFFF8E8E)
    val DangerContainer = Color(0xFF381F23)
    val Success = Color(0xFF8EE8A2)
    val SuccessContainer = Color(0xFF183121)
    val SelectionDock = Color(0xFF252D24)
}

private val DustDarkColorScheme = darkColorScheme(
    primary = DustModernColors.Accent,
    onPrimary = DustModernColors.OnAccent,
    primaryContainer = DustModernColors.AccentContainer,
    onPrimaryContainer = DustModernColors.Accent,
    secondary = DustModernColors.Cyan,
    onSecondary = DustModernColors.OnAccent,
    secondaryContainer = DustModernColors.CyanContainer,
    onSecondaryContainer = DustModernColors.Cyan,
    tertiary = DustModernColors.Success,
    onTertiary = DustModernColors.OnAccent,
    tertiaryContainer = DustModernColors.SuccessContainer,
    onTertiaryContainer = DustModernColors.Success,
    error = DustModernColors.Danger,
    onError = Color(0xFF281012),
    errorContainer = DustModernColors.DangerContainer,
    onErrorContainer = DustModernColors.Danger,
    background = DustModernColors.Background,
    onBackground = DustModernColors.OnSurface,
    surface = DustModernColors.Surface,
    onSurface = DustModernColors.OnSurface,
    surfaceVariant = DustModernColors.SurfaceHigh,
    onSurfaceVariant = DustModernColors.OnSurfaceMuted,
    outline = DustModernColors.OnSurfaceDim,
    outlineVariant = DustModernColors.SurfaceHighest,
)

private val DustLightColorScheme = lightColorScheme(
    primary = Color(0xFF476900),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0FFAD),
    onPrimaryContainer = Color(0xFF142006),
    secondary = Color(0xFF006A63),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF9EF2E8),
    onSecondaryContainer = Color(0xFF00201D),
    tertiary = Color(0xFF1A7130),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFA9F5B7),
    onTertiaryContainer = Color(0xFF00210A),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8FAF7),
    onBackground = Color(0xFF191C18),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C18),
    surfaceVariant = Color(0xFFE1E4DC),
    onSurfaceVariant = Color(0xFF44483F),
)

private val DustShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val DustPathFont = FontFamily.Monospace

@Composable
fun DustTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val useDarkPalette = darkTheme || isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (useDarkPalette) DustDarkColorScheme else DustLightColorScheme,
        typography = DustTypography,
        shapes = DustShapes,
        content = content,
    )
}
