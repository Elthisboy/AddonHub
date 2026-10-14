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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.appmcpe.hub.install.InstallSession
import com.appmcpe.hub.install.Stage
import com.appmcpe.hub.R
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
                    stringResource(R.string.plan_summary, session.plan.size, session.plan.joinToString(" → ")),
                    fontSize = 13.sp,
                    color = Color(0xFF37474F),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(2.dp))

            when (stage) {
                Stage.Preparing -> {
                    CircularProgressIndicator(color = DarkGreen)
                    Text(stringResource(R.string.checking_needs), fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = PixelFont, textAlign = TextAlign.Center)
                    CancelButton(stringResource(R.string.cancel), onClose)
                }
                is Stage.Downloading -> {
                    Text(
                        if (stage.count > 1) stringResource(R.string.downloading_n_of_m, stage.index + 1, stage.count)
                        else stringResource(R.string.downloading),
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
                        Text(stringResource(R.string.size_of, formatSize(stage.done), formatSize(stage.total)), fontSize = 16.sp)
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp)),
                            color = DarkGreen,
                            trackColor = Color.White,
                        )
                        if (stage.done > 0) Text(formatSize(stage.done), fontSize = 16.sp)
                    }
                    Hint(stringResource(R.string.hint_background))
                    CancelButton(stringResource(R.string.btn_background)) { activity?.moveTaskToBack(true) }
                    CancelButton(stringResource(R.string.btn_cancel_download), onClose)
                }
                is Stage.ReadyToImport -> {
                    Text(stringResource(R.string.downloaded_title), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFamily = PixelFont, color = DarkGreen)
                    Text(
                        if (stage.count > 1) stringResource(R.string.ready_multi, stage.count)
                        else stringResource(R.string.ready_single),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                    )
                    BigButton(stringResource(R.string.btn_import), Green, onClick = onNext)
                    CancelButton(stringResource(R.string.not_now), onClose)
                }
                is Stage.Opening -> {
                    CircularProgressIndicator(color = DarkGreen)
                    Text(stringResource(R.string.opening_minecraft), fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = PixelFont, textAlign = TextAlign.Center)
                    Text(stage.name, fontSize = 15.sp, textAlign = TextAlign.Center)
                }
                is Stage.NextStep -> {
                    Text(
                        stringResource(R.string.step_n_of_m, stage.sent + 1, stage.count),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold, fontFamily = PixelFont,
                        color = DarkGreen,
                    )
                    Text(
                        stringResource(R.string.next_step_text, stage.sentName, stage.nextName),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                    )
                    BigButton(stringResource(R.string.btn_import_next, stage.sent + 2, stage.count), Green, onClick = onNext)
                    CancelButton(stringResource(R.string.finish_here), onClose)
                }
                is Stage.Done -> {
                    Text(stringResource(R.string.done_title), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, fontFamily = PixelFont, color = DarkGreen)
                    Text(
                        if (stage.count > 1) stringResource(R.string.done_multi) else stringResource(R.string.done_single),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                    )
                    BigButton(stringResource(R.string.back), Green, onClick = onClose)
                }
                is Stage.Error -> {
                    Text(
                        stage.text.asString(),
                        fontSize = 16.sp,
                        color = Color(0xFFC62828),
                        textAlign = TextAlign.Center,
                    )
                    BigButton(stringResource(R.string.retry), Green, onClick = onRetry)
                    CancelButton(stringResource(R.string.back), onClose)
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
                    Text(stringResource(R.string.notes_title), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, fontFamily = PixelFont, color = Color(0xFF8D6E00))
                    session.notes.forEach { Text("• ${it.asString()}", fontSize = 14.sp) }
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
