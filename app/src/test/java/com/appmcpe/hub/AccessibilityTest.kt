package com.appmcpe.hub

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.appmcpe.hub.catalog.CfMod
import com.appmcpe.hub.ui.AddonHubTheme
import com.appmcpe.hub.ui.AppHeader
import com.appmcpe.hub.ui.FavoriteHeart
import com.appmcpe.hub.ui.TabBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Comprueba lo que oye un lector de pantalla (TalkBack) y que las zonas táctiles sean de 48 dp. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h568dp-xhdpi")
class AccessibilityTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun iconOnlyTabsAreNamedAndExposeSelectedState() {
        var selected = -1
        rule.setContent { AddonHubTheme { TabBar(active = 0, toolsEnabled = true, onSelect = { selected = it }) } }
        // En 320 dp solo la activa muestra texto; las demás son iconos, pero deben tener nombre.
        rule.onNodeWithContentDescription("Explorar").assertIsSelected()
        rule.onNodeWithContentDescription("Favoritos").assertIsNotSelected().assertHasClickAction()
        rule.onNodeWithContentDescription("Descargas").assertIsNotSelected()
        rule.onNodeWithContentDescription("Herramientas").assertIsNotSelected()
        rule.onNodeWithContentDescription("Descargas").performClick()
        assertEquals(2, selected)
    }

    @Test
    fun headerButtonsAreNamed() {
        var parents = 0
        var adultOff = 0
        rule.setContent {
            AddonHubTheme { AppHeader(adultMode = true, onAdultOff = { adultOff++ }, onParents = { parents++ }) }
        }
        rule.onNodeWithContentDescription("Zona de padres").assertHasClickAction().performClick()
        rule.onNodeWithContentDescription("Modo adulto activo. Toca para salir").assertHasClickAction().performClick()
        assertEquals(1, parents)
        assertEquals(1, adultOff)
    }

    @Test
    fun favoriteHeartSaysWhatItDoesAndIsBigEnough() {
        var favorite = false
        rule.setContent {
            AddonHubTheme {
                Column {
                    FavoriteHeart(favorite = favorite, onToggle = { favorite = !favorite })
                }
            }
        }
        rule.onNodeWithContentDescription("Añadir a favoritos")
            .assertHasClickAction()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun touchTargetsOfHeaderAndTabsAreAtLeast48dp() {
        rule.setContent {
            AddonHubTheme {
                Column {
                    AppHeader(adultMode = false, onAdultOff = {}, onParents = {})
                    TabBar(active = 1, toolsEnabled = false, onSelect = {})
                }
            }
        }
        rule.onNodeWithContentDescription("Zona de padres").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        for (tab in listOf("Explorar", "Favoritos", "Descargas")) {
            rule.onNodeWithContentDescription(tab).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
    }

    @Suppress("unused")
    private val unusedToKeepImport: CfMod? = null
}
