package com.appmcpe.hub.parental

import androidx.annotation.StringRes
import com.appmcpe.hub.R

/** Perfiles por edad. Cada uno bloquea más o menos tipos de contenido (ver [ContentTerms]). */
enum class AgeGroup(@StringRes val labelRes: Int, val emoji: String, @StringRes val descriptionRes: Int) {
    KIDS(R.string.age_kids, "🧒", R.string.age_kids_desc),
    PRE(R.string.age_pre, "🧑", R.string.age_pre_desc),
    TEEN(R.string.age_teen, "😎", R.string.age_teen_desc),

    /** +15 elegido por un adulto: sin filtro de edad, pero lo sexual sigue bloqueado como en todos. */
    OVER15(R.string.age_over15, "🎮", R.string.age_over15_desc),

    /** Solo se activa desde el modo adulto escondido; no aparece en la lista de edades. */
    ADULT(R.string.age_adult, "🔓", R.string.age_adult_desc);

    /** Sin filtro de edad ni marcas "18+": solo queda el bloqueo permanente de lo sexual. */
    val unfiltered: Boolean get() = this == OVER15 || this == ADULT

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
