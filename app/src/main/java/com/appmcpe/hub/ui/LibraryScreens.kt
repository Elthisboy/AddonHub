package com.appmcpe.hub.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.appmcpe.hub.R
import java.text.DateFormat
import java.util.Date

@Composable
private fun installedOn(date: String): String = stringResource(R.string.installed_on, date)

/** Pestaña ❤️ Favoritos. Solo guarda ids; los datos se piden en vivo. */
@Composable
fun FavoritesScreen(vm: MainViewModel) {
    val ids by vm.favoriteIds.collectAsState()
    val state by vm.favoriteMods.collectAsState()
    val parental by vm.parental.collectAsState()
    // Se recarga al abrir la pestaña y cada vez que cambia la lista o el filtro de edad.
    LaunchedEffect(ids, parental.effectiveGroup, parental.customWords, parental.allowed.keys) { vm.loadFavorites() }

    LibraryList(
        vm = vm,
        state = state,
        favorites = ids,
        emptyTitle = stringResource(R.string.fav_empty_title),
        emptyText = stringResource(R.string.fav_empty_text),
        noteFor = { null },
        footer = null,
    )
}

/** Pestaña 📥 Mis descargas: lo que ya se envió a Minecraft desde la app. */
@Composable
fun DownloadsScreen(vm: MainViewModel) {
    val history by vm.history.collectAsState()
    val favorites by vm.favoriteIds.collectAsState()
    val state by vm.historyMods.collectAsState()
    val parental by vm.parental.collectAsState()
    LaunchedEffect(history, parental.effectiveGroup, parental.customWords, parental.allowed.keys) { vm.loadHistory() }

    val dates = history.associate { it.modId to it.installedAt }
    val format = DateFormat.getDateInstance(DateFormat.MEDIUM)

    LibraryList(
        vm = vm,
        state = state,
        favorites = favorites,
        emptyTitle = stringResource(R.string.hist_empty_title),
        emptyText = stringResource(R.string.hist_empty_text),
        noteFor = { mod -> dates[mod.id]?.let { installedOn(format.format(Date(it))) } },
        footer = if (history.isNotEmpty()) {
            { OutlinedButton(onClick = vm::clearHistory, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.hist_clear)) } }
        } else null,
    )
}

@Composable
private fun LibraryList(
    vm: MainViewModel,
    state: LibraryState,
    favorites: List<Int>,
    emptyTitle: String,
    emptyText: String,
    noteFor: @Composable (com.appmcpe.hub.catalog.CfMod) -> String?,
    footer: (@Composable () -> Unit)?,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.mods, key = { it.id }) { mod ->
            ModCard(
                mod,
                favorite = mod.id in favorites,
                onToggleFavorite = { vm.toggleFavorite(mod.id) },
                onOpen = { vm.openDetail(mod) },
                onInstall = { vm.installMod(mod) },
                note = noteFor(mod),
            )
        }
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    state.loading && state.mods.isEmpty() -> CircularProgressIndicator()
                    state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.error_with_icon, state.error!!.asString()), color = Color(0xFFC62828), fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { vm.loadFavorites(); vm.loadHistory() }) { Text(stringResource(R.string.retry)) }
                    }
                    state.mods.isEmpty() -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 32.dp),
                    ) {
                        Mascot(size = 96.dp, mood = Mood.HAPPY)
                        Spacer(Modifier.height(12.dp))
                        Text(emptyTitle, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, fontFamily = PixelFont, color = DarkGreen, textAlign = TextAlign.Center)
                        Text(emptyText, fontSize = 14.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        if (state.hidden > 0) {
            item {
                Text(
                    stringResource(R.string.lib_hidden, state.hidden),
                    fontSize = 12.sp,
                    color = Color(0xFF37474F),
                )
            }
        }
        footer?.let { item { it() } }
    }
}
