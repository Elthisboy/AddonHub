package com.appmcpe.hub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.appmcpe.hub.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.appmcpe.hub.catalog.Category
import com.appmcpe.hub.catalog.CfMod
import com.appmcpe.hub.catalog.Relation
import com.appmcpe.hub.catalog.SearchRanker
import com.appmcpe.hub.catalog.Sort
import com.appmcpe.hub.parental.ContentFilter

@Composable
fun CatalogScreen(vm: MainViewModel) {
    val state by vm.catalog.collectAsState()
    var text by remember { mutableStateOf(state.query) }
    val parental by vm.parental.collectAsState()
    val favorites by vm.favoriteIds.collectAsState()
    // Las subcategorías que el filtro de edad bloquea (Horror) ni siquiera se ofrecen.
    val subcategories = state.subcategories.filter {
        it.classId == state.category.classId && !ContentFilter.isCategoryBlocked(it.id, it.slug, parental)
    }
    val searching = state.query.isNotBlank()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            singleLine = true,
            trailingIcon = {
                if (text.isNotEmpty() || state.query.isNotEmpty()) {
                    Text(
                        "✕",
                        fontSize = 18.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { text = ""; vm.search("") }
                            .padding(12.dp),
                    )
                }
            },
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search(text) }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))

        CategoryRow(state.category, vm::selectCategory)

        Spacer(Modifier.height(8.dp))
        if (searching) {
            // Con búsqueda: el orden lo da la relevancia; solo se elige dónde buscar.
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Chip(stringResource(R.string.search_all), state.searchAll) { vm.setSearchAll(true) } }
                item {
                    Chip(
                        stringResource(R.string.search_only_in, stringResource(state.category.labelRes)),
                        !state.searchAll,
                    ) { vm.setSearchAll(false) }
                }
            }
        } else {
            // Orden
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(Sort.entries) { s -> Chip(stringResource(s.labelRes), s == state.sort) { vm.selectSort(s) } }
            }
        }
        // Subcategorías (Terror, Magia, Aventura…)
        if (!searching && subcategories.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Chip(stringResource(R.string.sub_all), state.subcategory == null) { vm.selectSubcategory(null) } }
                items(subcategories, key = { it.id }) { sub ->
                    Chip(sub.name.asString(), sub.id == state.subcategory?.id) { vm.selectSubcategory(sub) }
                }
            }
        }

        if (state.limited) {
            Text(
                stringResource(R.string.limited_notice),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(state.mods, key = { _, mod -> mod.id }) { i, mod ->
                Column {
                    if (searching) {
                        // Título de grupo cuando cambia el nivel: contenido → creador → palabras clave.
                        val tier = SearchRanker.tierOf(mod, state.query)
                        if (i == 0 || tier != SearchRanker.tierOf(state.mods[i - 1], state.query)) {
                            Text(
                                stringResource(tier.labelRes),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold, fontFamily = PixelFont,
                                color = DarkGreen,
                                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp, top = if (i == 0) 0.dp else 6.dp),
                            )
                        }
                    }
                    ModCard(
                        mod,
                        favorite = mod.id in favorites,
                        onToggleFavorite = { vm.toggleFavorite(mod.id) },
                        onOpen = { vm.openDetail(mod) },
                        onInstall = { vm.installMod(mod) },
                    )
                }
            }
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when {
                        state.loading -> CircularProgressIndicator()
                        state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.error_with_icon, state.error!!.asString()), color = Color(0xFFC62828), fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { vm.loadCatalog(reset = state.mods.isEmpty()) }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                        state.blockedQuery -> Text(
                            stringResource(R.string.blocked_search),
                            fontSize = 16.sp,
                        )
                        state.mods.isEmpty() -> Text(
                            if (searching) stringResource(R.string.no_results_for, state.query)
                            else stringResource(R.string.no_results),
                            fontSize = 16.sp,
                        )
                        state.hasMore -> Button(
                            onClick = vm::loadMore,
                            colors = ButtonDefaults.buttonColors(containerColor = DarkGreen),
                        ) { Text(stringResource(R.string.load_more)) }
                    }
                }
            }
        }
    }
}

/** Las 4 categorías principales (Addons, Mapas, Texturas, Skins) en una fila de botones iguales. */
@Composable
fun CategoryRow(selected: Category, onSelect: (Category) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Category.entries.forEach { c ->
            val isSelected = c == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) Green else Color.White)
                    .clickable { onSelect(c) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(c.emoji, fontSize = 24.sp)
                Text(
                    stringResource(c.labelRes),
                    fontSize = 9.sp, // la fuente pixelada es ancha: con más tamaño "TEXTURAS" no cabe en 320 dp
                    fontWeight = FontWeight.Bold,
                    fontFamily = PixelFont,
                    maxLines = 1,
                    color = if (isSelected) Color.White else DarkGreen,
                )
            }
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = PixelFont,
        color = if (selected) Color.White else DarkGreen,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) DarkGreen else Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Corazón para marcar un contenido como favorito. */
@Composable
fun FavoriteHeart(favorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(if (favorite) R.string.fav_remove else R.string.fav_add)
    Box(
        modifier
            .size(48.dp) // 48 dp: tamaño mínimo recomendado para zonas táctiles
            .clip(RoundedCornerShape(24.dp))
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) { Text(if (favorite) "❤️" else "🤍", fontSize = 22.sp) }
}

@Composable
fun ModCard(
    mod: CfMod,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
    /** Texto extra bajo los datos (por ejemplo "Instalado el 12/10"). */
    note: String? = null,
    /** Icono que se ve mientras carga el logo o si el contenido no tiene. */
    fallbackEmoji: String = "🧩",
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .clickable(onClick = onOpen)
            .padding(12.dp),
    ) {
        Row {
            // Decorativa (null): el nombre ya se lee en el título. Sin logo se muestra un icono.
            Box(
                Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE0E0E0)),
                contentAlignment = Alignment.Center,
            ) {
                Text(fallbackEmoji, fontSize = 32.sp)
                AsyncImage(
                    model = mod.logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                // El nombre es contenido de terceros (largo e impredecible): fuente normal, legible.
                Text(
                    mod.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (mod.author.isNotBlank()) {
                    Text(stringResource(R.string.by_author, mod.author), fontSize = 13.sp, color = Color.Gray, maxLines = 1)
                }
                Text(
                    "⬇ ${compactCount(mod.downloads)} · ${stringResource(contentTypeRes(mod.fileName))} · ${formatSize(mod.sizeBytes)}",
                    fontSize = 12.sp,
                    color = DarkGreen,
                    fontWeight = FontWeight.Bold,
                )
                if (mod.dependencies.any { it.type == Relation.REQUIRED }) {
                    Text(stringResource(R.string.needs_others), fontSize = 13.sp, color = Color(0xFF8D6E00), fontWeight = FontWeight.Bold)
                }
                note?.let { Text(it, fontSize = 12.sp, color = Color.Gray) }
            }
            FavoriteHeart(favorite, onToggleFavorite)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val tight = PaddingValues(horizontal = 6.dp)
            Button(
                onClick = onOpen,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = tight,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8F5E9), contentColor = DarkGreen),
            ) { Text(stringResource(R.string.btn_view), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, fontFamily = PixelFont, maxLines = 1) }
            Button(
                onClick = onInstall,
                modifier = Modifier.weight(1.4f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = tight,
                colors = ButtonDefaults.buttonColors(containerColor = Green),
            ) { Text(stringResource(R.string.btn_install), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, fontFamily = PixelFont, maxLines = 1) }
        }
    }
}
