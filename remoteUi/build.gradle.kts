import com.android.build.gradle.LibraryExtension

/*
 * Interfaz de Selah Remoto compartida entre Android e iPhone (Compose Multiplatform).
 * commonMain: pantallas, conexión con el PC, clave, Biblia, lista… (todo lo que se ve)
 * androidMain / iosMain: lo propio de cada sistema (red, búsqueda del PC, preferencias, fotos).
 */
plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val withAndroid = gradle.extra["withAndroid"] as Boolean
if (withAndroid) apply(plugin = "com.android.library")

val ktor = "3.1.3" // cliente; 3.6 trae OkHttp 5 (exige compileSdk 37 en Android)

kotlin {
    jvmToolchain(17)
    if (withAndroid) androidTarget()
    // iPhone: el framework que usa el proyecto de Xcode (iosApp/). Solo se compila en una Mac.
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach {
        it.binaries.framework {
            baseName = "SelahRemotoUI"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            implementation("io.ktor:ktor-client-core:$ktor")
            implementation("io.ktor:ktor-client-websockets:$ktor")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
        if (withAndroid) androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:$ktor")
            implementation("androidx.activity:activity-compose:1.9.3")
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:$ktor")
        }
    }
}

if (withAndroid) {
    extensions.configure<LibraryExtension> {
        namespace = "app.selah.remote.ui"
        compileSdk = 36
        defaultConfig { minSdk = 24 }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}
