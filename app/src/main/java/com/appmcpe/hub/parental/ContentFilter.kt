package com.appmcpe.hub.parental

import com.appmcpe.hub.catalog.CfMod
import com.appmcpe.hub.catalog.SearchRanker

/** Resultado de revisar un contenido. [reason] está pensado para mostrarse al niño. */
data class Verdict(
    val blocked: Boolean,
    val reason: String? = null,
    /** true = lo sexual/explícito: ni un PIN ni el modo adulto lo pueden permitir. */
    val permanent: Boolean = false,
) {
    companion object {
        val OK = Verdict(false)
        fun blocked(reason: String, permanent: Boolean = false) = Verdict(true, reason, permanent)
    }
}

object ContentFilter {
    private const val ADULT = "Contenido para adultos"
    private const val NOT_FOR_AGE = "No es apto para tu edad"
    private const val BLOCKED_BY_ADULT = "Un adulto bloqueó este tema"

    // En descripciones largas una palabra suelta no basta ("sin gore ni sangre"); se exige este número.
    private const val DESCRIPTION_THRESHOLD = 3

    private class Term(val regex: Regex)

    private fun compile(words: Collection<String>): List<Term> = words
        .map { SearchRanker.normalize(it) }
        .filter { it.isNotEmpty() }
        .distinct()
        .map { Term(Regex(" ${Regex.escape(it)}(?:s|es)? ")) }

    private val always = compile(ContentTerms.ALWAYS)
    private val adultOnly = compile(ContentTerms.ADULT_ONLY)
    private val mature = compile(ContentTerms.MATURE)
    private val kidsOnly = compile(ContentTerms.KIDS_ONLY)

    /** Cuántas palabras distintas de [terms] aparecen en [padded] (texto normalizado con espacios a los lados). */
    private fun hits(padded: String, terms: List<Term>): Int = terms.count { it.regex.containsMatchIn(padded) }

    private fun padded(text: String) = " ${SearchRanker.normalize(text)} "

    /** Lo sexual/explícito: bloqueado siempre. */
    private fun hasAlways(padded: String): Boolean = hits(padded, always) > 0

    /** Marcas como "18+" o "adults only": bloquean a todos salvo en modo adulto. */
    private fun hasAdultMarker(raw: String, group: AgeGroup): Boolean {
        if (group == AgeGroup.ADULT) return false
        val lower = raw.lowercase()
        return ContentTerms.ADULT_ONLY_RAW.any { it.containsMatchIn(lower) }
    }

    /** Palabras de madurez que aplican al perfil, sin contar las de los padres. */
    private fun ageTerms(group: AgeGroup): List<Term> = when (group) {
        AgeGroup.KIDS -> mature + kidsOnly + adultOnly
        AgeGroup.PRE -> mature + adultOnly
        AgeGroup.TEEN -> adultOnly
        AgeGroup.ADULT -> emptyList()
    }

    /** ¿Está bloqueada una subcategoría entera (p. ej. Horror) para este perfil? */
    fun isCategoryBlocked(categoryId: Int, slug: String?, s: ParentalSettings): Boolean =
        (s.effectiveGroup == AgeGroup.KIDS || s.effectiveGroup == AgeGroup.PRE) &&
            (categoryId in ContentTerms.MATURE_CATEGORY_IDS || slug in ContentTerms.MATURE_CATEGORY_SLUGS)

    /** ¿Se puede buscar este texto? Evita que el buscador sirva para llegar a lo bloqueado. */
    fun isQueryBlocked(query: String, s: ParentalSettings): Boolean {
        if (query.isBlank()) return false
        val p = padded(query)
        return hasAlways(p) ||
            hasAdultMarker(query, s.effectiveGroup) ||
            hits(p, ageTerms(s.effectiveGroup)) > 0 ||
            hits(p, compile(s.customWords)) > 0
    }

    /** Revisa nombre, resumen y subcategorías (lo que se ve en el catálogo). */
    fun check(mod: CfMod, s: ParentalSettings): Verdict {
        val group = s.effectiveGroup
        val text = "${mod.name} ${mod.summary}"
        val p = padded(text)
        // Lo sexual nunca se puede permitir, ni a mano ni en modo adulto.
        if (hasAlways(p)) return Verdict.blocked(ADULT, permanent = true)
        if (mod.id in s.allowed) return Verdict.OK
        if (hasAdultMarker(text, group)) return Verdict.blocked(NOT_FOR_AGE)
        if (mod.categoryIds.any { isCategoryBlocked(it, null, s) }) return Verdict.blocked(NOT_FOR_AGE)
        if (hits(p, ageTerms(group)) > 0) return Verdict.blocked(NOT_FOR_AGE)
        if (hits(p, compile(s.customWords)) > 0) return Verdict.blocked(BLOCKED_BY_ADULT)
        return Verdict.OK
    }

    /** Revisa la descripción completa (más texto, así que se pide más de una coincidencia). */
    fun checkDescription(description: String, mod: CfMod, s: ParentalSettings): Verdict {
        val group = s.effectiveGroup
        val p = padded(description)
        if (hasAlways(p)) return Verdict.blocked(ADULT, permanent = true)
        if (mod.id in s.allowed) return Verdict.OK
        if (hasAdultMarker(description, group)) return Verdict.blocked(NOT_FOR_AGE)
        if (hits(p, ageTerms(group)) >= DESCRIPTION_THRESHOLD) return Verdict.blocked(NOT_FOR_AGE)
        if (hits(p, compile(s.customWords)) > 0) return Verdict.blocked(BLOCKED_BY_ADULT)
        return Verdict.OK
    }

    fun filter(mods: List<CfMod>, s: ParentalSettings): List<CfMod> = mods.filter { !check(it, s).blocked }
}
