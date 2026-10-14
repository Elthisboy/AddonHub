package com.appmcpe.hub.text

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.appmcpe.hub.R
import java.io.IOException

/**
 * Un texto para mostrar al usuario que se traduce al último momento (en la pantalla), no donde se crea.
 * Así la lógica (filtro, errores de red, avisos) puede producir mensajes sin conocer el idioma del teléfono.
 *
 * - [Res]: un texto de strings.xml; los argumentos pueden ser otros [UiText].
 * - [Plain]: texto que viene de fuera y no se traduce (nombres de contenidos, frases del autor).
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plain(val value: String) : UiText

    fun asString(context: Context): String = when (this) {
        is Plain -> value
        is Res -> context.getString(id, *args.map { if (it is UiText) it.asString(context) else it }.toTypedArray())
    }

    @Composable
    fun asString(): String = asString(LocalContext.current)

    companion object {
        fun of(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())
        fun plain(value: String): UiText = Plain(value)
    }
}

/** Cualquier error que ya sabe cómo explicarse al usuario, en su idioma. */
interface UiTextProvider {
    val text: UiText
}

/** Un [IOException] con mensaje traducible. El texto de la excepción queda en inglés para los registros. */
open class UiIOException(override val text: UiText, cause: Throwable? = null) :
    IOException(text.toString(), cause), UiTextProvider

/** Convierte cualquier error en algo que se pueda enseñar: lo conocido se traduce; lo demás se muestra genérico. */
fun Throwable.toUiText(): UiText = when (this) {
    is UiTextProvider -> text
    else -> UiText.of(R.string.error_generic, message ?: javaClass.simpleName)
}
