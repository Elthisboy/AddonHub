package com.appmcpe.hub.catalog

import androidx.annotation.StringRes
import com.appmcpe.hub.R
import com.appmcpe.hub.download.Backend
import com.appmcpe.hub.download.Http
import com.appmcpe.hub.install.ALLOWED_EXTENSIONS
import com.appmcpe.hub.text.UiText
import com.appmcpe.hub.text.UiTextProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Categorías principales de Minecraft Bedrock en CurseForge (classId). */
enum class Category(val classId: Int, @StringRes val labelRes: Int, val emoji: String) {
    ADDONS(4984, R.string.cat_addons, "🧩"),
    MAPS(6913, R.string.cat_maps, "🗺️"),
    TEXTURES(6929, R.string.cat_textures, "🎨"),
    SKINS(6925, R.string.cat_skins, "🧑"),
}

/** Orden del catálogo. [field] es el sortField de CurseForge. */
enum class Sort(val field: Int, @StringRes val labelRes: Int) {
    POPULAR(2, R.string.sort_popular),
    DOWNLOADS(6, R.string.sort_downloads),
    NEWEST(11, R.string.sort_newest),
    UPDATED(3, R.string.sort_updated),
}

/** [name] es traducible si se conoce la subcategoría; si no, queda el nombre original de CurseForge. */
data class Subcategory(val id: Int, val name: UiText, val classId: Int, val slug: String = "")

/** Tipos de relación de CurseForge entre archivos (relationType). */
object Relation {
    const val EMBEDDED = 1
    const val OPTIONAL = 2
    const val REQUIRED = 3
    const val TOOL = 4
    const val INCOMPATIBLE = 5
    const val INCLUDE = 6
}

data class CfDependency(val modId: Int, val type: Int)

data class CfMod(
    val id: Int,
    val name: String,
    val summary: String,
    val author: String,
    val logoUrl: String?,
    val downloads: Long,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val authors: List<String> = listOfNotNull(author.takeIf { it.isNotBlank() }),
    val categoryIds: List<Int> = emptyList(),
    val dateCreated: String = "",
    val dateModified: String = "",
    val screenshots: List<String> = emptyList(),
    val dependencies: List<CfDependency> = emptyList(),
    /** Página del contenido en curseforge.com (para el botón "Ver en CurseForge" de los adultos). */
    val websiteUrl: String = "",
)

/** Enlaces hacia fuera de la app: solo se abren si de verdad apuntan a CurseForge. */
object ExternalLinks {
    fun isCurseForgeUrl(url: String): Boolean = try {
        val u = java.net.URI(url)
        val host = u.host?.lowercase().orEmpty()
        u.scheme == "https" && u.userInfo == null && (host == "curseforge.com" || host.endsWith(".curseforge.com"))
    } catch (_: Exception) {
        false
    }
}

/** Un contenido del que depende otro. [mod] es null si no se puede descargar directamente. */
data class DepInfo(val id: Int, val name: String, val logoUrl: String?, val mod: CfMod?)

/**
 * [nextIndex] es el cursor de CurseForge para pedir la página siguiente (no es el largo de la
 * lista, porque se descartan contenidos que no se pueden descargar).
 */
data class CatalogPage(
    val mods: List<CfMod>,
    val hasMore: Boolean,
    val limited: Boolean,
    val nextIndex: Int = 0,
)

class CurseForgeException(override val text: UiText, val code: Int = 0) :
    IOException(text.toString()), UiTextProvider

object CurseForgeApi {
    /** Con proxy: nuestro servidor; sin proxy (desarrollo): CurseForge directo. */
    private val BASE: String get() = Backend.apiBase
    private const val GAME_ID = 78022 // Minecraft Bedrock
    const val PAGE_SIZE = 50
    private const val MAX_RESULTS = 10_000 // límite de CurseForge: index + pageSize <= 10000
    private const val MAX_DEP_ROUNDS = 3
    private const val SEARCH_PAGES = 3 // 3 × 50 resultados por búsqueda, ordenados juntos
    private const val SEARCH_SORT = 2 // popularidad: lo mejor de cada nivel sale primero

    /** Nombres traducibles de las subcategorías más comunes (por slug de CurseForge). */
    private val SUBCATEGORY_NAMES = mapOf(
        "survival" to R.string.sub_survival, "horror" to R.string.sub_horror, "magic" to R.string.sub_magic,
        "adventure" to R.string.sub_adventure, "puzzle" to R.string.sub_puzzle, "pvp" to R.string.sub_pvp,
        "mobs" to R.string.sub_mobs, "players" to R.string.sub_players, "fantasy" to R.string.sub_fantasy,
        "technology" to R.string.sub_technology, "utility" to R.string.sub_utility, "roleplay" to R.string.sub_roleplay,
        "multiplayer" to R.string.sub_multiplayer, "food" to R.string.sub_food, "realistic" to R.string.sub_realistic,
        "simplistic" to R.string.sub_simplistic, "themed" to R.string.sub_themed, "minigame" to R.string.sub_minigame,
        "creation" to R.string.sub_creation, "miscellaneous" to R.string.sub_miscellaneous,
        "cosmetics" to R.string.sub_cosmetics, "performance" to R.string.sub_performance,
        "vanilla" to R.string.sub_vanilla, "custom-terrain" to R.string.sub_custom_terrain,
        "armor-tools-and-weapons" to R.string.sub_armor_tools, "rollercoaster" to R.string.sub_rollercoaster,
        "enhanced-visuals" to R.string.sub_enhanced_visuals, "gui" to R.string.sub_gui,
        "skin-packs" to R.string.sub_skin_packs,
    )

    private fun request(url: String, post: String? = null): String {
        val builder = Backend.authorize(Request.Builder().url(url))
            .header("Accept", "application/json")
            .header("User-Agent", Http.USER_AGENT)
        if (post != null) builder.post(post.toRequestBody("application/json".toMediaType()))
        try {
            return Http.execute(builder.build()) { r ->
                if (!r.isSuccessful) throw CurseForgeException(UiText.of(R.string.error_curseforge_status, r.code), r.code)
                r.body?.string() ?: throw CurseForgeException(UiText.of(R.string.error_empty_response))
            }
        } catch (e: CurseForgeException) {
            throw e
        } catch (e: IOException) {
            throw CurseForgeException(
                UiText.of(R.string.error_no_connection, e.javaClass.simpleName, e.message.orEmpty()),
            )
        }
    }

    /** Descripción completa del mod (HTML). */
    suspend fun description(modId: Int): String = withContext(Dispatchers.IO) {
        JSONObject(request("$BASE/mods/$modId/description")).optString("data")
    }

    /**
     * Resuelve las dependencias de [root] (y las requeridas de sus dependencias) con consultas
     * por lotes. Devuelve un mapa id → info; los ids que CurseForge no devuelve quedan sin [DepInfo.mod].
     */
    suspend fun resolveDependencies(root: CfMod): Map<Int, DepInfo> = withContext(Dispatchers.IO) {
        val known = LinkedHashMap<Int, DepInfo>()
        var frontier = root.dependencies
            .filter { it.type != Relation.EMBEDDED && it.type != Relation.INCLUDE }
            .map { it.modId }
        repeat(MAX_DEP_ROUNDS) {
            val need = frontier.filter { it != root.id && it !in known }.distinct()
            if (need.isEmpty()) return@repeat
            val got = fetchInfos(need)
            known.putAll(got)
            // Lo que necesitan las dependencias también hay que resolverlo.
            frontier = got.values.flatMap { info ->
                info.mod?.dependencies.orEmpty().filter { it.type == Relation.REQUIRED }.map { it.modId }
            }
        }
        known
    }

    private fun fetchInfos(ids: List<Int>): Map<Int, DepInfo> {
        val out = HashMap<Int, DepInfo>()
        ids.chunked(50).forEach { chunk ->
            val body = JSONObject().put("modIds", JSONArray(chunk)).toString()
            val data = JSONObject(request("$BASE/mods", body)).getJSONArray("data")
            for (i in 0 until data.length()) {
                val m = data.getJSONObject(i)
                val id = m.getInt("id")
                out[id] = DepInfo(
                    id = id,
                    name = m.optString("name", "Contenido #$id"),
                    logoUrl = m.optJSONObject("logo")?.optString("thumbnailUrl")?.takeIf { it.isNotBlank() },
                    mod = parseMod(m),
                )
            }
        }
        // Ids que CurseForge no devolvió (borrados, privados…).
        ids.filter { it !in out }.forEach { out[it] = DepInfo(it, "Contenido #$it", null, null) }
        return out
    }

    /**
     * Pide en vivo los contenidos con esos [ids] (para Favoritos y Mis descargas, donde solo se
     * guardan ids). Los que ya no existen o no se pueden descargar se omiten. Conserva el orden.
     */
    suspend fun modsByIds(ids: List<Int>): List<CfMod> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val infos = fetchInfos(ids.distinct())
        ids.mapNotNull { infos[it]?.mod }
    }

    /** Subcategorías (Terror, Magia, Aventura…) de todas las categorías principales. */
    suspend fun subcategories(): List<Subcategory> = withContext(Dispatchers.IO) {
        val data = JSONObject(request("$BASE/categories?gameId=$GAME_ID")).getJSONArray("data")
        val out = ArrayList<Subcategory>()
        for (i in 0 until data.length()) {
            val c = data.getJSONObject(i)
            val classId = c.optInt("classId")
            // Solo las hijas directas de una categoría principal; se ignoran eventos (ModJam).
            if (c.optBoolean("isClass") || c.optInt("parentCategoryId") != classId) continue
            val slug = c.optString("slug")
            if (slug.startsWith("modjam")) continue
            val original = c.optString("name")
            val name = SUBCATEGORY_NAMES[slug]?.let { UiText.of(it) } ?: UiText.plain(original)
            out += Subcategory(c.getInt("id"), name, classId, slug)
        }
        // Se ordena por el nombre original (inglés) porque el traducido aún no se conoce aquí.
        out.sortedBy { it.slug }
    }

    /**
     * Sin [query]: lista de la categoría/subcategoría con el orden elegido.
     * Con [query]: busca (en todo si [searchAll]) y ordena por contenido → creador → palabras clave.
     */
    suspend fun load(
        category: Category,
        subcategory: Subcategory?,
        sort: Sort,
        query: String,
        index: Int,
        searchAll: Boolean = true,
    ): CatalogPage = withContext(Dispatchers.IO) {
        if (!Backend.configured) {
            throw CurseForgeException(UiText.of(R.string.error_not_configured))
        }
        try {
            if (query.isBlank()) browse(category, subcategory, sort, index)
            else searchRanked(if (searchAll) null else category, if (searchAll) null else subcategory, query, index)
        } catch (e: CurseForgeException) {
            // Si la key no tiene permiso de búsqueda (403) usamos populares/recientes.
            if (e.code == 403) featured(category, subcategory, sort, query) else throw e
        }
    }

    private class RawPage(val mods: List<CfMod>, val rawCount: Int, val total: Int)

    private fun fetchPage(
        category: Category?,
        subcategory: Subcategory?,
        sortField: Int,
        query: String,
        index: Int,
    ): RawPage {
        val url = buildString {
            append("$BASE/mods/search?gameId=$GAME_ID")
            category?.let { append("&classId=${it.classId}") }
            subcategory?.let { append("&categoryId=${it.id}") }
            append("&sortField=$sortField&sortOrder=desc&pageSize=$PAGE_SIZE&index=$index")
            if (query.isNotBlank()) append("&searchFilter=").append(java.net.URLEncoder.encode(query.trim(), "UTF-8"))
        }
        val root = JSONObject(request(url))
        val data = root.getJSONArray("data")
        val total = minOf(root.optJSONObject("pagination")?.optInt("totalCount", 0) ?: 0, MAX_RESULTS)
        return RawPage(parseMods(data), data.length(), total)
    }

    private fun browse(category: Category, subcategory: Subcategory?, sort: Sort, index: Int): CatalogPage {
        val page = fetchPage(category, subcategory, sort.field, "", index)
        val next = index + page.rawCount
        return CatalogPage(page.mods, hasMore = next < page.total && next + PAGE_SIZE <= MAX_RESULTS, limited = false, nextIndex = next)
    }

    /** Pide varias páginas a la vez y las ordena localmente, para que lo más relevante salga primero. */
    private fun searchRanked(category: Category?, subcategory: Subcategory?, query: String, index: Int): CatalogPage {
        val first = fetchPage(category, subcategory, SEARCH_SORT, query, index)
        val extraIndexes = (1 until SEARCH_PAGES)
            .map { index + it * PAGE_SIZE }
            .filter { it < first.total && it + PAGE_SIZE <= MAX_RESULTS }
        val extra = java.util.concurrent.Executors.newFixedThreadPool(maxOf(1, extraIndexes.size)).let { pool ->
            try {
                extraIndexes.map { i -> pool.submit<RawPage> { fetchPage(category, subcategory, SEARCH_SORT, query, i) } }
                    .map { it.get() }
            } catch (e: java.util.concurrent.ExecutionException) {
                throw (e.cause as? CurseForgeException)
                    ?: CurseForgeException(UiText.of(R.string.error_search_failed, e.cause?.message.orEmpty()))
            } finally {
                pool.shutdown()
            }
        }
        val all = (listOf(first) + extra)
        val mods = SearchRanker.rank(all.flatMap { it.mods }.distinctBy { it.id }, query)
        val next = index + SEARCH_PAGES * PAGE_SIZE
        return CatalogPage(
            mods,
            hasMore = next < first.total && next + PAGE_SIZE <= MAX_RESULTS,
            limited = false,
            nextIndex = next,
        )
    }

    /** Respaldo sin búsqueda: populares + recientes, filtrados y ordenados en el teléfono. */
    private fun featured(
        category: Category,
        subcategory: Subcategory?,
        sort: Sort,
        query: String,
    ): CatalogPage {
        val body = """{"gameId":$GAME_ID,"excludedModIds":[],"gameVersionTypeId":null}"""
        val data = JSONObject(request("$BASE/mods/featured", body)).getJSONObject("data")
        val all = JSONArray().apply {
            for (key in listOf("popular", "recentlyUpdated", "featured")) {
                val arr = data.optJSONArray(key) ?: continue
                for (i in 0 until arr.length()) put(arr.getJSONObject(i))
            }
        }
        val filtered = parseMods(all, classId = category.classId)
            .distinctBy { it.id }
            .filter { subcategory == null || subcategory.id in it.categoryIds }
            .filter {
                query.isBlank() || SearchRanker.tierOf(it, query) != SearchRanker.Tier.KEYWORD ||
                    it.summary.contains(query, ignoreCase = true)
            }
        val sorted = if (query.isNotBlank()) {
            SearchRanker.rank(filtered, query)
        } else when (sort) {
            Sort.POPULAR, Sort.DOWNLOADS -> filtered.sortedByDescending { it.downloads }
            Sort.NEWEST -> filtered.sortedByDescending { it.dateCreated }
            Sort.UPDATED -> filtered.sortedByDescending { it.dateModified }
        }
        return CatalogPage(sorted, hasMore = false, limited = true)
    }

    internal fun parseMods(arr: JSONArray, classId: Int? = null): List<CfMod> {
        val out = ArrayList<CfMod>()
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            if (classId != null && m.optInt("classId") != classId) continue
            parseMod(m)?.let(out::add)
        }
        return out
    }

    /** Convierte un mod de CurseForge; devuelve null si no se puede descargar e importar directamente. */
    internal fun parseMod(m: JSONObject): CfMod? {
        if (!m.optBoolean("allowModDistribution", true)) return null

        // Elegimos el archivo importable más reciente que tenga enlace directo.
        val files = m.optJSONArray("latestFiles") ?: return null
        var best: JSONObject? = null
        for (j in 0 until files.length()) {
            val f = files.getJSONObject(j)
            val ext = f.optString("fileName").substringAfterLast('.', "").lowercase()
            if (ext !in ALLOWED_EXTENSIONS || f.isNull("downloadUrl")) continue
            if (best == null || f.optString("fileDate") > best.optString("fileDate")) best = f
        }
        best ?: return null

        val cats = m.optJSONArray("categories")
        val deps = best.optJSONArray("dependencies")
        return CfMod(
            id = m.getInt("id"),
            name = m.optString("name"),
            summary = m.optString("summary"),
            author = m.optJSONArray("authors")?.optJSONObject(0)?.optString("name").orEmpty(),
            authors = m.optJSONArray("authors")?.let { a ->
                List(a.length()) { a.optJSONObject(it)?.optString("name").orEmpty() }.filter { it.isNotBlank() }
            }.orEmpty(),
            logoUrl = m.optJSONObject("logo")?.optString("thumbnailUrl")?.takeIf { it.isNotBlank() },
            downloads = m.optLong("downloadCount"),
            fileName = best.getString("fileName"),
            downloadUrl = best.getString("downloadUrl"),
            sizeBytes = best.optLong("fileLength"),
            categoryIds = List(cats?.length() ?: 0) { cats!!.getJSONObject(it).getInt("id") },
            dateCreated = m.optString("dateCreated"),
            dateModified = m.optString("dateModified"),
            screenshots = m.optJSONArray("screenshots")?.let { s ->
                List(s.length()) { s.getJSONObject(it).optString("url") }.filter { it.isNotBlank() }
            }.orEmpty(),
            dependencies = List(deps?.length() ?: 0) {
                deps!!.getJSONObject(it).let { d -> CfDependency(d.getInt("modId"), d.getInt("relationType")) }
            },
            websiteUrl = m.optJSONObject("links")?.optString("websiteUrl").orEmpty()
                .takeIf { ExternalLinks.isCurseForgeUrl(it) }.orEmpty(),
        )
    }
}
