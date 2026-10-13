package com.appmcpe.hub.install

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import java.io.File

/** Formatos que Minecraft Bedrock sabe importar. */
val ALLOWED_EXTENSIONS = setOf("mcpack", "mcaddon", "mcworld", "mctemplate")

object McInstaller {
    const val MC_PACKAGE = "com.mojang.minecraftpe"

    fun isMinecraftInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(MC_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** Versión de Minecraft instalada como lista de números (p. ej. [1, 21, 50]), o null. */
    fun installedVersion(context: Context): List<Int>? = try {
        context.packageManager.getPackageInfo(MC_PACKAGE, 0).versionName
            ?.split('.')?.mapNotNull { it.toIntOrNull() }?.take(3)?.takeIf { it.isNotEmpty() }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    /** Carpeta (dentro del cache) donde se guardan los archivos listos para importar. */
    fun importsDir(context: Context): File =
        File(context.cacheDir, "imports").apply { mkdirs() }

    /** Borra descargas anteriores para que el cache no crezca sin límite. */
    fun clearImports(context: Context) {
        importsDir(context).listFiles()?.forEach { it.delete() }
    }

    /**
     * Entrega el archivo a Minecraft para que lo importe.
     * Prueba varios tipos MIME porque Minecraft solo responde a algunos de ellos.
     */
    fun install(context: Context, file: File): Result<Unit> {
        if (!isMinecraftInstalled(context)) {
            return Result.failure(IllegalStateException("Minecraft no está instalado"))
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val candidates = listOf("application/octet-stream", null, "*/*").map { mime ->
            Intent(Intent.ACTION_VIEW).apply {
                if (mime != null) setDataAndType(uri, mime) else data = uri
                setPackage(MC_PACKAGE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        val intent = candidates.firstOrNull {
            it.resolveActivity(context.packageManager) != null
        } ?: candidates.first()

        return try {
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: ActivityNotFoundException) {
            Result.failure(e)
        }
    }
}
