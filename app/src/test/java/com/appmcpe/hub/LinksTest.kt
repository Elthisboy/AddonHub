package com.appmcpe.hub

import com.appmcpe.hub.catalog.CurseForgeApi
import com.appmcpe.hub.catalog.ExternalLinks
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksTest {
    @Test
    fun onlyHttpsCurseForgeAddressesAreOpenable() {
        assertTrue(ExternalLinks.isCurseForgeUrl("https://www.curseforge.com/minecraft-bedrock/addons/x"))
        assertTrue(ExternalLinks.isCurseForgeUrl("https://curseforge.com/minecraft-bedrock/addons/x"))
        assertFalse(ExternalLinks.isCurseForgeUrl("http://www.curseforge.com/x"))
        assertFalse(ExternalLinks.isCurseForgeUrl("https://curseforge.com.evil.com/x"))
        assertFalse(ExternalLinks.isCurseForgeUrl("https://evilcurseforge.com/x"))
        assertFalse(ExternalLinks.isCurseForgeUrl("https://user:pw@www.curseforge.com/x"))
        assertFalse(ExternalLinks.isCurseForgeUrl("intent://www.curseforge.com/#Intent;end"))
        assertFalse(ExternalLinks.isCurseForgeUrl("javascript:alert(1)"))
        assertFalse(ExternalLinks.isCurseForgeUrl(""))
    }

    private fun modJson(website: String) = JSONObject(
        """{"id":1,"name":"x","allowModDistribution":true,"links":{"websiteUrl":"$website"},
           "latestFiles":[{"id":1,"fileName":"a.mcaddon","downloadUrl":"https://x/a.mcaddon","fileDate":"2026"}]}""",
    )

    @Test
    fun parsedModKeepsItsCurseForgePage() {
        val mod = CurseForgeApi.parseMod(modJson("https://www.curseforge.com/minecraft-bedrock/addons/x"))!!
        assertEquals("https://www.curseforge.com/minecraft-bedrock/addons/x", mod.websiteUrl)
    }

    @Test
    fun aSuspiciousLinkIsDroppedAtParseTime() {
        assertEquals("", CurseForgeApi.parseMod(modJson("https://evil.example/phish"))!!.websiteUrl)
    }
}
