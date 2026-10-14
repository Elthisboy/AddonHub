package com.appmcpe.hub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.appmcpe.hub.R
import com.appmcpe.hub.text.UiText
import com.appmcpe.hub.parental.AgeCheck
import com.appmcpe.hub.parental.AgeGroup
import com.appmcpe.hub.parental.ParentalStore
import com.appmcpe.hub.parental.PinHasher
import com.appmcpe.hub.parental.PinResult

private val Red = Color(0xFFC62828)

/** Convierte el resultado de verificar el PIN en un mensaje de error (null = correcto). */
fun pinErrorMessage(result: PinResult): UiText? = when (result) {
    PinResult.Ok -> null
    is PinResult.Wrong -> UiText.of(R.string.pin_wrong, result.remainingTries)
    is PinResult.Locked -> UiText.of(R.string.pin_locked, result.seconds)
}

/** Pide el PIN de 4 números. [onSubmit] devuelve un mensaje de error, o null si fue correcto. */
@Composable
fun PinDialog(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    onSubmit: (String) -> UiText?,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<UiText?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.ExtraBold, fontFamily = PixelFont) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                subtitle?.let { Text(it, fontSize = 14.sp) }
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v ->
                        if (v.length <= 4 && v.all { it.isDigit() }) {
                            pin = v
                            error = null
                            if (v.length == 4) {
                                val e = onSubmit(v)
                                if (e != null) {
                                    error = e
                                    pin = ""
                                }
                            }
                        }
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = error != null,
                    placeholder = { Text("····") },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                error?.let { Text(it.asString(), color = Red, fontSize = 14.sp) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Configuración inicial para un adulto: elegir la edad y crear el PIN. Bloquea toda la app. */
@Composable
fun OnboardingScreen(onDone: (AgeGroup, String) -> Unit) {
    var group by remember { mutableStateOf(AgeGroup.KIDS) }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    val valid = PinHasher.isValidFormat(pin) && pin == pin2

    Box(
        Modifier
            .fillMaxSize()
            .background(Sky)
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mascot(size = 72.dp)
                Spacer(Modifier.width(14.dp))
                Text(stringResource(R.string.onboarding_hello), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, fontFamily = PixelFont, color = DarkGreen)
            }
            Text(
                stringResource(R.string.onboarding_intro),
                fontSize = 15.sp,
            )

            AgeGroup.selectable.forEach { g -> AgeCard(g, selected = g == group) { group = g } }

            PinField(stringResource(R.string.pin_create), pin) { pin = it }
            PinField(stringResource(R.string.pin_repeat), pin2) { pin2 = it }
            if (pin.length == 4 && pin2.length == 4 && pin != pin2) {
                Text(stringResource(R.string.pin_mismatch), color = Red, fontSize = 14.sp)
            }
            Text(
                stringResource(R.string.pin_note),
                fontSize = 13.sp,
                color = Color(0xFF37474F),
            )
            BigButton(stringResource(R.string.btn_save_start), Green, enabled = valid) { onDone(group, pin) }
        }
    }
}

@Composable
private fun PinField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.length <= 4 && v.all { it.isDigit() }) onChange(v) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AgeCard(group: AgeGroup, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(3.dp, if (selected) DarkGreen else Color.Transparent, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(group.emoji, fontSize = 32.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(group.labelRes), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, fontFamily = PixelFont)
            Text(stringResource(group.descriptionRes), fontSize = 13.sp, color = Color(0xFF37474F))
        }
        if (selected) Text("✅", fontSize = 22.sp)
    }
}

/** Zona de padres: edad, palabras bloqueadas, permitidos, herramientas y cambio de PIN. */
@Composable
fun ParentZoneScreen(onClose: () -> Unit) {
    val settings by ParentalStore.settings.collectAsState()
    var newWord by remember { mutableStateOf("") }
    var changingPin by remember { mutableStateOf(false) }
    var secretTaps by remember { mutableStateOf(0) }
    var adultDialog by remember { mutableStateOf(false) }
    BackHandler(onBack = onClose)

    Box(
        Modifier
            .fillMaxSize()
            .background(Sky)
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.zone_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold, fontFamily = PixelFont,
                    color = DarkGreen,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkGreen),
                ) { Text(stringResource(R.string.close)) }
            }

            ZoneSection(stringResource(R.string.sec_age)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AgeGroup.selectable.forEach { g ->
                        AgeCard(g, selected = g == settings.ageGroup) { ParentalStore.setAgeGroup(g) }
                    }
                }
            }

            ZoneSection(stringResource(R.string.sec_words)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.words_hint),
                        fontSize = 13.sp,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newWord,
                            onValueChange = { newWord = it },
                            placeholder = { Text(stringResource(R.string.words_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { ParentalStore.addWord(newWord); newWord = "" },
                            enabled = newWord.trim().length >= 2,
                        ) { Text(stringResource(R.string.add)) }
                    }
                    settings.customWords.sorted().forEach { w ->
                        ListRow(w, stringResource(R.string.remove)) { ParentalStore.removeWord(w) }
                    }
                }
            }

            ZoneSection(stringResource(R.string.sec_allowed)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (settings.allowed.isEmpty()) {
                        Text(
                            stringResource(R.string.allowed_empty),
                            fontSize = 13.sp,
                        )
                    }
                    settings.allowed.forEach { (id, name) ->
                        ListRow(name, stringResource(R.string.remove)) { ParentalStore.disallow(id) }
                    }
                }
            }

            ZoneSection(stringResource(R.string.sec_advanced)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.tools_toggle_title), fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.tools_toggle_desc),
                            fontSize = 13.sp,
                        )
                    }
                    Switch(checked = settings.toolsEnabled, onCheckedChange = ParentalStore::setToolsEnabled)
                }
            }

            OutlinedButton(
                onClick = { changingPin = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text(stringResource(R.string.change_pin), fontSize = 16.sp) }

            // Entrada escondida del modo adulto: tocar 5 veces este texto.
            Text(
                stringResource(R.string.zone_footer),
                fontSize = 12.sp,
                color = Color(0xFF37474F),
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    secretTaps++
                    if (secretTaps >= SECRET_TAPS) {
                        secretTaps = 0
                        adultDialog = true
                    }
                },
            )
        }
    }

    if (changingPin) ChangePinDialog(onDismiss = { changingPin = false })
    if (adultDialog) {
        AdultModeDialog(
            alreadyVerified = settings.adultVerified,
            onDismiss = { adultDialog = false },
            onActivated = {
                adultDialog = false
                onClose() // vuelve al catálogo, ya con el modo adulto activo
            },
        )
    }
}

private const val SECRET_TAPS = 5

/** Cuántos intentos fallidos de fecha se permiten antes de bloquear el diálogo hasta reiniciar la app. */
private const val MAX_AGE_TRIES = 3
private var failedAgeTries = 0

/**
 * Modo adulto: sin filtro de edad mientras la app esté abierta. La primera vez pide la fecha de
 * nacimiento (solo se usa para calcular la edad, no se guarda). Lo sexual sigue bloqueado.
 */
@Composable
private fun AdultModeDialog(alreadyVerified: Boolean, onDismiss: () -> Unit, onActivated: () -> Unit) {
    var day by remember { mutableStateOf("") }
    var month by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var confirmed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiText?>(null) }
    val locked = failedAgeTries >= MAX_AGE_TRIES

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.adult_title), fontWeight = FontWeight.ExtraBold, fontFamily = PixelFont) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    locked -> Text(stringResource(R.string.adult_locked))
                    alreadyVerified -> Text(
                        stringResource(R.string.adult_verified_text),
                        fontSize = 14.sp,
                    )
                    else -> {
                        Text(stringResource(R.string.adult_birth_text), fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DateField(stringResource(R.string.day), day, 2, Modifier.weight(1f)) { day = it }
                            DateField(stringResource(R.string.month), month, 2, Modifier.weight(1f)) { month = it }
                            DateField(stringResource(R.string.year), year, 4, Modifier.weight(1.4f)) { year = it }
                        }
                        Row(
                            Modifier.clickable { confirmed = !confirmed },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                            Text(stringResource(R.string.adult_confirm), fontSize = 13.sp)
                        }
                    }
                }
                error?.let { Text(it.asString(), color = Red, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !locked && (alreadyVerified || (confirmed && day.isNotEmpty() && month.isNotEmpty() && year.length == 4)),
                onClick = {
                    if (alreadyVerified) {
                        ParentalStore.setAdultMode(true)
                        onActivated()
                    } else when (AgeCheck.check(year.toInt(), month.toInt(), day.toInt())) {
                        AgeCheck.Result.Adult -> {
                            ParentalStore.markAdultVerified()
                            ParentalStore.setAdultMode(true)
                            onActivated()
                        }
                        AgeCheck.Result.Minor -> {
                            failedAgeTries++
                            error = UiText.of(R.string.adult_minor)
                        }
                        AgeCheck.Result.InvalidDate -> {
                            failedAgeTries++
                            error = UiText.of(R.string.adult_invalid_date)
                        }
                    }
                },
            ) { Text(stringResource(R.string.activate)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun DateField(label: String, value: String, maxLen: Int, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.length <= maxLen && v.all { it.isDigit() }) onChange(v) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun ChangePinDialog(onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    val valid = PinHasher.isValidFormat(pin) && pin == pin2
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_pin_title), fontWeight = FontWeight.ExtraBold, fontFamily = PixelFont) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinField(stringResource(R.string.new_pin), pin) { pin = it }
                PinField(stringResource(R.string.repeat_it), pin2) { pin2 = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { ParentalStore.setPin(pin); onDismiss() },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ZoneSection(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(14.dp),
    ) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, fontFamily = PixelFont, color = DarkGreen)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun ListRow(text: String, action: String, onAction: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF1F8E9))
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), fontSize = 15.sp, maxLines = 2)
        TextButton(onClick = onAction) { Text(action, color = Red) }
    }
}
