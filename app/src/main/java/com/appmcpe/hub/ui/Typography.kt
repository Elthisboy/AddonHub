package com.appmcpe.hub.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.appmcpe.hub.R

/**
 * Fuente pixelada para títulos, botones y pestañas (Silkscreen, licencia SIL OFL 1.1; ver
 * assets/licenses). Los textos largos (descripciones) usan la fuente normal para leerse bien.
 */
val PixelFont = FontFamily(
    Font(R.font.silkscreen_regular, FontWeight.Normal),
    Font(R.font.silkscreen_bold, FontWeight.Bold),
)
