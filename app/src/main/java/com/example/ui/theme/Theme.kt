package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.data.AccentPreset

private val BaseDarkColorScheme = darkColorScheme(
    primary = NightBluePrimaryDark,
    onPrimary = Color(0xFF00296B),
    primaryContainer = Color(0xFF003D9B),
    onPrimaryContainer = Color(0xFFD8E2FF),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant
)

private val BaseLightColorScheme = lightColorScheme(
    primary = NightBluePrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001A43),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant
)

@Composable
fun MangaReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentPreset: AccentPreset = AccentPreset.NIGHT_BLUE,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isSystemDynamic = accentPreset == AccentPreset.SYSTEM

    val baseScheme: ColorScheme = when {
        isSystemDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> BaseDarkColorScheme
        else -> BaseLightColorScheme
    }

    // Apply custom accent if not using system dynamic
    val finalColorScheme = if (!isSystemDynamic) {
        val primaryCol = Color(if (darkTheme) accentPreset.darkHex else accentPreset.hex)
        baseScheme.copy(
            primary = primaryCol,
            secondary = primaryCol.copy(alpha = 0.85f),
            primaryContainer = primaryCol.copy(alpha = 0.2f),
            onPrimaryContainer = if (darkTheme) primaryCol.copy(alpha = 0.95f) else primaryCol
        )
    } else {
        baseScheme
    }

    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = Typography,
        content = content
    )
}
