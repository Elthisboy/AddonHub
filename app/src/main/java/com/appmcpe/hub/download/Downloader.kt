package com.appmcpe.hub.download

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.appmcpe.hub.install.ALLOWED_EXTENSIONS
import com.appmcpe.hub.install.McInstaller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URLDecoder

object Downloader {
    private fun safeName(raw: String): String =
        raw.substringAfterLast('/').substringBefore('?')
            .let { URLDecoder.decode(it, "UTF-8") }
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .ifBlank { "download" }

    /** El archivo no es de un tipo que Minecraft importe; reintentar no lo arregla. */
    private class UnsupportedFormat(ext: String) :
        IOException("Formato no soportado (.$ext). Usa: ${ALLOWED_EXTENSIONS.joinToString()}")

    private fun requireAllowed(name: String) {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext !in ALLOWED_EXTENSIONS) throw UnsupportedFormat(ext)
    }

    /** Descarga una URL a la carpeta de imports. [onProgress] recibe (bytes bajados, total o -1). */
    suspend fun download(
        context: Context,
        url: String,
        fileName: String? = null,
        onProgress: (Long, Long) -> Unit,
    ): File = downloadTo(McInstaller.importsDir(context), url, fileName, onProgress = onProgress)

    /** Fallo HTTP; solo se reintenta si el error puede ser pasajero (servidor caído, demasiadas peticiones). */
    private class HttpFailure(val code: Int) : IOException("Error del servidor: $code") {
        val retryable: Boolean get() = code >= 500 || code == 408 || code == 429
    }

    /** El servidor no admite continuar una descarga (rechazó el Range): hay que empezar de cero. */
    private class ResumeRejected : IOException("El servidor no admite continuar la descarga")

    private const val MAX_ATTEMPTS = 4

    /**
     * Descarga a [dir] con reintentos: si la conexión se corta (wifi inestable) continúa desde donde iba
     * usando `Range`, en vez de empezar de cero. Lo bajado se guarda en `<nombre>.part` y solo se renombra
     * al terminar, así Minecraft nunca recibe un archivo a medias.
     *
     * Reanudar solo se intenta cuando se conoce [fileName] de antemano (descargas del catálogo).
     */
    internal suspend fun downloadTo(
        dir: File,
        url: String,
        fileName: String? = null,
        retryDelayMs: Long = 1_500,
        onProgress: (Long, Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        require(url.startsWith("https://") || url.startsWith("http://")) { "URL inválida" }
        dir.mkdirs()
        val fixedName = fileName?.let(::safeName)?.also(::requireAllowed)
        val knownPart = fixedName?.let { File(dir, "$it.part") }

        var failures = 0
        var restarted = false
        while (true) {
            currentCoroutineContext().ensureActive()
            val resumeFrom = knownPart?.takeIf { it.exists() }?.length() ?: 0L
            try {
                return@withContext attempt(dir, url, fixedName, resumeFrom, onProgress)
            } catch (e: CancellationException) {
                knownPart?.delete() // cancelado a propósito: no dejar restos
                throw e
            } catch (e: ResumeRejected) {
                knownPart?.delete()
                if (restarted) throw e
                restarted = true // se vuelve a intentar una vez desde cero, sin Range
            } catch (e: IOException) {
                failures++
                // Un 404, un formato no permitido, etc. no se arreglan reintentando.
                val fatal = e is UnsupportedFormat || (e is HttpFailure && !e.retryable)
                if (fatal || failures >= MAX_ATTEMPTS) {
                    knownPart?.delete()
                    throw e
                }
                delay(retryDelayMs * failures)
            }
        }
        @Suppress("UNREACHABLE_CODE")
        error("inalcanzable")
    }

    /** Un intento de descarga. Si [resumeFrom] > 0 pide solo lo que falta. */
    private suspend fun attempt(
        dir: File,
        url: String,
        fixedName: String?,
        resumeFrom: Long,
        onProgress: (Long, Long) -> Unit,
    ): File {
        // Con proxy, el CDN de CurseForge se pide por nuestro servidor (la key no está en la app).
        val builder = Backend.download(url)
        if (resumeFrom > 0) builder.header("Range", "bytes=$resumeFrom-")

        return Http.open(builder.build()).use { response ->
            if (!response.isSuccessful) {
                if (resumeFrom > 0 && (response.code == 404 || response.code == 416)) throw ResumeRejected()
                throw HttpFailure(response.code)
            }
            // El nombre final (tras redirecciones) suele traer la extensión correcta.
            val name = fixedName ?: safeName(response.request.url.encodedPath).also(::requireAllowed)
            val out = File(dir, name)
            val part = File(dir, "$name.part")
            val body = response.body ?: throw IOException("Respuesta vacía")

            // 206 = el servidor sigue desde donde pedimos; 200 = ignoró el Range y manda todo otra vez.
            val resuming = resumeFrom > 0 && response.code == 206
            val offset = if (resuming) resumeFrom else 0L
            val total = body.contentLength().let { if (it >= 0) it + offset else -1L }

            var done = offset
            var finished = false
            try {
                body.byteStream().use { input ->
                    java.io.FileOutputStream(part, resuming).use { output ->
                        val buf = ByteArray(32 * 1024)
                        var lastReport = 0L
                        onProgress(done, total)
                        while (true) {
                            currentCoroutineContext().ensureActive() // permite cancelar
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            val now = System.nanoTime()
                            if (now - lastReport > 100_000_000L) { // ~10 actualizaciones por segundo
                                lastReport = now
                                onProgress(done, total)
                            }
                        }
                    }
                }
                // Si el servidor cortó antes de tiempo, el siguiente intento continúa desde aquí.
                if (total > 0 && done != total) throw IOException("Descarga incompleta ($done de $total bytes)")
                onProgress(done, if (total > 0) total else done)
                out.delete()
                if (!part.renameTo(out)) throw IOException("No se pudo guardar el archivo")
                finished = true
                out
            } finally {
                // Sin nombre conocido no se puede reanudar: no dejar restos. Con nombre, el resto lo
                // limpia (o reutiliza) el bucle de reintentos.
                if (!finished && fixedName == null) part.delete()
            }
        }
    }

    /** Copia un archivo elegido con el selector del sistema. */
    suspend fun copyFromUri(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val name = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) c.getString(i) else null
        }?.let(::safeName) ?: "archivo.mcpack"
        requireAllowed(name)
        val out = File(McInstaller.importsDir(context), name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { input.copyTo(it) }
        } ?: throw IOException("No se pudo abrir el archivo")
        out
    }

    /** Copia el pack de prueba incluido en la app. */
    suspend fun copyTestPack(context: Context): File = withContext(Dispatchers.IO) {
        val out = File(McInstaller.importsDir(context), "test_pack.mcpack")
        context.assets.open("test_pack.mcpack").use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        out
    }
}
