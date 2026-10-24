package com.ultradownloader.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.ultradownloader.app.data.local.AppLanguage
import com.ultradownloader.app.data.local.ThemePreference

private val DarkColorScheme = darkColorScheme(
    primary = ElectricPurple,
    secondary = ElectricBlue,
    tertiary = IndigoPulse,
    background = ObsidianBackground,
    surface = ObsidianSurface,
    surfaceVariant = ObsidianCard,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = ObsidianBorder,
    error = CrimsonError
)

private val LightColorScheme = lightColorScheme(
    primary = ElectricPurple,
    secondary = IndigoPulse,
    tertiary = ElectricBlue,
    background = DaylightBackground,
    surface = DaylightSurface,
    surfaceVariant = DaylightCard,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF475569),
    outline = DaylightBorder,
    error = CrimsonError
)

@Composable
fun UltraDownloaderTheme(
    themePreference: ThemePreference = ThemePreference.DARK,
    language: AppLanguage = AppLanguage.ARABIC,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themePreference) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme
    val layoutDirection = if (language == AppLanguage.ARABIC) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = UltraTypography,
            content = content
        )
    }
}
