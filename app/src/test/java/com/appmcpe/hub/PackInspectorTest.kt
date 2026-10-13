package com.appmcpe.hub

import com.appmcpe.hub.install.PackInspector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PackInspectorTest {
    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { z ->
            for ((name, data) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(data)
                z.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun manifest(uuid: String, deps: String = "[]", min: String = "[1,20,0]") = """
        {"format_version":2,
         "header":{"name":"x","uuid":"$uuid","version":[1,0,0],"min_engine_version":$min},
         "modules":[{"type":"data","uuid":"m-$uuid","version":[1,0,0]}],
         "dependencies":$deps}
    """.trimIndent().toByteArray()

    private fun tempFile(bytes: ByteArray, name: String) =
        File.createTempFile("inspector", name).apply { writeBytes(bytes); deleteOnExit() }

    @Test
    fun bundledDependencyInsideNestedMcpackProducesNoWarning() {
        val bp = zip("manifest.json" to manifest("bp-1", """[{"uuid":"rp-1","version":[1,0,0]}]"""))
        val rp = zip("manifest.json" to manifest("rp-1"))
        val addon = tempFile(zip("a.mcpack" to bp, "b.mcpack" to rp), ".mcaddon")
        assertTrue(PackInspector.inspect(listOf(addon), listOf(1, 21, 50)).isEmpty())
    }

    @Test
    fun missingPackBetaApiAndNewerEngineAreReported() {
        val m = manifest(
            "bp-1",
            """[{"uuid":"other-pack","version":[1,0,0]},{"module_name":"@minecraft/server","version":"1.16.0-beta"}]""",
            min = "[1,21,90]",
        )
        val addon = tempFile(zip("bp/manifest.json" to m), ".mcpack")
        val notes = PackInspector.inspect(listOf(addon), listOf(1, 21, 50))
        assertEquals(3, notes.size)
        assertTrue(notes.any { it.contains("1 pack") })
        assertTrue(notes.any { it.contains("APIs Beta") })
        assertTrue(notes.any { it.contains("1.21.90") && it.contains("1.21.50") })
    }

    @Test
    fun dependencyProvidedByAnotherDownloadedFileIsNotMissing() {
        val lib = tempFile(zip("manifest.json" to manifest("lib-1")), ".mcpack")
        val main = tempFile(zip("manifest.json" to manifest("main-1", """[{"uuid":"lib-1","version":[1,0,0]}]""")), ".mcpack")
        assertTrue(PackInspector.inspect(listOf(lib, main), null).isEmpty())
    }

    @Test
    fun brokenArchiveIsIgnored() {
        val bad = tempFile("no es un zip".toByteArray(), ".mcpack")
        assertTrue(PackInspector.inspect(listOf(bad), null).isEmpty())
    }
}
