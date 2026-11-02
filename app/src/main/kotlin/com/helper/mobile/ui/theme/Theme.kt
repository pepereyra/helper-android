package com.helper.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Naranja de acción principal, igual que en Figma.
private val Orange = Color(0xFFE0661B)
private val OrangeSoft = Color(0xFFFDE7D9)
private val Ink = Color(0xFF1B1F24)

private val LightColors = lightColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    primaryContainer = OrangeSoft,
    onPrimaryContainer = Ink,
    error = Color(0xFFC62828),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF9A5C),
    onPrimary = Ink,
    primaryContainer = Color(0xFF5A2A0C),
    onPrimaryContainer = OrangeSoft,
)

@Composable
fun HelperTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
