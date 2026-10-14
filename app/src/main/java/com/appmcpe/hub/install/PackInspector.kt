package com.appmcpe.hub.install

import com.appmcpe.hub.R
import com.appmcpe.hub.text.UiText
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Lee los `manifest.json` dentro de los archivos descargados para avisar de cosas que
 * CurseForge no declara: packs necesarios que faltan, APIs beta y versión mínima de Minecraft.
 */
object PackInspector {
    private const val MAX_MANIFEST_BYTES = 256 * 1024
    private const val MAX_NESTED_ARCHIVE_BYTES = 64 * 1024 * 1024
    private const val MAX_ENTRIES = 5000

    private data class Manifest(
        val uuid: String?,
        val dependencyUuids: List<String>,
        val betaModules: List<String>,
        val minEngine: List<Int>?,
    )

    /**
     * @param installedVersion versión de Minecraft instalada (p. ej. [1, 21, 50]) o null si no se conoce.
     * @return avisos traducibles, sin repetir; vacío si no hay nada que advertir.
     */
    fun inspect(files: List<File>, installedVersion: List<Int>?): List<UiText> {
        val manifests = files.flatMap { runCatching { readManifests(it) }.getOrDefault(emptyList()) }
        if (manifests.isEmpty()) return emptyList()

        val notes = LinkedHashSet<UiText>()

        val provided = manifests.mapNotNull { it.uuid }.toSet()
        val missing = manifests.flatMap { it.dependencyUuids }.filter { it !in provided }.toSet()
        if (missing.isNotEmpty()) {
            notes += UiText.of(R.string.note_missing_packs, missing.size)
        }

        if (manifests.any { it.betaModules.isNotEmpty() }) {
            notes += UiText.of(R.string.note_beta_apis)
        }

        val needed = manifests.mapNotNull { it.minEngine }.maxWithOrNull(::compareVersions)
        if (needed != null && installedVersion != null && compareVersions(needed, installedVersion) > 0) {
            notes += UiText.of(R.string.note_newer_minecraft, needed.joinToString("."), installedVersion.joinToString("."))
        }
        return notes.toList()
    }

    fun compareVersions(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val d = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    private fun readManifests(file: File): List<Manifest> =
        file.inputStream().buffered().use { scan(it, depth = 0) }

    /** Recorre un zip buscando manifest.json, también dentro de .mcpack/.zip anidados (un .mcaddon). */
    private fun scan(input: InputStream, depth: Int): List<Manifest> {
        val out = ArrayList<Manifest>()
        ZipInputStream(input).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            var count = 0
            while (entry != null && count++ < MAX_ENTRIES) {
                val name = entry.name.substringAfterLast('/').lowercase()
                val ext = name.substringAfterLast('.', "")
                when {
                    entry.isDirectory -> {}
                    name == "manifest.json" -> readLimited(zip, MAX_MANIFEST_BYTES)?.let { bytes ->
                        parse(String(bytes, Charsets.UTF_8))?.let(out::add)
                    }
                    depth < 1 && ext in ALLOWED_EXTENSIONS + "zip" ->
                        readLimited(zip, MAX_NESTED_ARCHIVE_BYTES)?.let { bytes ->
                            out += runCatching { scan(ByteArrayInputStream(bytes), depth + 1) }
                                .getOrDefault(emptyList())
                        }
                }
                entry = zip.nextEntry
            }
        }
        return out
    }

    /** Lee la entrada actual; devuelve null si supera [limit] bytes (para no llenar la memoria). */
    private fun readLimited(zip: ZipInputStream, limit: Int): ByteArray? {
        val buf = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        while (true) {
            val n = zip.read(chunk)
            if (n < 0) break
            if (buf.size() + n > limit) return null
            buf.write(chunk, 0, n)
        }
        return buf.toByteArray()
    }

    private fun parse(text: String): Manifest? = try {
        val root = JSONObject(text.trimStart('﻿'))
        val deps = root.optJSONArray("dependencies")
        val uuids = ArrayList<String>()
        val beta = ArrayList<String>()
        for (i in 0 until (deps?.length() ?: 0)) {
            val d = deps!!.optJSONObject(i) ?: continue
            d.optString("uuid").takeIf { it.isNotBlank() }?.let { uuids += it.lowercase() }
            val module = d.optString("module_name")
            if (module.startsWith("@minecraft/") && d.optString("version").contains("beta", ignoreCase = true)) {
                beta += module
            }
        }
        val min = root.optJSONObject("header")?.optJSONArray("min_engine_version")
            ?.let { a -> List(a.length()) { a.optInt(it) } }
        Manifest(
            uuid = root.optJSONObject("header")?.optString("uuid")?.takeIf { it.isNotBlank() }?.lowercase(),
            dependencyUuids = uuids,
            betaModules = beta,
            minEngine = min,
        )
    } catch (_: Exception) {
        null // manifest ilegible: simplemente no hay avisos
    }
}
