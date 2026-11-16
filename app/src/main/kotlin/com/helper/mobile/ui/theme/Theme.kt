package com.helper.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Naranja de acción principal, igual que en Figma, y grises neutros: la app usa solo esos dos tonos.
private val Orange = Color(0xFFE0661B)
private val OrangeSoft = Color(0xFFFDE7D9)
private val Ink = Color(0xFF1B1F24)
private val InkSoft = Color(0xFF5B6168)

private val LightColors = lightColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    primaryContainer = OrangeSoft,
    onPrimaryContainer = Ink,
    // Chips seleccionados y la pestaña activa de la barra inferior: naranja suave, no el lila de Material.
    secondary = InkSoft,
    onSecondary = Color.White,
    secondaryContainer = OrangeSoft,
    onSecondaryContainer = Ink,
    tertiary = InkSoft,
    tertiaryContainer = Color(0xFFEFEFEF),
    onTertiaryContainer = Ink,
    background = Color(0xFFFCFCFC),
    onBackground = Ink,
    surface = Color(0xFFFCFCFC),
    onSurface = Ink,
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = InkSoft,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F7F7),
    surfaceContainer = Color(0xFFF2F2F2),
    surfaceContainerHigh = Color(0xFFEDEDED),
    surfaceContainerHighest = Color(0xFFE8E8E8),
    outline = Color(0xFFB9BDC2),
    outlineVariant = Color(0xFFDDDFE2),
    error = Color(0xFFC62828),
    errorContainer = Color(0xFFFBE4E1),
    onErrorContainer = Color(0xFF5F1410),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF9A5C),
    onPrimary = Ink,
    primaryContainer = Color(0xFF5A2A0C),
    onPrimaryContainer = OrangeSoft,
    secondary = Color(0xFFC4C7CB),
    secondaryContainer = Color(0xFF5A2A0C),
    onSecondaryContainer = OrangeSoft,
    surfaceTint = Color.Transparent,
)

@Composable
fun HelperTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
