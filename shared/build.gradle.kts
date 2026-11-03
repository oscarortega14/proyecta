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

    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

if (withAndroid) {
    extensions.configure<LibraryExtension> {
        namespace = "app.proyecta.shared"
        compileSdk = 34
        defaultConfig { minSdk = 24 }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}
