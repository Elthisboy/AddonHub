package com.appmcpe.hub.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.appmcpe.hub.catalog.CfMod
import com.appmcpe.hub.catalog.ExternalLinks
import com.appmcpe.hub.parental.ParentalStore
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val detail by vm.detail.collectAsState()
    val session by vm.session.collectAsState()
    val parental by vm.parental.collectAsState()
    var parentStep by remember { mutableStateOf(ParentStep.NONE) }
    var askAdultFor by remember { mutableStateOf<CfMod?>(null) }
    var openLinkFor by remember { mutableStateOf<CfMod?>(null) }

    // Android 13+: pedir permiso de notificaciones la primera vez que se descarga algo,
    // para poder avisar con la app cerrada. Si lo niegan, la descarga igual funciona.
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(session != null) {
        if (session != null && !askedNotifications && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askedNotifications = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    AddonHubTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(Sky)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
        // Herramientas se salta el filtro: solo existe si un adulto la activó.
        // Pestañas: 0 Explorar · 1 Favoritos · 2 Descargas · 3 Herramientas (solo si un adulto la activó).
        val activeTab = if (tab == 3 && !parental.toolsEnabled) 0 else tab
        val favorites by vm.favoriteIds.collectAsState()

        Column(Modifier.fillMaxSize()) {
            AppHeader(
                adultMode = parental.adultMode,
                onAdultOff = { ParentalStore.setAdultMode(false) },
                onParents = { parentStep = ParentStep.PIN },
            )
            TabBar(activeTab, parental.toolsEnabled) { tab = it }
            Box(Modifier.fillMaxSize().padding(top = 8.dp)) {
                when (activeTab) {
                    0 -> CatalogScreen(vm)
                    1 -> FavoritesScreen(vm)
                    2 -> DownloadsScreen(vm)
                    else -> ToolsScreen(vm)
                }
            }
        }

        // Capas de arriba: detalle (resumen y preview) y pantalla de descarga, que bloquea todo.
        detail?.let { d ->
            DetailScreen(
                detail = d,
                onBack = vm::closeDetail,
                onInstall = vm::installFromDetail,
                onToggleOptional = vm::toggleOptional,
                onAskAdult = { askAdultFor = d.mod },
                favorite = d.mod.id in favorites,
                onToggleFavorite = { vm.toggleFavorite(d.mod.id) },
                onOpenCurseForge = { openLinkFor = d.mod },
            )
        }
        session?.let { s ->
            DownloadScreen(
                session = s,
                onClose = vm::dismissSession,
                onRetry = vm::retrySession,
                onNext = vm::importNext,
            )
        }

        // Zona de padres: primero el PIN, luego los ajustes.
        when (parentStep) {
            ParentStep.PIN -> PinDialog(
                title = "Zona de padres",
                subtitle = "Escribe tu PIN de 4 números.",
                onDismiss = { parentStep = ParentStep.NONE },
                onSubmit = { pin ->
                    pinErrorMessage(ParentalStore.verifyPin(pin)).also { err ->
                        if (err == null) parentStep = ParentStep.ZONE
                    }
                },
            )
            ParentStep.ZONE -> ParentZoneScreen(onClose = { parentStep = ParentStep.NONE })
            ParentStep.NONE -> {}
        }

        // Un adulto aprueba un contenido bloqueado por el filtro de edad.
        askAdultFor?.let { mod ->
            PinDialog(
                title = "Pedir permiso",
                subtitle = "Un adulto debe escribir el PIN para permitir «${mod.name}».",
                onDismiss = { askAdultFor = null },
                onSubmit = { pin ->
                    pinErrorMessage(ParentalStore.verifyPin(pin)).also { err ->
                        if (err == null) {
                            vm.allowMod(mod)
                            askAdultFor = null
                        }
                    }
                },
            )
        }

        // Salir de la app hacia la página del autor en CurseForge: solo con el PIN de un adulto.
        openLinkFor?.let { mod ->
            PinDialog(
                title = "Ver en CurseForge",
                subtitle = "Un adulto debe escribir el PIN para abrir la página de «${mod.name}» en el navegador.",
                onDismiss = { openLinkFor = null },
                onSubmit = { pin ->
                    pinErrorMessage(ParentalStore.verifyPin(pin)).also { err ->
                        if (err == null) {
                            openLinkFor = null
                            openCurseForgePage(context, mod.websiteUrl)
                        }
                    }
                },
            )
        }

        // Primera vez: un adulto elige la edad y crea el PIN. Tapa toda la app.
        if (!parental.onboarded) {
            OnboardingScreen(onDone = ParentalStore::completeOnboarding)
        }
        }
    }
}

private enum class ParentStep { NONE, PIN, ZONE }

/** Abre la página en el navegador, solo si de verdad es de curseforge.com. */
private fun openCurseForgePage(context: android.content.Context, url: String) {
    if (!ExternalLinks.isCurseForgeUrl(url)) return
    try {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: android.content.ActivityNotFoundException) {
        // Sin navegador instalado no hay nada que hacer.
    }
}

/** Título de la app, aviso de modo adulto (si está activo) y el engranaje de la zona de padres. */
@Composable
fun AppHeader(adultMode: Boolean, onAdultOff: () -> Unit, onParents: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "AddonHub",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold, fontFamily = PixelFont,
            maxLines = 1,
            softWrap = false, // nunca partir el nombre de la app en varias líneas
            color = DarkGreen,
            modifier = Modifier.weight(1f),
        )
        if (adultMode) {
            // Aviso visible de que el filtro está apagado; tocarlo lo vuelve a encender.
            Text(
                "🔓 ✕", // corto a propósito: en 320 dp no cabe el nombre de la app junto a un texto largo
                fontSize = 12.sp,
                maxLines = 1,
                fontWeight = FontWeight.Bold,
                fontFamily = PixelFont,
                color = Color.White,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFEF6C00))
                    .clickable(onClick = onAdultOff)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White)
                .clickable(onClick = onParents),
            contentAlignment = Alignment.Center,
        ) { Text("⚙️", fontSize = 22.sp) }
    }
}

/** Pestañas: 0 Explorar · 1 Favoritos · 2 Descargas · 3 Herramientas (solo si un adulto la activó). */
@Composable
fun TabBar(active: Int, toolsEnabled: Boolean, onSelect: (Int) -> Unit) {
    val tabs = buildList {
        add("🔍" to "Explorar")
        add("❤️" to "Favoritos")
        add("📥" to "Descargas")
        if (toolsEnabled) add("🛠️" to "Herramientas")
    }
    // En pantallas estrechas solo la pestaña activa muestra su nombre; las demás, solo el icono.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 480.dp
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tabs.forEachIndexed { i, (emoji, label) ->
                TabPill(emoji, label, selected = i == active, showLabel = !compact || i == active) { onSelect(i) }
            }
        }
    }
}

@Composable
private fun TabPill(emoji: String, label: String, selected: Boolean, showLabel: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) DarkGreen else Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = if (showLabel) 16.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (showLabel) {
            Text(
                "$emoji $label",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = PixelFont,
                maxLines = 1,
                color = if (selected) Color.White else DarkGreen,
            )
        } else {
            Text(emoji, fontSize = 20.sp) // solo el icono; el nombre se lee con el lector de pantalla
        }
    }
}
