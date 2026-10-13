package com.appmcpe.hub.ui

import android.app.Application
import android.net.Uri
import android.text.Html
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appmcpe.hub.catalog.Category
import com.appmcpe.hub.catalog.CfMod
import com.appmcpe.hub.catalog.CurseForgeApi
import com.appmcpe.hub.catalog.DepInfo
import com.appmcpe.hub.catalog.InstallPlan
import com.appmcpe.hub.catalog.Relation
import com.appmcpe.hub.catalog.Requirements
import com.appmcpe.hub.catalog.Sort
import com.appmcpe.hub.catalog.Subcategory
import com.appmcpe.hub.download.Backend
import com.appmcpe.hub.download.Downloader
import com.appmcpe.hub.download.Http
import com.appmcpe.hub.install.InstallItem
import com.appmcpe.hub.install.InstallManager
import com.appmcpe.hub.install.InstallSession
import com.appmcpe.hub.install.InstallSpec
import com.appmcpe.hub.parental.ContentFilter
import com.appmcpe.hub.parental.ParentalSettings
import com.appmcpe.hub.parental.ParentalStore
import com.appmcpe.hub.parental.Verdict
import com.appmcpe.hub.user.HistoryEntry
import com.appmcpe.hub.user.UserLists
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DetailState(
    val mod: CfMod,
    val description: String? = null,
    val loadingDescription: Boolean = true,
    /** Cosas que el autor pide en la descripción (experimentos, "necesitas instalar X"…). */
    val requirements: List<String> = emptyList(),
    val loadingDeps: Boolean = false,
    val depsError: Boolean = false,
    val infos: Map<Int, DepInfo> = emptyMap(),
    val selectedOptional: Set<Int> = emptySet(),
    /** Motivo por el que el filtro de edad bloquea este contenido, o null si se puede instalar. */
    val blockReason: String? = null,
    /** El bloqueo es de contenido para adultos y ni un PIN lo puede levantar. */
    val blockIsPermanent: Boolean = false,
)

data class CatalogState(
    val category: Category = Category.ADDONS,
    val subcategory: Subcategory? = null,
    val subcategories: List<Subcategory> = emptyList(),
    val sort: Sort = Sort.POPULAR,
    /** Texto buscado (ya enviado). Vacío = navegar por categoría. */
    val query: String = "",
    /** Con búsqueda activa: true = en todas las categorías, false = solo en la elegida. */
    val searchAll: Boolean = true,
    val mods: List<CfMod> = emptyList(),
    val nextIndex: Int = 0,
    val loading: Boolean = false,
    val hasMore: Boolean = false,
    val limited: Boolean = false,
    val error: String? = null,
    /** La búsqueda escrita contiene algo que el filtro de edad no permite. */
    val blockedQuery: Boolean = false,
)

/** Contenido de una pestaña de la biblioteca (Favoritos o Mis descargas), pedido en vivo. */
data class LibraryState(
    val mods: List<CfMod> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    /** Cuántos guardados ya no están disponibles (borrados, bloqueados por edad…). */
    val hidden: Int = 0,
)

/** Se lanza cuando el filtro de edad impide instalar algo. El mensaje se muestra al niño. */
class ContentBlockedException(message: String) : Exception(message)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val _catalog = MutableStateFlow(CatalogState())
    val catalog: StateFlow<CatalogState> = _catalog
    private var loadJob: Job? = null

    /** La instalación vive en [InstallManager] para que siga con la app en segundo plano. */
    val session: StateFlow<InstallSession?> = InstallManager.session

    private val _detail = MutableStateFlow<DetailState?>(null)
    val detail: StateFlow<DetailState?> = _detail
    private var detailJob: Job? = null
    private var depsJob: Job? = null

    private val _diagnosis = MutableStateFlow<List<String>?>(null)
    val diagnosis: StateFlow<List<String>?> = _diagnosis

    /** Ajustes de los padres (edad, palabras bloqueadas, permitidos). */
    val parental: StateFlow<ParentalSettings> = ParentalStore.settings

    val favoriteIds: StateFlow<List<Int>> = UserLists.favorites
    val history: StateFlow<List<HistoryEntry>> = UserLists.history

    private val _favoriteMods = MutableStateFlow(LibraryState())
    val favoriteMods: StateFlow<LibraryState> = _favoriteMods
    private val _historyMods = MutableStateFlow(LibraryState())
    val historyMods: StateFlow<LibraryState> = _historyMods
    private var favoritesJob: Job? = null
    private var historyJob: Job? = null

    fun toggleFavorite(modId: Int) = UserLists.toggleFavorite(modId)
    fun clearHistory() = UserLists.clearHistory()

    /** Vuelve a pedir los favoritos (se llama al abrir la pestaña o cuando cambian). */
    fun loadFavorites() {
        favoritesJob?.cancel()
        favoritesJob = loadLibrary(UserLists.favorites.value, _favoriteMods)
    }

    fun loadHistory() {
        historyJob?.cancel()
        historyJob = loadLibrary(UserLists.history.value.map { it.modId }, _historyMods)
    }

    private fun loadLibrary(ids: List<Int>, target: MutableStateFlow<LibraryState>): Job {
        target.value = LibraryState(mods = target.value.mods, loading = true)
        return viewModelScope.launch {
            try {
                val all = CurseForgeApi.modsByIds(ids)
                val settings = ParentalStore.settings.value
                val visible = ContentFilter.filter(all, settings)
                target.value = LibraryState(mods = visible, hidden = ids.size - visible.size)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                target.value = LibraryState(error = e.message ?: "No se pudo cargar")
            }
        }
    }

    init {
        loadCatalog(reset = true)
        viewModelScope.launch {
            try {
                _catalog.value = _catalog.value.copy(subcategories = CurseForgeApi.subcategories())
            } catch (_: Exception) {
                // Sin subcategorías el catálogo sigue funcionando.
            }
        }
        // Si los padres cambian algo que afecta qué se ve, se recarga el catálogo.
        viewModelScope.launch {
            ParentalStore.settings
                .map { Triple(it.effectiveGroup, it.customWords, it.allowed.keys) }
                .distinctUntilChanged()
                .drop(1)
                .collect { loadCatalog(reset = true) }
        }
    }

    // ---------- Catálogo ----------

    fun selectCategory(category: Category) {
        _catalog.value = _catalog.value.copy(category = category, subcategory = null)
        loadCatalog(reset = true)
    }

    fun selectSubcategory(sub: Subcategory?) {
        _catalog.value = _catalog.value.copy(subcategory = sub)
        loadCatalog(reset = true)
    }

    fun selectSort(sort: Sort) {
        _catalog.value = _catalog.value.copy(sort = sort)
        loadCatalog(reset = true)
    }

    fun search(query: String) {
        _catalog.value = _catalog.value.copy(query = query.trim())
        loadCatalog(reset = true)
    }

    fun setSearchAll(all: Boolean) {
        _catalog.value = _catalog.value.copy(searchAll = all)
        if (_catalog.value.query.isNotBlank()) loadCatalog(reset = true)
    }

    fun loadMore() = loadCatalog(reset = false)

    fun loadCatalog(reset: Boolean) {
        loadJob?.cancel()
        val current = _catalog.value
        val settings = ParentalStore.settings.value

        // El buscador no puede usarse para llegar a lo que el filtro oculta.
        if (ContentFilter.isQueryBlocked(current.query, settings)) {
            _catalog.value = current.copy(
                mods = emptyList(), loading = false, hasMore = false, error = null, blockedQuery = true,
            )
            return
        }

        val index = if (reset) 0 else current.nextIndex
        _catalog.value = current.copy(
            loading = true,
            error = null,
            blockedQuery = false,
            mods = if (reset) emptyList() else current.mods,
        )
        loadJob = viewModelScope.launch {
            try {
                // El filtro oculta contenidos; si una página queda casi vacía se piden más.
                var page = CurseForgeApi.load(
                    current.category, current.subcategory, current.sort, current.query, index,
                    current.searchAll,
                )
                var visible = ContentFilter.filter(page.mods, settings)
                var extraPages = 0
                while (visible.size < MIN_VISIBLE && page.hasMore && extraPages < MAX_EXTRA_PAGES) {
                    page = CurseForgeApi.load(
                        current.category, current.subcategory, current.sort, current.query,
                        page.nextIndex, current.searchAll,
                    )
                    visible = visible + ContentFilter.filter(page.mods, settings)
                    extraPages++
                }
                val s = _catalog.value
                _catalog.value = s.copy(
                    mods = if (reset) visible.distinctBy { it.id } else (s.mods + visible).distinctBy { it.id },
                    nextIndex = page.nextIndex,
                    hasMore = page.hasMore,
                    limited = page.limited,
                    loading = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _catalog.value = _catalog.value.copy(
                    loading = false,
                    error = e.message ?: "No se pudo cargar el catálogo",
                )
            }
        }
    }

    // ---------- Detalle ----------

    private fun hasDependencies(mod: CfMod) =
        mod.dependencies.any { it.type != Relation.EMBEDDED && it.type != Relation.INCLUDE }

    fun openDetail(mod: CfMod) {
        detailJob?.cancel()
        depsJob?.cancel()
        _detail.value = DetailState(mod, loadingDeps = hasDependencies(mod))

        detailJob = viewModelScope.launch {
            var text: String? = null
            var requirements = emptyList<String>()
            var verdict = Verdict.OK
            try {
                val html = CurseForgeApi.description(mod.id)
                withContext(Dispatchers.Default) {
                    text = htmlToText(html)
                    requirements = Requirements.extract(text.orEmpty())
                    verdict = ContentFilter.checkDescription(text.orEmpty(), mod, ParentalStore.settings.value)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Sin descripción el detalle sigue sirviendo.
            }
            updateDetail(mod.id) {
                it.copy(
                    description = text,
                    requirements = requirements,
                    loadingDescription = false,
                    blockReason = verdict.reason,
                    blockIsPermanent = verdict.permanent,
                )
            }
        }

        if (hasDependencies(mod)) {
            depsJob = viewModelScope.launch {
                try {
                    val infos = CurseForgeApi.resolveDependencies(mod)
                    updateDetail(mod.id) { it.copy(infos = infos, loadingDeps = false) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    updateDetail(mod.id) { it.copy(loadingDeps = false, depsError = true) }
                }
            }
        }
    }

    private fun updateDetail(modId: Int, change: (DetailState) -> DetailState) {
        _detail.update { d -> if (d != null && d.mod.id == modId) change(d) else d }
    }

    fun toggleOptional(depId: Int) {
        _detail.update { d ->
            d?.copy(
                selectedOptional = if (depId in d.selectedOptional) d.selectedOptional - depId
                else d.selectedOptional + depId,
            )
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        depsJob?.cancel()
        _detail.value = null
    }

    /** Instala desde la pantalla de detalle, con las dependencias ya resueltas y las opcionales elegidas. */
    fun installFromDetail() {
        val d = _detail.value ?: return
        if (d.loadingDeps || d.blockReason != null) return
        installMod(
            d.mod, d.selectedOptional, d.infos.takeIf { hasDependencies(d.mod) && !d.depsError },
            // Si la descripción ya se revisó al abrir el detalle, no se vuelve a pedir.
            checkDescription = d.description == null,
        )
    }

    /** Un adulto (ya validado con el PIN) permite este contenido pese al filtro de edad. */
    fun allowMod(mod: CfMod) {
        ParentalStore.allow(mod.id, mod.name)
        updateDetail(mod.id) { it.copy(blockReason = null, blockIsPermanent = false) }
    }

    private fun htmlToText(html: String): String =
        Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString()
            .replace('\uFFFC', ' ') // marcadores de imágenes
            .replace(Regex("[ \\t]+\n"), "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

    // ---------- Instalación (la ejecuta InstallManager, también con la app en segundo plano) ----------

    /**
     * Instala [mod] junto con todo lo que necesita (y las opcionales elegidas en [optional]).
     * [known] son las dependencias ya resueltas; si es null se consultan aquí.
     */
    fun installMod(
        mod: CfMod,
        optional: Set<Int> = emptySet(),
        known: Map<Int, DepInfo>? = null,
        checkDescription: Boolean = true,
    ) {
        val ctx = getApplication<Application>()
        InstallManager.start(mod.name, mod.logoUrl, retry = { installMod(mod, optional, known, checkDescription) }) {
            // Desde la tarjeta no se vio la descripción: se revisa antes de bajar nada.
            val settings = ParentalStore.settings.value
            if (ContentFilter.check(mod, settings).blocked) {
                throw ContentBlockedException("🔒 Este contenido no está permitido para ti.")
            }
            if (checkDescription) {
                val text = try {
                    withContext(Dispatchers.Default) { htmlToText(CurseForgeApi.description(mod.id)) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null // sin descripción no hay nada más que revisar
                }
                if (text != null && ContentFilter.checkDescription(text, mod, settings).blocked) {
                    throw ContentBlockedException(
                        "🔒 Este contenido necesita permiso de un adulto. Ábrelo con «VER» y pídelo ahí.",
                    )
                }
            }
            val infos = known
                ?: if (hasDependencies(mod)) CurseForgeApi.resolveDependencies(mod) else emptyMap()

            val notes = InstallPlan.unavailable(mod, infos, optional).map {
                "Necesita «${it.name}», que no se puede descargar desde aquí. Búscalo en CurseForge."
            }
            val items = InstallPlan.buildPlan(mod, infos, optional).map { m ->
                InstallItem(m.name, modId = m.id) { progress ->
                    Downloader.download(ctx, m.downloadUrl, "${m.id}-${m.fileName}", progress)
                }
            }
            InstallSpec(items, notes)
        }
    }

    fun installTestPack() {
        val ctx = getApplication<Application>()
        InstallManager.start("Pack de prueba", null, retry = ::installTestPack) {
            InstallSpec(listOf(InstallItem("Pack de prueba") { Downloader.copyTestPack(ctx) }))
        }
    }

    fun installFromUrl(url: String) {
        val ctx = getApplication<Application>()
        InstallManager.start("Descarga por enlace", null, retry = { installFromUrl(url) }) {
            InstallSpec(listOf(InstallItem("Archivo") { progress -> Downloader.download(ctx, url.trim(), null, progress) }))
        }
    }

    fun installFromUri(uri: Uri) {
        val ctx = getApplication<Application>()
        InstallManager.start("Archivo del teléfono", null, retry = { installFromUri(uri) }) {
            InstallSpec(listOf(InstallItem("Archivo") { Downloader.copyFromUri(ctx, uri) }))
        }
    }

    fun importNext() = InstallManager.importNext()
    fun dismissSession() = InstallManager.dismiss()
    fun retrySession() = InstallManager.retry()

    private companion object {
        /** Si tras filtrar quedan menos que esto, se pide otra página. */
        const val MIN_VISIBLE = 12
        const val MAX_EXTRA_PAGES = 4
    }

    // ---------- Diagnóstico de red ----------

    fun runDiagnosis() {
        _diagnosis.value = listOf("⏳ Probando conexión…")
        viewModelScope.launch(Dispatchers.IO) {
            _diagnosis.value = listOf(
                Http.probe("Internet (google)", "https://www.google.com/generate_204", false),
                Http.probe(
                    if (Backend.usesProxy) "Servidor de AddonHub" else "api.curseforge.com",
                    "${Backend.apiBase}/categories?gameId=78022",
                    true,
                ),
                Http.probe("edge.forgecdn.net", "https://edge.forgecdn.net/", false),
            )
        }
    }
}
