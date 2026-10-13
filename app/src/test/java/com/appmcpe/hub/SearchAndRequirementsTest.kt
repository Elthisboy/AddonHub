package com.appmcpe.hub

import com.appmcpe.hub.catalog.CfDependency
import com.appmcpe.hub.catalog.CfMod
import com.appmcpe.hub.catalog.CurseForgeApi
import com.appmcpe.hub.catalog.Relation
import com.appmcpe.hub.catalog.Requirements
import com.appmcpe.hub.catalog.SearchRanker
import com.appmcpe.hub.catalog.SearchRanker.Tier
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchAndRequirementsTest {
    private fun mod(id: Int, name: String, vararg authors: String, downloads: Long = 0) = CfMod(
        id = id, name = name, summary = "", author = authors.firstOrNull().orEmpty(), logoUrl = null,
        downloads = downloads, fileName = "x.mcaddon", downloadUrl = "https://x", sizeBytes = 1,
        authors = authors.toList(),
    )

    @Test
    fun normalizeStripsEmojiAccentsAndPunctuation() {
        assertEquals("large meals bedrock", SearchRanker.normalize("🍲Large  Meals, Bedrock!🍲"))
        assertEquals("camion", SearchRanker.normalize("Camión"))
    }

    @Test
    fun tiersAreContentThenCreatorThenKeyword() {
        val byName = mod(1, "🍲Large Meals Bedrock", "MaxisShellgryphon")
        val byCreator = mod(2, "Farmer's Pizzeria", "Elaikes")
        val other = mod(3, "Sunflower Delight", "Someone")
        assertEquals(Tier.CONTENT, SearchRanker.tierOf(byName, "large meals"))
        assertEquals(Tier.CREATOR, SearchRanker.tierOf(byCreator, "elaikes"))
        assertEquals(Tier.KEYWORD, SearchRanker.tierOf(other, "farmer"))
        // todas las palabras en el nombre aunque estén separadas
        assertEquals(Tier.CONTENT, SearchRanker.tierOf(byName, "meals large"))
    }

    @Test
    fun rankPutsNameMatchesBeforeCreatorBeforeKeywords_andExactBeforePartial() {
        val keyword = mod(1, "Zombie Pack", "Someone", downloads = 9_000_000)
        val creator = mod(2, "Cool Stuff", "Dragon", downloads = 5_000_000)
        val partial = mod(3, "Dragon Evolution Add-on", "A", downloads = 100)
        val exact = mod(4, "Dragon", "B", downloads = 1)
        val ranked = SearchRanker.rank(listOf(keyword, creator, partial, exact), "dragon").map { it.id }
        assertEquals(listOf(4, 3, 2, 1), ranked)
    }

    @Test
    fun emptyQueryKeepsOrder() {
        val mods = listOf(mod(1, "B"), mod(2, "A"))
        assertEquals(mods, SearchRanker.rank(mods, "  "))
    }

    @Test
    fun requirementsFindsExperimentsAndSplitSentences() {
        val text = """
            Sunflower Delight is an addon for the popular food mod
            For the addon to work, you need to download
            Farmer's Delight
            To create a port on Bedrock, I needed permission. Enable Holiday Creator Features in the world.
        """.trimIndent()
        val found = Requirements.extract(text)
        assertTrue(found.any { it.contains("Holiday Creator Features") })
        assertTrue(found.any { it.contains("you need to download Farmer's Delight") })
    }

    @Test
    fun requirementsIsEmptyWhenNothingIsAsked() {
        assertTrue(Requirements.extract("Un addon divertido con dragones.\nDisfrútalo.").isEmpty())
    }

    @Test
    fun parseModKeepsDeclaredDependenciesAndAllAuthors() {
        // Misma forma que devuelve CurseForge para un addon con una dependencia requerida.
        val json = JSONObject(
            """
            {"id":1504044,"name":"Large Meals Bedrock","summary":"s","downloadCount":10,
             "allowModDistribution":true,
             "authors":[{"id":1,"name":"Elaikes"},{"id":2,"name":"Otro"}],
             "logo":{"thumbnailUrl":"https://x/l.png"},
             "categories":[{"id":4986}],
             "latestFiles":[{"id":1,"fileName":"LM.mcaddon","downloadUrl":"https://x/LM.mcaddon",
               "fileLength":123,"fileDate":"2026-05-15T13:01:18.623Z",
               "dependencies":[{"modId":1236727,"relationType":3},{"modId":55,"relationType":2}]}]}
            """.trimIndent(),
        )
        val mod = CurseForgeApi.parseMod(json)
        assertNotNull(mod)
        assertEquals(listOf("Elaikes", "Otro"), mod!!.authors)
        assertEquals(
            listOf(CfDependency(1236727, Relation.REQUIRED), CfDependency(55, Relation.OPTIONAL)),
            mod.dependencies,
        )
    }

    @Test
    fun parseModSkipsContentThatCannotBeDownloaded() {
        val noDistribution = JSONObject(
            """{"id":1,"name":"x","allowModDistribution":false,"latestFiles":[]}""",
        )
        val noDirectUrl = JSONObject(
            """{"id":2,"name":"x","allowModDistribution":true,
               "latestFiles":[{"id":1,"fileName":"a.mcaddon","downloadUrl":null,"fileDate":"2026"}]}""",
        )
        val wrongFormat = JSONObject(
            """{"id":3,"name":"x","allowModDistribution":true,
               "latestFiles":[{"id":1,"fileName":"a.zip","downloadUrl":"https://x/a.zip","fileDate":"2026"}]}""",
        )
        assertEquals(null, CurseForgeApi.parseMod(noDistribution))
        assertEquals(null, CurseForgeApi.parseMod(noDirectUrl))
        assertEquals(null, CurseForgeApi.parseMod(wrongFormat))
    }
}
