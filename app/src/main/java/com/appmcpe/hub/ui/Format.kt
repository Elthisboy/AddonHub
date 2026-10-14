package com.appmcpe.hub.ui

import androidx.annotation.StringRes
import com.appmcpe.hub.R
import java.util.Locale

fun compactCount(n: Long): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
    n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1_000.0)
    else -> n.toString()
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
    bytes > 0 -> String.format(Locale.US, "%d KB", bytes / 1_000)
    else -> ""
}

/** Nombre amigable (traducible) del tipo de contenido según la extensión del archivo. */
@StringRes
fun contentTypeRes(fileName: String): Int = when (fileName.substringAfterLast('.', "").lowercase()) {
    "mcaddon" -> R.string.type_addon
    "mcpack" -> R.string.type_pack
    "mcworld" -> R.string.type_world
    "mctemplate" -> R.string.type_template
    else -> R.string.type_file
}
