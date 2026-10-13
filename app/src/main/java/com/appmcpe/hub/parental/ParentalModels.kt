package com.appmcpe.hub.parental

/** Perfiles por edad. Cada uno bloquea más o menos tipos de contenido (ver [ContentTerms]). */
enum class AgeGroup(val label: String, val emoji: String, val description: String) {
    KIDS("6 a 9 años", "🧒", "Sin terror, sangre, armas ni violencia."),
    PRE("10 a 12 años", "🧑", "Sin terror, sangre, drogas ni alcohol. Permite armas y aventuras."),
    TEEN("13 a 15 años", "😎", "Permite terror y acción. Sin drogas, alcohol ni apuestas."),

    /** Solo se activa desde el modo adulto escondido; no aparece en la lista de edades. */
    ADULT("Adulto (18+)", "🔓", "Sin filtro de edad. Lo sexual sigue bloqueado siempre.");

    companion object {
        /** Las edades que un adulto puede elegir normalmente. */
        val selectable: List<AgeGroup> = entries.filter { it != ADULT }
    }
}

data class ParentalSettings(
    /** false hasta que un adulto termina la configuración inicial. */
    val onboarded: Boolean = false,
    /** Mientras no se elija otro, se usa el perfil más estricto. */
    val ageGroup: AgeGroup = AgeGroup.KIDS,
    /** Palabras extra bloqueadas por los padres (se aplican a todas las edades). */
    val customWords: Set<String> = emptySet(),
    /** Contenidos que un adulto aprobó a mano: id → nombre. */
    val allowed: Map<Int, String> = emptyMap(),
    /** Muestra la pestaña Herramientas (enlaces y archivos propios), que se salta el filtro. */
    val toolsEnabled: Boolean = false,
    /** Un adulto confirmó su mayoría de edad una vez (no se guarda la fecha de nacimiento). */
    val adultVerified: Boolean = false,
    /** Modo adulto activo ahora. Solo dura mientras la app está abierta: no se guarda. */
    val adultMode: Boolean = false,
) {
    /** El perfil que realmente se aplica: el adulto manda mientras el modo esté activo. */
    val effectiveGroup: AgeGroup get() = if (adultMode) AgeGroup.ADULT else ageGroup
}
