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
import androidx.compose.foundation.layout.size
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
import com.appmcpe.hub.ui.Mood
import com.appmcpe.hub.ui.ModCard
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
 * Genera el material gráfico de Google Play en `store/` (icono, imagen destacada y capturas) con
 * contenido FICTICIO: no se usan logos, nombres ni imágenes reales de creadores.
 * Se regenera con:  gradlew testDebugUnitTest --tests "*StoreAssets*"
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class StoreAssetsBase {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    protected fun render(path: String, content: @Composable () -> Unit) {
        rule.setContent { AddonHubTheme { Box(Modifier.fillMaxSize().background(Sky)) { content() } } }
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val file = File("../store/$path").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

/** Icono de la tienda: 512×512 px. */
@Config(sdk = [34], qualifiers = "w512dp-h512dp-mdpi")
class StoreIcon : StoreAssetsBase() {
    @Test
    fun icon() = render("icon-512.png") {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

/** Imagen destacada: 1024×500 px. */
@Config(sdk = [34], qualifiers = "w1024dp-h500dp-mdpi")
class StoreFeatureGraphic : StoreAssetsBase() {
    @Test
    fun feature() = render("feature-graphic-1024x500.png") {
        Row(Modifier.fillMaxSize().padding(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Mascot(size = 280.dp, mood = Mood.HAPPY, animated = false)
            Spacer(Modifier.width(48.dp))
            Column {
                Text("AddonHub", fontSize = 64.sp, fontFamily = PixelFont, fontWeight = FontWeight.Bold, color = DarkGreen)
                Spacer(Modifier.height(16.dp))
                Text(
                    "Addons y mapas para Minecraft Bedrock",
                    fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3A1E),
                )
                Text(
                    "en pocos toques, seguro y sin anuncios",
                    fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B3A1E),
                )
            }
        }
    }
}

/** Capturas de teléfono: 1080×2160 (relación 2:1, el máximo que acepta Google Play). */
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xxhdpi")
class StorePhoneScreenshots : StoreAssetsBase() {
    private fun fake(id: Int, name: String, author: String, downloads: Long, size: Long, vararg deps: CfDependency) = CfMod(
        id = id, name = name, summary = "Contenido de ejemplo", author = author, logoUrl = null,
        downloads = downloads, fileName = "ejemplo.mcaddon", downloadUrl = "https://example.invalid/x",
        sizeBytes = size, dependencies = deps.toList(),
        websiteUrl = "https://www.curseforge.com/minecraft-bedrock",
    )

    private val dragons = fake(1, "Dragones Pixel", "CreadorEjemplo", 1_250_000, 8_400_000)
    private val farm = fake(2, "Granja Feliz", "OtroCreador", 640_000, 3_100_000, CfDependency(9, Relation.REQUIRED))
    private val island = fake(3, "Aventura en la Isla", "MapasDeEjemplo", 310_000, 22_000_000)
    private val lib = fake(9, "Librería de Comida", "OtroCreador", 900_000, 1_500_000)

    @Test
    fun home() = render("phone/1-explorar.png") {
        Column {
            AppHeader(adultMode = false, onAdultOff = {}, onParents = {})
            TabBar(active = 0, toolsEnabled = false, onSelect = {})
            Spacer(Modifier.height(8.dp))
            CategoryRow(Category.ADDONS, onSelect = {})
            Spacer(Modifier.height(8.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Chip("🔥 Popular", true) {} }
                item { Chip("⬇️ Más bajados", false) {} }
                item { Chip("🆕 Nuevos", false) {} }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ModCard(dragons, favorite = true, onToggleFavorite = {}, onOpen = {}, onInstall = {}, fallbackEmoji = "🐉")
                ModCard(farm, favorite = false, onToggleFavorite = {}, onOpen = {}, onInstall = {}, fallbackEmoji = "🌾")
                ModCard(island, favorite = false, onToggleFavorite = {}, onOpen = {}, onInstall = {}, fallbackEmoji = "🏝️")
            }
        }
    }

    @Test
    fun detail() = render("phone/2-detalle.png") {
        DetailScreen(
            detail = DetailState(
                mod = farm,
                description = "Una granja llena de cultivos y animales para tu mundo.",
                loadingDescription = false,
                infos = mapOf(9 to DepInfo(9, lib.name, null, lib)),
            ),
            onBack = {}, onInstall = {}, onToggleOptional = {}, onAskAdult = {}, favorite = false,
            onToggleFavorite = {}, onOpenCurseForge = {},
        )
    }

    @Test
    fun downloading() = render("phone/3-descargando.png") {
        DownloadScreen(
            InstallSession(
                "Granja Feliz", null, Stage.Downloading(1, 2, "Granja Feliz", 12_300_000, 31_000_000),
                plan = listOf("Librería de Comida", "Granja Feliz"),
            ),
            onClose = {}, onRetry = {}, onNext = {},
        )
    }

    @Test
    fun parents() {
        ParentalStore.init(ApplicationProvider.getApplicationContext())
        render("phone/4-padres.png") { ParentZoneScreen(onClose = {}) }
    }
}
