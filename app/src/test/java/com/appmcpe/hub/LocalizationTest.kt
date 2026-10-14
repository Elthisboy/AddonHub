package com.appmcpe.hub

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.appmcpe.hub.text.UiText
import com.appmcpe.hub.text.toUiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** El idioma sale del teléfono: español si está en español; en cualquier otro caso, inglés. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalizationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    @Config(qualifiers = "es")
    fun spanishPhoneGetsSpanish() {
        assertEquals("Explorar", context.getString(R.string.tab_explore))
        assertEquals("Reintentar", context.getString(R.string.retry))
    }

    @Test
    @Config(qualifiers = "es-rCL")
    fun spanishRegionalVariantsAlsoGetSpanish() {
        assertEquals("Explorar", context.getString(R.string.tab_explore))
    }

    @Test
    @Config(qualifiers = "en")
    fun englishPhoneGetsEnglish() {
        assertEquals("Explore", context.getString(R.string.tab_explore))
    }

    @Test
    @Config(qualifiers = "fr")
    fun unsupportedLanguageFallsBackToEnglish() {
        assertEquals("Explore", context.getString(R.string.tab_explore))
        assertEquals("Try again", context.getString(R.string.retry))
    }

    @Test
    @Config(qualifiers = "ja")
    fun anotherUnsupportedLanguageAlsoFallsBackToEnglish() {
        assertEquals("Cancel", context.getString(R.string.cancel))
    }

    @Test
    @Config(qualifiers = "es")
    fun uiTextResolvesArgumentsAndNestedTexts() {
        val text = UiText.of(R.string.error_server, 503)
        assertEquals("Error del servidor: 503", text.asString(context))
        val nested = UiText.of(R.string.error_generic, "x")
        assertEquals("Algo salió mal (x)", nested.asString(context))
        assertEquals("tal cual", UiText.plain("tal cual").asString(context))
    }

    @Test
    @Config(qualifiers = "en")
    fun anyThrowableBecomesAReadableText() {
        val text = IllegalStateException("boom").toUiText()
        assertEquals("Something went wrong (boom)", text.asString(context))
    }

    // ---------- Los dos archivos de textos deben decir lo mismo ----------

    private fun readStrings(path: String): Map<String, String> {
        val file = File(path)
        assertTrue("No existe $path", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate {
            val n = nodes.item(it)
            n.attributes.getNamedItem("name").nodeValue to n.textContent
        }
    }

    private val formatArg = Regex("%(\\d+)\\$[sd]")

    @Test
    fun spanishHasExactlyTheSameKeysAsTheDefaultEnglish() {
        val en = readStrings("src/main/res/values/strings.xml") - "app_name"
        val es = readStrings("src/main/res/values-es/strings.xml")
        assertEquals("Faltan en español", emptySet<String>(), en.keys - es.keys)
        assertEquals("Sobran en español", emptySet<String>(), es.keys - en.keys)
    }

    @Test
    fun bothLanguagesUseTheSameFormatArguments() {
        val en = readStrings("src/main/res/values/strings.xml")
        val es = readStrings("src/main/res/values-es/strings.xml")
        for ((key, english) in en) {
            val spanish = es[key] ?: continue
            val a = formatArg.findAll(english).map { it.value }.toSortedSet()
            val b = formatArg.findAll(spanish).map { it.value }.toSortedSet()
            assertEquals("Argumentos distintos en '$key'", a, b)
        }
    }

    @Test
    fun noStringIsEmptyOrLeftUntranslated() {
        val en = readStrings("src/main/res/values/strings.xml")
        val es = readStrings("src/main/res/values-es/strings.xml")
        assertTrue(en.values.none { it.isBlank() })
        assertTrue(es.values.none { it.isBlank() })
        // Un texto largo idéntico en los dos idiomas casi seguro quedó sin traducir.
        val same = en.filter { (k, v) -> es[k] == v && v.length > 25 && formatArg.replace(v, "").any { it.isLetter() } }
        assertFalse("Posibles textos sin traducir: ${same.keys}", same.isNotEmpty())
    }
}
