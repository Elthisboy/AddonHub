package com.appmcpe.hub.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Green = Color(0xFF4CAF50)
val DarkGreen = Color(0xFF2E7D32)
val Sky = Color(0xFF81D4FA)

private val Colors = lightColorScheme(
    primary = DarkGreen,
    onPrimary = Color.White,
    secondary = Green,
    onSecondary = Color.White,
    tertiary = Color(0xFFEF6C00),
    surface = Color.White,
    onSurface = Color(0xFF1B1B1B),
    error = Color(0xFFC62828),
    outline = Color(0xFF607D8B),
)

/** Colores de AddonHub (verde y cielo). Sin esto, Material pinta de morado los botones y casillas. */
@Composable
fun AddonHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
