package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SlateColorScheme = darkColorScheme(
    primary = SlatePrimary,
    onPrimary = Color.Black,
    secondary = SlateSecondary,
    onSecondary = Color.Black,
    background = SlateBackground,
    onBackground = Color(0xFFF1F5F9), // Slate 100
    surface = SlateSurface,
    onSurface = Color(0xFFF8FAFC), // Slate 50
    error = SlateAccent,
    tertiary = SlateAccent
)

private val CyberColorScheme = darkColorScheme(
    primary = CyberPrimary,
    onPrimary = Color.Black,
    secondary = CyberSecondary,
    onSecondary = Color.Black,
    background = CyberBackground,
    onBackground = Color(0xFFFAFAFA),
    surface = CyberSurface,
    onSurface = Color(0xFFFAFAFA),
    error = CyberAccent,
    tertiary = CyberAccent
)

private val OceanColorScheme = darkColorScheme(
    primary = OceanPrimary,
    onPrimary = Color.Black,
    secondary = OceanSecondary,
    onSecondary = Color.Black,
    background = OceanBackground,
    onBackground = Color(0xFFF0F9FF),
    surface = OceanSurface,
    onSurface = Color(0xFFF0F9FF),
    error = OceanAccent,
    tertiary = OceanAccent
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    secondary = LightSecondary,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = Color(0xFF0F172A),
    surface = LightSurface,
    onSurface = Color(0xFF0F172A),
    error = LightAccent,
    tertiary = LightAccent
)

@Composable
fun IPTVTheme(
    themeState: String,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeState) {
        "CYBER" -> CyberColorScheme
        "OCEAN" -> OceanColorScheme
        "LIGHT" -> LightColorScheme
        else -> SlateColorScheme // Default is SLATE
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
