package com.appmcpe.hub

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.appmcpe.hub.catalog.Category
import com.appmcpe.hub.catalog.CfDependency
import com.appmcpe.hub.catalog.CfMod
import com.appmcpe.hub.catalog.DepInfo
import com.appmcpe.hub.catalog.Relation
import com.appmcpe.hub.install.InstallSession
import com.appmcpe.hub.install.Stage
import com.appmcpe.hub.parental.ParentalStore
import com.appmcpe.hub.ui.AddonHubTheme
import com.appmcpe.hub.ui.AppHeader
import com.appmcpe.hub.ui.CategoryRow
import com.appmcpe.hub.ui.Chip
import com.appmcpe.hub.ui.DarkGreen
import com.appmcpe.hub.ui.DetailScreen
import com.appmcpe.hub.ui.DetailState
import com.appmcpe.hub.ui.DownloadScreen
import com.appmcpe.hub.ui.Mascot
import com.appmcpe.hub.ui.ModCard
import com.appmcpe.hub.ui.Mood
import com.appmcpe.hub.ui.ParentZoneScreen
import com.appmcpe.hub.ui.PixelFont
import com.appmcpe.hub.ui.Sky
import com.appmcpe.hub.ui.TabBar
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Genera el material gráfico de Google Play en `store/<idioma>/` (icono, imagen destacada y capturas)
 * con contenido FICTICIO: no se usan logos, nombres ni imágenes reales de creadores.
 * Se regenera con:  gradlew testDebugUnitTest --tests "*Store*"
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class StoreAssetsBase {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** "es" o "en": sale del idioma con el que se ejecuta la prueba. */
    protected val lang: String get() = rule.activity.resources.configuration.locales[0].language
    protected val isEs: Boolean get() = lang == "es"

    protected fun render(path: String, content: @Composable () -> Unit) {
        rule.setContent { AddonHubTheme { Box(Modifier.fillMaxSize().background(Sky)) { content() } } }
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val file = File("../store/$lang/$path").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

/** Icono de la tienda: 512×512 px (el mismo en todos los idiomas). */
@Config(sdk = [34], qualifiers = "en-w512dp-h512dp-mdpi")
class StoreIcon : StoreAssetsBase() {
    @Test
    fun icon() {
        render("icon-512.png") {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        // Una sola copia, común a los dos idiomas, en la raíz de store/.
        File("../store/en/icon-512.png").copyTo(File("../store/icon-512.png"), overwrite = true)
        File("../store/en/icon-512.png").delete()
    }
}

abstract class StoreFeatureBase : StoreAssetsBase() {
    /** Imagen destacada: 1024×500 px. */
    @Test
    fun feature() = render("feature-graphic-1024x500.png") {
        Row(Modifier.fillMaxSize().padding(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Mascot(size = 280.dp, mood = Mood.HAPPY, animated = false)
            Spacer(Modifier.width(48.dp))
            Column {
                Text("AddonHub", fontSize = 64.sp, fontFamily = PixelFont, fontWeight = FontWeight.Bold, color = DarkGreen)
                Spacer(Modifier.height(16.dp))
                val (line1, line2) = if (isEs) {
                    "Addons y mapas para Minecraft Bedrock" to "en pocos toques, seguro y sin anuncios"
                } else {
                    "Add-ons and maps for Minecraft Bedrock" to "in a few taps, safe and ad-free"
                }
                Text(line1, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3A1E))
                Text(line2, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3A1E))
            }
        }
    }
}

@Config(sdk = [34], qualifiers = "es-w1024dp-h500dp-mdpi")
class StoreFeatureEs : StoreFeatureBase()

@Config(sdk = [34], qualifiers = "en-w1024dp-h500dp-mdpi")
class StoreFeatureEn : StoreFeatureBase()

/** Capturas de teléfono: 1080×2160 (relación 2:1, el máximo que acepta Google Play). */
abstract class StorePhoneBase : StoreAssetsBase() {
    private fun fake(id: Int, name: String, author: String, downloads: Long, size: Long, vararg deps: CfDependency) = CfMod(
        id = id, name = name, summary = "", author = author, logoUrl = null,
        downloads = downloads, fileName = "example.mcaddon", downloadUrl = "https://example.invalid/x",
        sizeBytes = size, dependencies = deps.toList(),
        websiteUrl = "https://www.curseforge.com/minecraft-bedrock",
    )

    private val creator get() = if (isEs) "CreadorEjemplo" else "ExampleCreator"
    private val otherCreator get() = if (isEs) "OtroCreador" else "AnotherCreator"
    private val dragons get() = fake(1, if (isEs) "Dragones Pixel" else "Pixel Dragons", creator, 1_250_000, 8_400_000)
    private val farm get() = fake(2, if (isEs) "Granja Feliz" else "Happy Farm", otherCreator, 640_000, 3_100_000, CfDependency(9, Relation.REQUIRED))
    private val island get() = fake(3, if (isEs) "Aventura en la Isla" else "Island Adventure", if (isEs) "MapasDeEjemplo" else "ExampleMaps", 310_000, 22_000_000)
    private val lib get() = fake(9, if (isEs) "Librería de Comida" else "Food Library", otherCreator, 900_000, 1_500_000)

    @Test
    fun home() = render("phone/1-explore.png") {
        Column {
            AppHeader(adultMode = false, onAdultOff = {}, onParents = {})
            TabBar(active = 0, toolsEnabled = false, onSelect = {})
            Spacer(Modifier.height(8.dp))
            CategoryRow(Category.ADDONS, onSelect = {})
            Spacer(Modifier.height(8.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Chip(rule.activity.getString(R.string.sort_popular), true) {} }
                item { Chip(rule.activity.getString(R.string.sort_downloads), false) {} }
                item { Chip(rule.activity.getString(R.string.sort_newest), false) {} }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ModCard(dragons, favorite = true, onToggleFavorite = {}, onOpen = {}, onInstall = {}, fallbackEmoji = "🐉")
                ModCard(farm, favorite = false, onToggleFavorite = {}, onOpen = {}, onInstall = {}, fallbackEmoji = "🌾")
                ModCard(island, favorite = false, onToggleFavorite = {}, onOpen = {}, onInstall = {}, fallbackEmoji = "🏝️")
            }
        }
    }

    @Test
    fun detail() = render("phone/2-detail.png") {
        DetailScreen(
            detail = DetailState(
                mod = farm,
                description = if (isEs) "Una granja llena de cultivos y animales para tu mundo." else "A farm full of crops and animals for your world.",
                loadingDescription = false,
                infos = mapOf(9 to DepInfo(9, lib.name, null, lib)),
            ),
            onBack = {}, onInstall = {}, onToggleOptional = {}, onAskAdult = {}, favorite = false,
            onToggleFavorite = {}, onOpenCurseForge = {},
        )
    }

    @Test
    fun downloading() = render("phone/3-downloading.png") {
        DownloadScreen(
            InstallSession(
                farm.name, null, Stage.Downloading(1, 2, farm.name, 12_300_000, 31_000_000),
                plan = listOf(lib.name, farm.name),
            ),
            onClose = {}, onRetry = {}, onNext = {},
        )
    }

    @Test
    fun parents() {
        ParentalStore.init(ApplicationProvider.getApplicationContext())
        render("phone/4-parents.png") { ParentZoneScreen(onClose = {}) }
    }
}

@Config(sdk = [34], qualifiers = "es-w360dp-h720dp-xxhdpi")
class StorePhoneEs : StorePhoneBase()

@Config(sdk = [34], qualifiers = "en-w360dp-h720dp-xxhdpi")
class StorePhoneEn : StorePhoneBase()
