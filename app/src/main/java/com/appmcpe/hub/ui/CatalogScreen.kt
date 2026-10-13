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
            placeholder = { Text("Buscar contenido, creador o palabra…") },
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
                item { Chip("🌐 Buscar en todo", state.searchAll) { vm.setSearchAll(true) } }
                item { Chip("Solo en ${state.category.label}", !state.searchAll) { vm.setSearchAll(false) } }
            }
        } else {
            // Orden
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(Sort.entries) { s -> Chip(s.label, s == state.sort) { vm.selectSort(s) } }
            }
        }
        // Subcategorías (Terror, Magia, Aventura…)
        if (!searching && subcategories.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Chip("Todo", state.subcategory == null) { vm.selectSubcategory(null) } }
                items(subcategories, key = { it.id }) { sub ->
                    Chip(sub.name, sub.id == state.subcategory?.id) { vm.selectSubcategory(sub) }
                }
            }
        }

        if (state.limited) {
            Text(
                "Mostrando solo lo más popular (la búsqueda completa no está disponible).",
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
                                tier.label,
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
                            Text("❌ ${state.error}", color = Color(0xFFC62828), fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { vm.loadCatalog(reset = state.mods.isEmpty()) }) {
                                Text("Reintentar")
                            }
                        }
                        state.blockedQuery -> Text(
                            "Esa búsqueda no está disponible 🔒\nProbá con otra palabra.",
                            fontSize = 16.sp,
                        )
                        state.mods.isEmpty() -> Text(
                            if (searching) "No encontramos «${state.query}» 😕\nProbá con otra palabra."
                            else "No encontramos nada 😕",
                            fontSize = 16.sp,
                        )
                        state.hasMore -> Button(
                            onClick = vm::loadMore,
                            colors = ButtonDefaults.buttonColors(containerColor = DarkGreen),
                        ) { Text("Ver más") }
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
                    c.label,
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
    Box(
        modifier
            .size(40.dp)
            .clip(RoundedCornerShape(20.dp))
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
            AsyncImage(
                model = mod.logoUrl,
                contentDescription = mod.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFE0E0E0)),
            )
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
                    Text("por ${mod.author}", fontSize = 13.sp, color = Color.Gray, maxLines = 1)
                }
                Text(
                    "⬇ ${compactCount(mod.downloads)}  ·  ${contentType(mod.fileName)}  ·  ${formatSize(mod.sizeBytes)}",
                    fontSize = 13.sp,
                    color = DarkGreen,
                    fontWeight = FontWeight.Bold,
                )
                if (mod.dependencies.any { it.type == Relation.REQUIRED }) {
                    Text("🔗 Necesita otros contenidos", fontSize = 13.sp, color = Color(0xFF8D6E00), fontWeight = FontWeight.Bold)
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
            ) { Text("👀 VER", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, fontFamily = PixelFont, maxLines = 1) }
            Button(
                onClick = onInstall,
                modifier = Modifier.weight(1.4f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = tight,
                colors = ButtonDefaults.buttonColors(containerColor = Green),
            ) { Text("⬇️ INSTALAR", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, fontFamily = PixelFont, maxLines = 1) }
        }
    }
}
