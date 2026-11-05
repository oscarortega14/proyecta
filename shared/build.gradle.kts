import com.android.build.gradle.LibraryExtension

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

val withAndroid = gradle.extra["withAndroid"] as Boolean
if (withAndroid) apply(plugin = "com.android.library")

kotlin {
    jvmToolchain(17)
    jvm()
    if (withAndroid) androidTarget()
    // iPhone (solo se compilan en una Mac; en Linux/Windows Gradle los salta)
    iosArm64(); iosSimulatorArm64(); iosX64()

    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

if (withAndroid) {
    extensions.configure<LibraryExtension> {
        namespace = "app.proyecta.shared"
        compileSdk = 36
        defaultConfig { minSdk = 24 }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}
