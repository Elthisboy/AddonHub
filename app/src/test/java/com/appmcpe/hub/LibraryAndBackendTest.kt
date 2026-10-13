package com.appmcpe.hub

import com.appmcpe.hub.download.Backend
import com.appmcpe.hub.user.HistoryEntry
import com.appmcpe.hub.user.UserLists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryAndBackendTest {
    // ---------- Favoritos e historial (solo ids) ----------

    @Test
    fun favoriteToggleAddsAtFrontAndRemoves() {
        assertEquals(listOf(3, 1, 2), UserLists.toggled(listOf(1, 2), 3))
        assertEquals(listOf(1, 2), UserLists.toggled(listOf(3, 1, 2), 3))
    }

    @Test
    fun idsRoundTripAndIgnoreGarbage() {
        assertEquals(listOf(5, 7, 9), UserLists.decodeIds(UserLists.encodeIds(listOf(5, 7, 9))))
        assertEquals(listOf(1, 2), UserLists.decodeIds("1,x,,2, 1"))
        assertEquals(emptyList<Int>(), UserLists.decodeIds(null))
    }

    @Test
    fun historyKeepsNewestFirstWithoutRepeatsAndIsCapped() {
        var h = emptyList<HistoryEntry>()
        h = UserLists.withEntry(h, HistoryEntry(1, 100))
        h = UserLists.withEntry(h, HistoryEntry(2, 200))
        h = UserLists.withEntry(h, HistoryEntry(1, 300)) // reinstalado: sube y no se repite
        assertEquals(listOf(1, 2), h.map { it.modId })
        assertEquals(300L, h.first().installedAt)

        repeat(UserLists.MAX_HISTORY + 20) { h = UserLists.withEntry(h, HistoryEntry(1000 + it, it.toLong())) }
        assertEquals(UserLists.MAX_HISTORY, h.size)
    }

    @Test
    fun historyRoundTripAndIgnoreGarbage() {
        val list = listOf(HistoryEntry(4, 1111), HistoryEntry(8, 2222))
        assertEquals(list, UserLists.decodeHistory(UserLists.encodeHistory(list)))
        assertEquals(listOf(HistoryEntry(1, 5)), UserLists.decodeHistory("1:5,bad,2:,:3"))
    }

    // ---------- Backend / proxy ----------

    @Test
    fun apiBaseGoesDirectWithoutProxyAndThroughItWhenConfigured() {
        assertEquals("https://api.curseforge.com/v1", Backend.apiBase(""))
        assertEquals("https://p.example.workers.dev/v1", Backend.apiBase("https://p.example.workers.dev"))
        assertEquals("https://p.example.workers.dev/v1", Backend.apiBase("https://p.example.workers.dev/"))
    }

    @Test
    fun proxyDownloadUrlEncodesTheCdnAddress() {
        val url = Backend.proxyDownloadUrl(
            "https://p.example.workers.dev/",
            "https://edge.forgecdn.net/files/1/2/Mi addon+v2.mcaddon",
        )
        assertTrue(url.startsWith("https://p.example.workers.dev/dl?u="))
        assertFalse(url.substringAfter("?u=").contains("://"))
        assertFalse(url.contains(' '))
    }

    @Test
    fun onlyCurseForgeCdnHostsAreTreatedAsCdn() {
        assertTrue(Backend.isCdnHost("edge.forgecdn.net"))
        assertTrue(Backend.isCdnHost("mediafilez.forgecdn.net"))
        assertFalse(Backend.isCdnHost("example.com"))
        assertFalse(Backend.isCdnHost("forgecdn.net.evil.com"))
        assertFalse(Backend.isCdnHost("notforgecdn.net"))
    }
}
