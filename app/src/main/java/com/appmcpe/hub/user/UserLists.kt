package com.appmcpe.hub.user

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Una instalación hecha desde el catálogo, para la pestaña "Mis descargas". */
data class HistoryEntry(val modId: Int, val installedAt: Long)

/**
 * Favoritos e historial del usuario. Solo se guardan **ids** (y la fecha): los nombres, imágenes y
 * demás datos se piden a CurseForge en vivo cada vez que se abre la pestaña, porque sus términos
 * no permiten guardar los datos de la API.
 */
object UserLists {
    private lateinit var prefs: SharedPreferences

    private val _favorites = MutableStateFlow<List<Int>>(emptyList())
    /** Más recientes primero. */
    val favorites: StateFlow<List<Int>> = _favorites

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    /** Más recientes primero. */
    val history: StateFlow<List<HistoryEntry>> = _history

    fun init(context: Context) {
        prefs = context.getSharedPreferences("lists", Context.MODE_PRIVATE)
        _favorites.value = decodeIds(prefs.getString(K_FAVORITES, null))
        _history.value = decodeHistory(prefs.getString(K_HISTORY, null))
    }

    fun isFavorite(modId: Int) = modId in _favorites.value

    fun toggleFavorite(modId: Int) {
        val next = toggled(_favorites.value, modId)
        _favorites.value = next
        prefs.edit().putString(K_FAVORITES, encodeIds(next)).apply()
    }

    fun addHistory(modId: Int, now: Long = System.currentTimeMillis()) {
        val next = withEntry(_history.value, HistoryEntry(modId, now))
        _history.value = next
        prefs.edit().putString(K_HISTORY, encodeHistory(next)).apply()
    }

    fun clearHistory() {
        _history.value = emptyList()
        prefs.edit().remove(K_HISTORY).apply()
    }

    // ---------- Lógica pura (probada en tests) ----------

    const val MAX_HISTORY = 100

    /** Quita [id] si estaba; si no, lo agrega al inicio. */
    fun toggled(list: List<Int>, id: Int): List<Int> = if (id in list) list - id else listOf(id) + list

    /** Pone [entry] al inicio sin repetir el contenido y respeta el máximo. */
    fun withEntry(list: List<HistoryEntry>, entry: HistoryEntry): List<HistoryEntry> =
        (listOf(entry) + list.filter { it.modId != entry.modId }).take(MAX_HISTORY)

    fun encodeIds(ids: List<Int>) = ids.joinToString(",")

    fun decodeIds(raw: String?): List<Int> =
        raw.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.distinct()

    fun encodeHistory(list: List<HistoryEntry>) = list.joinToString(",") { "${it.modId}:${it.installedAt}" }

    fun decodeHistory(raw: String?): List<HistoryEntry> = raw.orEmpty().split(',').mapNotNull { part ->
        val bits = part.split(':')
        val id = bits.getOrNull(0)?.trim()?.toIntOrNull()
        val at = bits.getOrNull(1)?.trim()?.toLongOrNull()
        if (id != null && at != null) HistoryEntry(id, at) else null
    }

    private const val K_FAVORITES = "favorites"
    private const val K_HISTORY = "history"
}
