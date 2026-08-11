package com.example.ytshortsblocker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Indigo80,
    onPrimary = OnIndigoDark,
    primaryContainer = IndigoContainerDark,
    onPrimaryContainer = Indigo80,
    secondary = Slate80,
    tertiary = Coral80,
    background = SurfaceDark,
    onBackground = TextDark,
    surface = SurfaceDark,
    onSurface = TextDark,
    surfaceVariant = CardDark,
    onSurfaceVariant = Slate80,
)

private val LightColorScheme = lightColorScheme(
    primary = Indigo40,
    primaryContainer = IndigoContainerLight,
    onPrimaryContainer = Indigo40,
    secondary = Slate40,
    tertiary = Coral40,
    background = SurfaceLight,
    surface = SurfaceLight,
    surfaceVariant = CardLightVariant,
)

@Composable
fun YTShortsBlockerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default: dynamic colour would replace the palette above with wallpaper colours, and
    // the status colours only read correctly against a known scheme.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
