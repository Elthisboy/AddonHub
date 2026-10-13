package com.appmcpe.hub.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.appmcpe.hub.install.InstallSession
import com.appmcpe.hub.install.Stage
import com.appmcpe.hub.install.isWorking

/**
 * Pantalla completa que bloquea el resto de la app mientras se descarga e instala.
 * Solo se puede salir con "Cancelar" (durante la descarga) o "Volver" (al terminar).
 */
@Composable
fun DownloadScreen(
    session: InstallSession,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onNext: () -> Unit,
) {
    val stage = session.stage
    val working = stage.isWorking

    // Mientras descarga, "atrás" minimiza la app (la descarga sigue en segundo plano);
    // solo el botón Cancelar la corta.
    val activity = LocalContext.current as? Activity
    BackHandler { if (working) activity?.moveTaskToBack(true) else onClose() }

    // Mantener la pantalla encendida mientras dure la descarga.
    val view = LocalView.current
    DisposableEffect(working) {
        view.keepScreenOn = working
        onDispose { view.keepScreenOn = false }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Sky)
            // Consume todos los toques para que nada de abajo reciba clics.
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (session.logoUrl != null) {
                AsyncImage(
                    model = session.logoUrl,
                    contentDescription = session.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(110.dp).clip(RoundedCornerShape(24.dp)).background(Color.White),
                )
            } else {
                Mascot(
                    size = 110.dp,
                    mood = when (stage) {
                        is Stage.Done, is Stage.ReadyToImport -> Mood.HAPPY
                        is Stage.Error -> Mood.SAD
                        else -> Mood.WORKING
                    },
                )
            }
            // Nombre del contenido (de terceros, largo): fuente normal.
            Text(
                session.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = DarkGreen,
                textAlign = TextAlign.Center,
            )
            if (session.plan.size > 1) {
                Text(
                    "Se instalarán ${session.plan.size} cosas: " + session.plan.joinToString(" → "),
                    fontSize = 13.sp,
                    color = Color(0xFF37474F),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(2.dp))

            when (stage) {
                Stage.Preparing -> {
                    CircularProgressIndicator(color = DarkGreen)
                    Text("Revisando qué necesita…", fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = PixelFont, textAlign = TextAlign.Center)
                    CancelButton("Cancelar", onClose)
                }
                is Stage.Downloading -> {
                    Text(
                        if (stage.count > 1) "Descargando ${stage.index + 1} de ${stage.count}"
                        else "Descargando…",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (stage.count > 1) Text(stage.name, fontSize = 15.sp, textAlign = TextAlign.Center)
                    if (stage.progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { stage.progress },
                            modifier = Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp)),
                            color = DarkGreen,
                            trackColor = Color.White,
                        )
                        Text("${(stage.progress * 100).toInt()}%", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
                        Text("${formatSize(stage.done)} de ${formatSize(stage.total)}", fontSize = 16.sp)
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp)),
                            color = DarkGreen,
                            trackColor = Color.White,
                        )
                        if (stage.done > 0) Text(formatSize(stage.done), fontSize = 16.sp)
                    }
                    Hint("Puedes salir de la app: seguirá descargando y te avisamos cuando termine 🙂")
                    CancelButton("Seguir en segundo plano") { activity?.moveTaskToBack(true) }
                    CancelButton("Cancelar descarga", onClose)
                }
                is Stage.ReadyToImport -> {
                    Text("✅ ¡Descargado!", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFamily = PixelFont, color = DarkGreen)
                    Text(
                        if (stage.count > 1) "Ya están los ${stage.count} archivos. Toca para importarlos a Minecraft."
                        else "Ya está listo. Toca para importarlo a Minecraft.",
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                    )
                    BigButton("📥 IMPORTAR A MINECRAFT", Green, onClick = onNext)
                    CancelButton("Ahora no", onClose)
                }
                is Stage.Opening -> {
                    CircularProgressIndicator(color = DarkGreen)
                    Text("Abriendo Minecraft…", fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = PixelFont, textAlign = TextAlign.Center)
                    Text(stage.name, fontSize = 15.sp, textAlign = TextAlign.Center)
                }
                is Stage.NextStep -> {
                    Text(
                        "Paso ${stage.sent + 1} de ${stage.count} ✅",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold, fontFamily = PixelFont,
                        color = DarkGreen,
                    )
                    Text(
                        "Minecraft está importando «${stage.sentName}». Espera a que termine y " +
                            "vuelve aquí para seguir con «${stage.nextName}».",
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                    )
                    BigButton("Importar siguiente (${stage.sent + 2} de ${stage.count})", Green, onClick = onNext)
                    CancelButton("Terminar aquí", onClose)
                }
                is Stage.Done -> {
                    Text("¡Listo!", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, fontFamily = PixelFont, color = DarkGreen)
                    Text(
                        if (stage.count > 1) {
                            "Minecraft está importando el último archivo. Cuando termine, ya tendrás " +
                                "todo para usarlo."
                        } else {
                            "Minecraft está importando el archivo. Cuando termine, búscalo en tus " +
                                "mundos o en tus packs de recursos."
                        },
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                    )
                    BigButton("Volver", Green, onClick = onClose)
                }
                is Stage.Error -> {
                    Text(
                        stage.message,
                        fontSize = 16.sp,
                        color = Color(0xFFC62828),
                        textAlign = TextAlign.Center,
                    )
                    BigButton("Reintentar", Green, onClick = onRetry)
                    CancelButton("Volver", onClose)
                }
            }

            if (session.notes.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFFF8E1))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("⚠️ Ojo", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, fontFamily = PixelFont, color = Color(0xFF8D6E00))
                    session.notes.forEach { Text("• $it", fontSize = 14.sp) }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 14.sp, color = Color(0xFF37474F), textAlign = TextAlign.Center)
}

@Composable
private fun CancelButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
    ) { Text(text, fontSize = 18.sp) }
}
