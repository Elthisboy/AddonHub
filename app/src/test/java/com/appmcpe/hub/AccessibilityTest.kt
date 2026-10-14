package com.appmcpe.hub

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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

/**
 * Comprueba lo que oye un lector de pantalla (TalkBack) y que las zonas táctiles sean de 48 dp.
 * Los nombres se piden a los recursos, así que la misma prueba sirve en inglés y en español.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class AccessibilityBase {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun s(@StringRes id: Int) = rule.activity.getString(id)

    @Test
    fun iconOnlyTabsAreNamedAndExposeSelectedState() {
        var selected = -1
        rule.setContent { AddonHubTheme { TabBar(active = 0, toolsEnabled = true, onSelect = { selected = it }) } }
        // En 320 dp solo la activa muestra texto; las demás son iconos, pero deben tener nombre.
        rule.onNodeWithContentDescription(s(R.string.tab_explore)).assertIsSelected()
        rule.onNodeWithContentDescription(s(R.string.tab_favorites)).assertIsNotSelected().assertHasClickAction()
        rule.onNodeWithContentDescription(s(R.string.tab_downloads)).assertIsNotSelected()
        rule.onNodeWithContentDescription(s(R.string.tab_tools)).assertIsNotSelected()
        rule.onNodeWithContentDescription(s(R.string.tab_downloads)).performClick()
        assertEquals(2, selected)
    }

    @Test
    fun headerButtonsAreNamed() {
        var parents = 0
        var adultOff = 0
        rule.setContent {
            AddonHubTheme { AppHeader(adultMode = true, onAdultOff = { adultOff++ }, onParents = { parents++ }) }
        }
        rule.onNodeWithContentDescription(s(R.string.parents_zone)).assertHasClickAction().performClick()
        rule.onNodeWithContentDescription(s(R.string.adult_mode_active)).assertHasClickAction().performClick()
        assertEquals(1, parents)
        assertEquals(1, adultOff)
    }

    @Test
    fun favoriteHeartSaysWhatItDoesAndIsBigEnough() {
        rule.setContent {
            AddonHubTheme { Column { FavoriteHeart(favorite = false, onToggle = {}) } }
        }
        rule.onNodeWithContentDescription(s(R.string.fav_add))
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
        rule.onNodeWithContentDescription(s(R.string.parents_zone)).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        for (tab in listOf(R.string.tab_explore, R.string.tab_favorites, R.string.tab_downloads)) {
            rule.onNodeWithContentDescription(s(tab)).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
    }
}

@Config(sdk = [34], qualifiers = "en-w320dp-h568dp-xhdpi")
class AccessibilityEnglishTest : AccessibilityBase()

@Config(sdk = [34], qualifiers = "es-w320dp-h568dp-xhdpi")
class AccessibilitySpanishTest : AccessibilityBase()
