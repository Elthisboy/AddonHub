package com.appmcpe.hub.catalog

import androidx.annotation.StringRes
import com.appmcpe.hub.R
import java.text.Normalizer

/**
 * Ordena resultados de búsqueda en tres niveles de importancia:
 * 1) el nombre del contenido, 2) el creador, 3) palabras clave (resumen/descripción).
 */
object SearchRanker {
    enum class Tier(@StringRes val labelRes: Int) {
        CONTENT(R.string.tier_content),
        CREATOR(R.string.tier_creator),
        KEYWORD(R.string.tier_keyword),
    }

    /** Minúsculas, sin tildes ni emojis ni signos: "🍲Large  Meals!" → "large meals". */
    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    fun tierOf(mod: CfMod, query: String): Tier {
        val q = normalize(query)
        if (q.isEmpty()) return Tier.CONTENT
        val name = normalize(mod.name)
        val tokens = q.split(' ')
        if (name.contains(q) || tokens.all { name.contains(it) }) return Tier.CONTENT
        val compactQuery = q.replace(" ", "")
        val byCreator = mod.authors.any { author ->
            val a = normalize(author).replace(" ", "")
            a.isNotEmpty() && (a.contains(compactQuery) || compactQuery.contains(a))
        }
        return if (byCreator) Tier.CREATOR else Tier.KEYWORD
    }

    /** Qué tan bien coincide dentro de su nivel (0 = mejor). */
    private fun closeness(mod: CfMod, query: String, tier: Tier): Int {
        val q = normalize(query)
        return when (tier) {
            Tier.CONTENT -> {
                val name = normalize(mod.name)
                when {
                    name == q -> 0
                    name.startsWith(q) -> 1
                    " $q" in " $name" -> 2
                    name.contains(q) -> 3
                    else -> 4 // contiene todas las palabras pero separadas
                }
            }
            Tier.CREATOR -> if (mod.authors.any { normalize(it).replace(" ", "") == q.replace(" ", "") }) 0 else 1
            Tier.KEYWORD -> 0
        }
    }

    /** Devuelve [mods] ordenados por nivel, luego cercanía, luego descargas. */
    fun rank(mods: List<CfMod>, query: String): List<CfMod> {
        if (normalize(query).isEmpty()) return mods
        return mods.sortedWith(
            compareBy<CfMod> { tierOf(it, query).ordinal }
                .thenBy { closeness(it, query, tierOf(it, query)) }
                .thenByDescending { it.downloads },
        )
    }
}
