import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun loadProps(name: String): Properties = Properties().apply {
    rootProject.file(name).takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

val local = loadProps("local.properties")
val keystoreProps = loadProps("keystore.properties")

/** Proxy propio que guarda la API key (ver /backend). Si está vacío, la app habla directo con CurseForge. */
// También se puede pasar por línea de comandos: gradlew assembleRelease -Pproxy.url=... -Pproxy.token=...
val proxyUrl: String = ((project.findProperty("proxy.url") as String?) ?: local.getProperty("proxy.url", "")).trim()
val proxyToken: String = ((project.findProperty("proxy.token") as String?) ?: local.getProperty("proxy.token", "")).trim()

/** La key solo se incrusta en el APK en desarrollo y solo si no hay proxy. */
val devApiKey: String = if (proxyUrl.isBlank()) local.getProperty("curseforge.apiKey", "").trim() else ""

// Un release nunca debe llevar la key dentro: sin proxy, no se compila.
if (gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) } && proxyUrl.isBlank()) {
    throw GradleException(
        "Para compilar un release hay que configurar el proxy en local.properties " +
            "(proxy.url y proxy.token). Ver backend/README.md.",
    )
}

android {
    namespace = "com.appmcpe.hub"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.appmcpe.hub"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "0.8.0"
        buildConfigField("String", "CF_API_KEY", "\"$devApiKey\"")
        buildConfigField("String", "CF_PROXY_URL", "\"$proxyUrl\"")
        buildConfigField("String", "CF_APP_TOKEN", "\"$proxyToken\"")
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sin keystore.properties queda sin firmar (útil solo para comprobar que compila).
            signingConfig = if (keystoreProps.isNotEmpty()) signingConfigs.getByName("release") else null
            buildConfigField("String", "CF_API_KEY", "\"\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // Robolectric necesita los recursos de la app (fuentes, iconos) para dibujar pantallas.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.09.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0") // servidor local para probar descargas

    // Capturas de pantalla de la interfaz en varios tamaños, sin emulador (ver UiScreenshotsTest).
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("org.json:json:20240303") // el org.json de Android es solo un stub en tests
}
