package com.appmcpe.hub

import com.appmcpe.hub.catalog.CfMod
import com.appmcpe.hub.parental.AgeCheck
import com.appmcpe.hub.parental.AgeGroup
import com.appmcpe.hub.parental.ContentFilter
import com.appmcpe.hub.parental.ParentalSettings
import com.appmcpe.hub.parental.PinAttempts
import com.appmcpe.hub.parental.PinHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ParentalTest {
    private fun mod(name: String, summary: String = "", id: Int = 1, cats: List<Int> = emptyList()) = CfMod(
        id = id, name = name, summary = summary, author = "a", logoUrl = null, downloads = 0,
        fileName = "x.mcaddon", downloadUrl = "https://x", sizeBytes = 1, categoryIds = cats,
    )

    private fun settings(
        group: AgeGroup,
        words: Set<String> = emptySet(),
        allowed: Map<Int, String> = emptyMap(),
        adult: Boolean = false,
    ) = ParentalSettings(
        onboarded = true, ageGroup = group, customWords = words, allowed = allowed,
        adultVerified = adult, adultMode = adult,
    )

    private fun blocked(m: CfMod, s: ParentalSettings) = ContentFilter.check(m, s).blocked

    // ---------- niveles por edad ----------

    @Test
    fun horrorIsBlockedForKidsAndPreButAllowedForTeen() {
        val m = mod("Scary Horror Mansion", "A creepy place")
        assertTrue(blocked(m, settings(AgeGroup.KIDS)))
        assertTrue(blocked(m, settings(AgeGroup.PRE)))
        assertFalse(blocked(m, settings(AgeGroup.TEEN)))
    }

    @Test
    fun gunsAreBlockedOnlyForKids() {
        val m = mod("Gun Pack", "Adds pistols and rifles")
        assertTrue(blocked(m, settings(AgeGroup.KIDS)))
        assertFalse(blocked(m, settings(AgeGroup.PRE)))
        assertFalse(blocked(m, settings(AgeGroup.TEEN)))
    }

    @Test
    fun alcoholAndDrugsAreAdultOnly_blockedUnder18() {
        val m = mod("Beer Brewing", "Make cerveza and wine")
        for (g in listOf(AgeGroup.KIDS, AgeGroup.PRE, AgeGroup.TEEN)) assertTrue(g.name, blocked(m, settings(g)))
        assertFalse(blocked(m, settings(AgeGroup.TEEN, adult = true)))
    }

    @Test
    fun sexualContentIsNeverAllowed_notEvenInAdultMode_orByParentApproval() {
        val m = mod("NSFW Skins", id = 7)
        val adult = settings(AgeGroup.TEEN, adult = true)
        assertTrue(blocked(m, adult))
        assertTrue(blocked(m, settings(AgeGroup.TEEN, allowed = mapOf(7 to "x"))))
        assertTrue(ContentFilter.check(m, adult).permanent)
    }

    @Test
    fun adultMarkersLike18PlusBlockMinorsButNotVersionNumbers() {
        assertTrue(blocked(mod("Cool map 18+"), settings(AgeGroup.TEEN)))
        assertTrue(blocked(mod("For adults only"), settings(AgeGroup.TEEN)))
        assertFalse(blocked(mod("Works on 1.18+ and 1.20"), settings(AgeGroup.TEEN)))
        assertFalse(blocked(mod("Cool map 18+"), settings(AgeGroup.TEEN, adult = true)))
    }

    @Test
    fun matchingIsByWholeWord_noFalsePositivesOnSubstrings() {
        // "sex" dentro de "Essex", "kill" dentro de "skill", "gun" dentro de "gunmetal"
        assertFalse(blocked(mod("Essex Castle"), settings(AgeGroup.KIDS)))
        assertFalse(blocked(mod("Skill Tree"), settings(AgeGroup.KIDS)))
        assertFalse(blocked(mod("Gunmetal Armor"), settings(AgeGroup.KIDS)))
    }

    @Test
    fun spanishWordsAndAccentsAndEmojiAreHandled() {
        assertTrue(blocked(mod("🩸 Mundo de TERROR"), settings(AgeGroup.PRE)))
        assertTrue(blocked(mod("Pesadilla"), settings(AgeGroup.PRE)))
        assertTrue(blocked(mod("Sangre"), settings(AgeGroup.PRE)))
        assertFalse(blocked(mod("Granja feliz", "Cultiva y cocina"), settings(AgeGroup.KIDS)))
    }

    @Test
    fun horrorCategoryIsBlockedForYoungerProfilesOnly() {
        val m = mod("Totally Normal Name", cats = listOf(8833))
        assertTrue(blocked(m, settings(AgeGroup.KIDS)))
        assertTrue(blocked(m, settings(AgeGroup.PRE)))
        assertFalse(blocked(m, settings(AgeGroup.TEEN)))
    }

    // ---------- permisos de los padres ----------

    @Test
    fun parentApprovalOverridesAgeFilterForThatModOnly() {
        val m = mod("Gun Pack", id = 5)
        val s = settings(AgeGroup.KIDS, allowed = mapOf(5 to "Gun Pack"))
        assertFalse(blocked(m, s))
        assertTrue(blocked(mod("Gun Pack 2", id = 6), s))
    }

    @Test
    fun customWordsBlockAtEveryAge() {
        val m = mod("Zombie Farm")
        assertFalse(blocked(m, settings(AgeGroup.TEEN)))
        assertTrue(blocked(m, settings(AgeGroup.TEEN, words = setOf("zombie"))))
        assertTrue(blocked(m, settings(AgeGroup.TEEN, words = setOf("zombie"), adult = true)))
    }

    // ---------- buscador y descripciones ----------

    @Test
    fun searchCannotBeUsedToReachBlockedTopics() {
        val kids = settings(AgeGroup.KIDS)
        assertTrue(ContentFilter.isQueryBlocked("horror maps", kids))
        assertTrue(ContentFilter.isQueryBlocked("porn", settings(AgeGroup.TEEN, adult = true)))
        assertFalse(ContentFilter.isQueryBlocked("dragones", kids))
        assertFalse(ContentFilter.isQueryBlocked("  ", kids))
    }

    @Test
    fun descriptionNeedsSeveralHitsExceptForSexualContent() {
        val kids = settings(AgeGroup.KIDS)
        val m = mod("Nice Addon")
        // Una mención suelta ("sin sangre") no bloquea
        assertFalse(ContentFilter.checkDescription("A friendly addon, no blood at all.", m, kids).blocked)
        // Varias coincidencias sí
        assertTrue(ContentFilter.checkDescription("Horror, blood and gore with demons.", m, kids).blocked)
        // Lo sexual bloquea con una sola
        assertTrue(ContentFilter.checkDescription("includes nude models", m, settings(AgeGroup.TEEN)).blocked)
    }

    // ---------- PIN ----------

    @Test
    fun pinHashingVerifiesAndSaltsDiffer() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("1234", salt)
        assertTrue(PinHasher.matches("1234", salt, hash))
        assertFalse(PinHasher.matches("1235", salt, hash))
        assertTrue(PinHasher.newSalt() != salt)
        assertTrue(PinHasher.hash("1234", PinHasher.newSalt()) != hash)
    }

    @Test
    fun pinFormatMustBeFourDigits() {
        assertTrue(PinHasher.isValidFormat("0000"))
        assertFalse(PinHasher.isValidFormat("123"))
        assertFalse(PinHasher.isValidFormat("12345"))
        assertFalse(PinHasher.isValidFormat("12a4"))
    }

    @Test
    fun lockoutAfterFiveWrongPinsAndExpiresAfterAMinute() {
        var a = PinAttempts()
        repeat(4) { a = a.onFailure(1_000L) }
        assertEquals(0L, a.lockRemainingMs(1_000L))
        a = a.onFailure(1_000L) // 5.º intento
        assertEquals(PinAttempts.LOCK_MS, a.lockRemainingMs(1_000L))
        assertEquals(0L, a.lockRemainingMs(1_000L + PinAttempts.LOCK_MS))
    }

    // ---------- fecha de nacimiento ----------

    private val today = LocalDate.of(2026, 10, 13)

    @Test
    fun ageCheckHandlesBirthdayBoundary() {
        assertEquals(AgeCheck.Result.Adult, AgeCheck.check(2008, 10, 13, today)) // cumple 18 hoy
        assertEquals(AgeCheck.Result.Minor, AgeCheck.check(2008, 10, 14, today)) // mañana
        assertEquals(AgeCheck.Result.Adult, AgeCheck.check(1990, 1, 1, today))
        assertEquals(AgeCheck.Result.Minor, AgeCheck.check(2014, 6, 1, today))
    }

    @Test
    fun ageCheckRejectsImpossibleDates() {
        assertEquals(AgeCheck.Result.InvalidDate, AgeCheck.check(2000, 2, 30, today))
        assertEquals(AgeCheck.Result.InvalidDate, AgeCheck.check(2000, 13, 1, today))
        assertEquals(AgeCheck.Result.InvalidDate, AgeCheck.check(2030, 1, 1, today))
        assertEquals(AgeCheck.Result.InvalidDate, AgeCheck.check(1800, 1, 1, today))
    }
}
