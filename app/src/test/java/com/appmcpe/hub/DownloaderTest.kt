package com.appmcpe.hub

import com.appmcpe.hub.download.Downloader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Pruebas de descarga con un servidor local que simula cortes, Range y errores. */
class DownloaderTest {
    private lateinit var server: MockWebServer
    private lateinit var dir: File
    private val content = ByteArray(300_000) { (it * 31 + 7).toByte() }
    private val seen = java.util.concurrent.CopyOnWriteArrayList<RecordedRequest>()

    private fun base(path: String) = server.url(path).toString()

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("dl-test").toFile()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        dir.deleteRecursively()
    }

    /** Define cómo responde el servidor; [n] cuenta las peticiones (1 = la primera). */
    private fun serve(handler: (RecordedRequest, Int) -> MockResponse) {
        var n = 0
        server.dispatcher = object : Dispatcher() {
            @Synchronized
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += request
                return handler(request, ++n)
            }
        }
    }

    private fun full() = MockResponse().setResponseCode(200).setBody(Buffer().write(content))

    /** Envía la mitad del cuerpo y corta la conexión: el cliente ve una descarga incompleta. */
    private fun cutInHalf() = full().setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)

    private fun partial(from: Int) = MockResponse()
        .setResponseCode(206)
        .addHeader("Content-Range", "bytes $from-${content.size - 1}/${content.size}")
        .setBody(Buffer().write(content, from, content.size - from))

    private fun rangeStart(r: RecordedRequest): Int? =
        r.getHeader("Range")?.removePrefix("bytes=")?.substringBefore('-')?.toIntOrNull()

    private fun download(path: String, name: String? = "archivo.mcaddon", progress: (Long, Long) -> Unit = { _, _ -> }) =
        runBlocking { Downloader.downloadTo(dir, base(path), name, retryDelayMs = 1, onProgress = progress) }

    @Test
    fun plainDownloadSavesExactBytesAndLeavesNoPartFile() {
        serve { _, _ -> full() }
        val progress = mutableListOf<Pair<Long, Long>>()
        val file = download("/ok") { d, t -> progress += d to t }
        assertArrayEquals(content, file.readBytes())
        assertEquals(listOf("archivo.mcaddon"), dir.list()!!.toList())
        assertEquals(content.size.toLong() to content.size.toLong(), progress.last())
        assertTrue(progress.map { it.first }.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun connectionCutMidwayResumesWithRangeAndFinishesCorrectly() {
        serve { r, n -> if (n == 1) cutInHalf() else partial(rangeStart(r)!!) }
        val file = download("/cut")
        assertArrayEquals(content, file.readBytes())
        assertEquals(2, seen.size)
        assertNull(seen[0].getHeader("Range"))
        val from = rangeStart(seen[1])!!
        assertTrue("debía continuar a mitad, no de cero (Range desde $from)", from in 1 until content.size)
        assertEquals(listOf("archivo.mcaddon"), dir.list()!!.toList())
    }

    @Test
    fun resumedProgressNeverGoesBackwards() {
        serve { r, n -> if (n == 1) cutInHalf() else partial(rangeStart(r)!!) }
        val progress = mutableListOf<Long>()
        download("/cut") { d, _ -> progress += d }
        assertTrue(progress.toString(), progress.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(content.size.toLong(), progress.last())
    }

    @Test
    fun serverThatIgnoresRangeRestartsFromScratchWithoutCorruption() {
        serve { _, n -> if (n == 1) cutInHalf() else full() /* ignora el Range: 200 completo */ }
        val file = download("/norange")
        assertArrayEquals(content, file.readBytes()) // no quedó duplicado ni mezclado
    }

    @Test
    fun rangeRejectedWith404FallsBackToFreshDownload() {
        serve { r, n ->
            when {
                n == 1 -> cutInHalf()
                r.getHeader("Range") != null -> MockResponse().setResponseCode(404)
                else -> full()
            }
        }
        val file = download("/r404")
        assertArrayEquals(content, file.readBytes())
        assertEquals(3, seen.size)
        assertNull(seen[2].getHeader("Range")) // el último intento fue desde cero
    }

    @Test
    fun notFoundFailsFastWithoutRetries() {
        serve { _, _ -> MockResponse().setResponseCode(404) }
        try {
            download("/missing")
            fail("debía fallar")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("404"))
        }
        assertEquals(1, seen.size)
        assertTrue(dir.list()!!.isEmpty())
    }

    @Test
    fun serverErrorsAreRetriedUntilTheyClear() {
        serve { _, n -> if (n < 3) MockResponse().setResponseCode(503) else full() }
        assertArrayEquals(content, download("/flaky").readBytes())
        assertEquals(3, seen.size)
    }

    @Test
    fun givesUpAfterTheMaximumAttemptsAndCleansUp() {
        serve { _, _ -> MockResponse().setResponseCode(500) }
        try {
            download("/down")
            fail("debía fallar")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("500"))
        }
        assertEquals(4, seen.size)
        assertTrue(dir.list()!!.isEmpty())
    }

    @Test
    fun unsupportedFormatIsRejectedBeforeAnyRequest() {
        serve { _, _ -> full() }
        try {
            download("/x", name = "virus.exe")
            fail("debía fallar")
        } catch (e: IOException) {
            assertTrue(e.message!!.startsWith("Formato no soportado"))
        }
        assertEquals(0, seen.size)
    }

    @Test
    fun nameFromUrlWhenNoneGiven_andFormatCheckedThere() {
        serve { _, _ -> full() }
        assertEquals("mundo.mcworld", download("/files/mundo.mcworld", name = null).name)
        try {
            download("/files/malo.exe", name = null)
            fail("debía fallar")
        } catch (e: IOException) {
            assertTrue(e.message!!.startsWith("Formato no soportado"))
        }
        assertEquals(listOf("mundo.mcworld"), dir.list()!!.toList())
    }

    @Test
    fun cancellingMidDownloadLeavesNoFilesBehind() {
        serve { _, _ -> full().throttleBody(10_000, 40, TimeUnit.MILLISECONDS) }
        var cancelled = false
        runBlocking {
            val job = launch {
                try {
                    Downloader.downloadTo(dir, base("/slow"), "archivo.mcaddon", retryDelayMs = 1) { _, _ -> }
                } catch (e: CancellationException) {
                    cancelled = true
                    throw e
                }
            }
            delay(400)
            job.cancel()
            job.join()
        }
        assertTrue(cancelled)
        assertFalse(File(dir, "archivo.mcaddon").exists())
        assertTrue(dir.list()!!.toList().toString(), dir.list()!!.isEmpty())
    }
}
