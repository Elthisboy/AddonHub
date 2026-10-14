package com.appmcpe.hub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.appmcpe.hub.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.appmcpe.hub.catalog.DepInfo
import com.appmcpe.hub.catalog.InstallPlan

private const val DESCRIPTION_PREVIEW_CHARS = 700

/** Resumen, preview y datos de lo que se va a descargar, antes de instalar. */
@Composable
fun DetailScreen(
    detail: DetailState,
    onBack: () -> Unit,
    onInstall: () -> Unit,
    onToggleOptional: (Int) -> Unit,
    onAskAdult: () -> Unit,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenCurseForge: () -> Unit,
) {
    val mod = detail.mod
    val backLabel = stringResource(R.string.back)
    BackHandler(onBack = onBack)

    val required = InstallPlan.required(mod, detail.infos, detail.selectedOptional)
    val optional = InstallPlan.optional(mod, detail.infos)
    val incompatible = InstallPlan.incompatible(mod, detail.infos)
    val unavailable = InstallPlan.unavailable(mod, detail.infos, detail.selectedOptional)
    val hasDeps = detail.loadingDeps || detail.depsError || required.isNotEmpty() ||
        optional.isNotEmpty() || incompatible.isNotEmpty() || unavailable.isNotEmpty()
    // Todo lo que se descargará (sin contar el contenido principal).
    val extraCount = InstallPlan.buildPlan(mod, detail.infos, detail.selectedOptional).size - 1

    Column(
        Modifier
            .fillMaxSize()
            .background(Sky)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .semantics { contentDescription = backLabel; role = Role.Button }
                    .clickable(onClick = onBack)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) { Text("← $backLabel", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = PixelFont, color = DarkGreen) }
            Spacer(Modifier.weight(1f))
            FavoriteHeart(favorite, onToggleFavorite, Modifier.background(Color.White, RoundedCornerShape(20.dp)))
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Preview: capturas de pantalla, o el logo si no hay.
            val images = mod.screenshots.ifEmpty { listOfNotNull(mod.logoUrl) }
            if (images.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(images) { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = stringResource(R.string.image_of, mod.name),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 300.dp, height = 170.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFE0E0E0)),
                        )
                    }
                }
            }

            // Nombre de terceros: fuente normal (la pixelada es muy ancha para nombres largos).
            Text(mod.name, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = DarkGreen)
            if (mod.author.isNotBlank()) Text(stringResource(R.string.by_author, mod.author), fontSize = 15.sp, color = Color(0xFF37474F))
            if (mod.websiteUrl.isNotBlank()) {
                // Enlace de vuelta al autor en CurseForge; pide el PIN porque es una salida de la app.
                Text(
                    stringResource(R.string.detail_open_curseforge),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkGreen,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClick = onOpenCurseForge)
                        .padding(vertical = 14.dp), // zona táctil de al menos 48 dp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip("⬇ ${compactCount(mod.downloads)}")
                InfoChip("🧩 ${stringResource(contentTypeRes(mod.fileName))}")
                formatSize(mod.sizeBytes).takeIf { it.isNotEmpty() }?.let { InfoChip("📦 $it") }
            }

            Section(stringResource(R.string.sec_download)) {
                Text(mod.fileName, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                if (extraCount > 0) {
                    Text(stringResource(R.string.extra_needed, extraCount), fontSize = 14.sp, color = DarkGreen)
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    stringResource(R.string.install_note),
                    fontSize = 14.sp,
                )
            }

            // Siempre se muestra, para que quede claro que sí se revisó.
            run {
                Section(stringResource(R.string.sec_others)) {
                    if (!hasDeps) {
                        Text(
                            stringResource(R.string.no_deps),
                            fontSize = 14.sp,
                        )
                    }
                    when {
                        detail.loadingDeps -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 3.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.checking_deps), fontSize = 14.sp)
                        }
                        detail.depsError -> Text(
                            stringResource(R.string.deps_error),
                            fontSize = 14.sp,
                        )
                        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (required.isNotEmpty()) {
                                DepGroup(stringResource(R.string.dep_required))
                                required.forEach { DepRow(it, null, null) }
                            }
                            if (unavailable.isNotEmpty()) {
                                DepGroup(stringResource(R.string.dep_unavailable))
                                unavailable.forEach {
                                    Text(stringResource(R.string.dep_unavailable_item, it.name), fontSize = 14.sp)
                                }
                            }
                            if (optional.isNotEmpty()) {
                                DepGroup(stringResource(R.string.dep_optional))
                                optional.forEach { dep ->
                                    DepRow(dep, dep.id in detail.selectedOptional) { onToggleOptional(dep.id) }
                                }
                            }
                            if (incompatible.isNotEmpty()) {
                                DepGroup(stringResource(R.string.dep_incompatible))
                                incompatible.forEach { Text("• ${it.name}", fontSize = 14.sp) }
                            }
                        }
                    }
                }
            }

            // Requisitos que el autor menciona en la descripción pero no declaró como dependencia.
            if (detail.requirements.isNotEmpty()) {
                Section(stringResource(R.string.sec_author_asks)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        detail.requirements.forEach { Text("• ${it.asString()}", fontSize = 14.sp) }
                    }
                }
            }

            if (mod.summary.isNotBlank()) {
                Section(stringResource(R.string.sec_summary)) { Text(mod.summary, fontSize = 16.sp) }
            }

            Section(stringResource(R.string.sec_description)) {
                when {
                    detail.loadingDescription -> CircularProgressIndicator(Modifier.size(28.dp))
                    detail.description.isNullOrBlank() -> Text(stringResource(R.string.no_description), fontSize = 14.sp)
                    else -> ExpandableText(detail.description)
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Box(Modifier.padding(16.dp)) {
            when {
                detail.blockReason != null && detail.blockIsPermanent ->
                    BigButton("🔒 ${detail.blockReason.asString()}", Color.Gray, enabled = false) {}
                detail.blockReason != null ->
                    BigButton(stringResource(R.string.btn_ask_permission, detail.blockReason.asString()), Color(0xFFEF6C00), onClick = onAskAdult)
                else -> BigButton(
                    if (extraCount > 0) stringResource(R.string.btn_install_all, extraCount + 1) else stringResource(R.string.btn_install),
                    Green,
                    enabled = !detail.loadingDeps,
                    onClick = onInstall,
                )
            }
        }
    }
}

@Composable
private fun DepGroup(title: String) {
    Text(title, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = DarkGreen)
}

/** Una dependencia con su logo; con [checked] no nulo muestra una casilla para elegirla. */
@Composable
private fun DepRow(dep: DepInfo, checked: Boolean?, onToggle: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = dep.logoUrl,
            contentDescription = dep.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFE0E0E0)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(dep.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            dep.mod?.let { Text(formatSize(it.sizeBytes), fontSize = 12.sp, color = Color.Gray) }
        }
        if (checked != null) Checkbox(checked = checked, onCheckedChange = { onToggle?.invoke() })
    }
}

@Composable
private fun ExpandableText(text: String) {
    var expanded by remember(text) { mutableStateOf(false) }
    val long = text.length > DESCRIPTION_PREVIEW_CHARS
    Text(
        if (expanded || !long) text else text.take(DESCRIPTION_PREVIEW_CHARS).trimEnd() + "…",
        fontSize = 14.sp,
    )
    if (long) {
        Text(
            if (expanded) stringResource(R.string.read_less) else stringResource(R.string.read_more),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = DarkGreen,
            modifier = Modifier.padding(top = 8.dp).clickable { expanded = !expanded },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
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
private fun InfoChip(text: String) {
    Text(
        text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = DarkGreen,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
