package com.appmcpe.hub.ui

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

/** Nombre amigable del tipo de contenido según la extensión del archivo. */
fun contentType(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
    "mcaddon" -> "Addon"
    "mcpack" -> "Pack"
    "mcworld" -> "Mundo"
    "mctemplate" -> "Plantilla"
    else -> "Archivo"
}
