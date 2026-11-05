import java.util.Properties
plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin { jvmToolchain(17) }

val appVersion = providers.gradleProperty("proyecta.version").get()

android {
    namespace = "app.proyecta.remote"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.proyecta.remote"
        minSdk = 24 // Android 7: la tablet de la iglesia
        targetSdk = 34
        // Mismo número que el programa del PC (gradle.properties); 2.3.0 → 20300
        versionName = appVersion
        versionCode = appVersion.split('.').map { it.toInt() }.let { (a, b, c) -> a * 10000 + b * 100 + c }
    }

    // Clave propia de Proyecta (fuera del repositorio, en ~/.config/proyecta). Android solo acepta
    // una actualización firmada con la MISMA clave: hay que respaldarla. Sin ella se usa la de depuración.
    val releaseKey = File(System.getProperty("user.home"), ".config/proyecta/firma-android.properties")
    signingConfigs {
        if (releaseKey.isFile) create("proyecta") {
            val p = Properties().apply { releaseKey.inputStream().use { load(it) } }
            storeFile = File(p.getProperty("storeFile"))
            storePassword = p.getProperty("storePassword")
            keyAlias = p.getProperty("keyAlias")
            keyPassword = p.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("proyecta") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    packaging {
        resources.excludes += setOf("META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties", "META-INF/*.kotlin_module")
    }
}

val ktor = "3.1.3" // cliente; 3.6 trae OkHttp 5, que pide compileSdk 37

dependencies {
    implementation(project(":shared"))
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("io.ktor:ktor-client-okhttp:$ktor")
    implementation("io.ktor:ktor-client-websockets:$ktor")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
