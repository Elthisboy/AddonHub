package com.appmcpe.hub.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Mood { HAPPY, WORKING, SAD }

/**
 * Hubi, la mascota de AddonHub: una carita cuadrada hecha de "píxeles", dibujada en código
 * (sin imágenes). Es un personaje propio, sin relación con personajes de Minecraft.
 */
@Composable
fun Mascot(modifier: Modifier = Modifier, size: Dp = 96.dp, mood: Mood = Mood.HAPPY, animated: Boolean = true) {
    // Rebota suave; más rápido cuando está trabajando (descargando).
    val bob = rememberInfiniteTransition(label = "mascot-bob")
    val dy by bob.animateFloat(
        initialValue = 0f,
        targetValue = if (animated) (if (mood == Mood.WORKING) -6f else -3f) else 0f,
        animationSpec = infiniteRepeatable(
            tween(if (mood == Mood.WORKING) 450 else 900, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "mascot-dy",
    )
    Canvas(modifier.size(size).graphicsLayer { translationY = dy * density }) {
        val u = this.size.minDimension / 16f // cuadrícula de 16×16
        fun px(x: Int, y: Int, w: Int, h: Int, c: Color) =
            drawRect(c, Offset(x * u, y * u), Size(w * u, h * u))

        val dark = Color(0xFF2E7D32)
        val face = Color(0xFF66BB6A)
        val light = Color(0xFF81C784)
        val eye = Color.White
        val pupil = Color(0xFF1B3A1E)
        val cheek = Color(0xFFFFAB91)

        px(1, 1, 14, 14, dark)
        px(2, 2, 12, 12, face)
        px(2, 2, 12, 2, light)

        // Ojos
        px(4, 6, 3, 3, eye)
        px(9, 6, 3, 3, eye)
        val py = if (mood == Mood.WORKING) 8 else 7 // trabajando: mira hacia abajo
        px(5, py, 1, 1, pupil)
        px(10, py, 1, 1, pupil)

        // Mejillas
        px(3, 10, 1, 1, cheek)
        px(12, 10, 1, 1, cheek)

        // Boca
        when (mood) {
            Mood.HAPPY -> {
                px(5, 11, 6, 1, pupil)
                px(4, 10, 1, 1, pupil)
                px(11, 10, 1, 1, pupil)
            }
            Mood.WORKING -> px(6, 11, 4, 1, pupil)
            Mood.SAD -> {
                px(5, 11, 6, 1, pupil)
                px(4, 12, 1, 1, pupil)
                px(11, 12, 1, 1, pupil)
            }
        }
    }
}
