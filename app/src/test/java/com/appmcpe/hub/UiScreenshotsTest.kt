package com.appmcpe.hub

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.appmcpe.hub.catalog.CfDependency
import com.appmcpe.hub.catalog.CfMod
import com.appmcpe.hub.catalog.DepInfo
import com.appmcpe.hub.catalog.Relation
import com.appmcpe.hub.install.InstallSession
import com.appmcpe.hub.install.Stage
import com.appmcpe.hub.text.UiText
import com.appmcpe.hub.parental.ParentalStore
import com.appmcpe.hub.catalog.Category
import com.appmcpe.hub.ui.AddonHubTheme
import com.appmcpe.hub.ui.AppHeader
import com.appmcpe.hub.ui.CategoryRow
import com.appmcpe.hub.ui.Chip
import com.appmcpe.hub.ui.TabBar
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.appmcpe.hub.ui.DetailScreen
import com.appmcpe.hub.ui.DetailState
import com.appmcpe.hub.ui.DownloadScreen
import com.appmcpe.hub.ui.ModCard
import com.appmcpe.hub.ui.OnboardingScreen
import com.appmcpe.hub.ui.ParentZoneScreen
import com.appmcpe.hub.ui.Sky
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Dibuja las pantallas de la app a PNG en `app/build/screenshots/<tamaño>/` para revisar la
 * interfaz en varios tamaños sin emulador. No compara contra nada: sirve para mirar las imágenes
 * y para que el dibujado no falle (desbordes, fuentes que no cargan, etc.).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class UiScreenshotsBase {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val mod = CfMod(
        id = 1504044, name = "Large Meals Bedrock (Unofficial)", summary = "Más comida grande y deliciosa para tu mundo.",
        author = "Elaikes", logoUrl = null, downloads = 1_234_567, fileName = "LM.mcaddon",
        downloadUrl = "https://x", sizeBytes = 37_688_517, websiteUrl = "https://www.curseforge.com/minecraft-bedrock/addons/large-meals",
        dependencies = listOf(CfDependency(1236727, Relation.REQUIRED), CfDependency(55, Relation.OPTIONAL)),
    )
    private val longNameMod = mod.copy(
        id = 2, name = "Super Mega Ultra Dragon Evolution Add-on Pack 2026 Deluxe Edition (Bedrock)",
        author = "UnCreadorConUnNombreMuyMuyLargo", downloads = 12_345, sizeBytes = 512_000,
    )
    private val farmers = mod.copy(id = 1236727, name = "Farmer's Delight Bedrock", sizeBytes = 2_000_000, dependencies = emptyList())
    private val extras = mod.copy(id = 55, name = "Extra Cosas Opcionales", sizeBytes = 800_000, dependencies = emptyList())

    private fun shot(name: String, content: @Composable () -> Unit) {
        rule.setContent { AddonHubTheme { Box(Modifier.fillMaxSize().background(Sky)) { content() } } }
        rule.waitForIdle()
        // captureToImage espera un redibujado de ventana que Robolectric no emite: se dibuja la raíz a mano.
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val dir = File("build/screenshots/${javaClass.simpleName}").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun header() = shot("00-encabezado") {
        Column {
            AppHeader(adultMode = false, onAdultOff = {}, onParents = {})
            TabBar(active = 0, toolsEnabled = true, onSelect = {})
            Spacer(Modifier.height(8.dp))
            CategoryRow(Category.TEXTURES, onSelect = {})
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                item { Chip("🔥 Popular", true) {} }
                item { Chip("⬇️ Más bajados", false) {} }
                item { Chip("🆕 Nuevos", false) {} }
            }
            Spacer(Modifier.height(24.dp))
            AppHeader(adultMode = true, onAdultOff = {}, onParents = {})
            TabBar(active = 2, toolsEnabled = false, onSelect = {})
        }
    }

    @Test
    fun cards() = shot("01-tarjetas") {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            ModCard(mod, favorite = true, onToggleFavorite = {}, onOpen = {}, onInstall = {}, note = "Instalado el 12 oct 2026")
            Spacer(Modifier.height(12.dp))
            ModCard(longNameMod, favorite = false, onToggleFavorite = {}, onOpen = {}, onInstall = {})
        }
    }

    @Test
    fun detail() = shot("02-detalle") {
        DetailScreen(
            detail = DetailState(
                mod = mod,
                description = "Este addon agrega comidas grandes. ".repeat(40),
                loadingDescription = false,
                requirements = listOf(
                    UiText.of(R.string.req_experiments, "Holiday Creator Features"),
                    UiText.plain("For the addon to work, you need to download Farmer's Delight"),
                ),
                infos = mapOf(
                    1236727 to DepInfo(1236727, farmers.name, null, farmers),
                    55 to DepInfo(55, extras.name, null, extras),
                ),
                selectedOptional = setOf(55),
            ),
            onBack = {}, onInstall = {}, onToggleOptional = {}, onAskAdult = {}, favorite = false, onToggleFavorite = {}, onOpenCurseForge = {},
        )
    }

    @Test
    fun detailBlocked() = shot("03-detalle-bloqueado") {
        DetailScreen(
            detail = DetailState(mod, description = "x", loadingDescription = false, blockReason = UiText.of(R.string.reason_not_for_age)),
            onBack = {}, onInstall = {}, onToggleOptional = {}, onAskAdult = {}, favorite = true, onToggleFavorite = {}, onOpenCurseForge = {},
        )
    }

    private fun session(stage: Stage, plan: List<String> = emptyList(), notes: List<UiText> = emptyList()) =
        InstallSession("Large Meals Bedrock (Unofficial)", null, stage, plan, notes)

    @Test
    fun downloading() = shot("04-descargando") {
        DownloadScreen(
            session(
                Stage.Downloading(1, 2, "Large Meals Bedrock (Unofficial)", 12_300_000, 37_688_517),
                plan = listOf("Farmer's Delight Bedrock", "Large Meals Bedrock (Unofficial)"),
            ),
            onClose = {}, onRetry = {}, onNext = {},
        )
    }

    @Test
    fun readyToImportWithNotes() = shot("05-listo-para-importar") {
        DownloadScreen(
            session(
                Stage.ReadyToImport(2),
                notes = listOf(
                    UiText.of(R.string.note_newer_minecraft, "1.26.30", "1.21.50"),
                    UiText.of(R.string.note_beta_apis),
                ),
            ),
            onClose = {}, onRetry = {}, onNext = {},
        )
    }

    @Test
    fun error() = shot("06-error") {
        val error = UiText.of(R.string.error_no_connection, "SocketException", "Connection reset")
        DownloadScreen(session(Stage.Error(error)), onClose = {}, onRetry = {}, onNext = {})
    }

    @Test
    fun onboarding() = shot("07-bienvenida") { OnboardingScreen(onDone = { _, _ -> }) }

    @Test
    fun parentZone() {
        ParentalStore.init(ApplicationProvider.getApplicationContext())
        shot("08-zona-de-padres") { ParentZoneScreen(onClose = {}) }
    }
}

/** Un teléfono pequeño y antiguo (320×568 dp, como un iPhone SE 1.ª gen / Android compacto). */
@Config(sdk = [34], qualifiers = "w320dp-h568dp-xhdpi")
class UiScreenshotsSmall : UiScreenshotsBase()

/** Un teléfono típico. */
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class UiScreenshotsPhone : UiScreenshotsBase()

/** Una tableta de 10". */
@Config(sdk = [34], qualifiers = "w800dp-h1280dp-xhdpi")
class UiScreenshotsTablet : UiScreenshotsBase()

/** Lo mismo en español, para revisar la traducción (textos más largos). */
@Config(sdk = [34], qualifiers = "es-w320dp-h568dp-xhdpi")
class UiScreenshotsSmallEs : UiScreenshotsBase()

@Config(sdk = [34], qualifiers = "es-w360dp-h780dp-xxhdpi")
class UiScreenshotsPhoneEs : UiScreenshotsBase()
