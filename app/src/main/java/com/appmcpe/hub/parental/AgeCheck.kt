package com.appmcpe.hub.parental

import java.time.DateTimeException
import java.time.LocalDate
import java.time.Period

/**
 * Comprobación de mayoría de edad por fecha de nacimiento.
 *
 * Es una declaración del propio usuario (no verifica identidad). La fecha solo se usa para
 * calcular la edad y no se guarda ni se envía a ningún sitio.
 */
object AgeCheck {
    const val ADULT_AGE = 18

    sealed interface Result {
        data object Adult : Result
        data object Minor : Result
        data object InvalidDate : Result
    }

    fun check(year: Int, month: Int, day: Int, today: LocalDate = LocalDate.now()): Result {
        val birth = try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            return Result.InvalidDate
        }
        // Fechas imposibles: en el futuro o de hace más de 120 años.
        if (birth.isAfter(today) || year < today.year - 120) return Result.InvalidDate
        return if (Period.between(birth, today).years >= ADULT_AGE) Result.Adult else Result.Minor
    }
}
