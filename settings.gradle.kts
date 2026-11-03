rootProject.name = "proyecta"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// El módulo Android solo se incluye si hay SDK de Android (local.properties o ANDROID_HOME).
// Así en un PC con Windows basta el JDK para compilar y empaquetar el programa de escritorio.
val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
    file("local.properties").takeIf { it.exists() }?.readText()?.contains("sdk.dir") == true
gradle.extra["withAndroid"] = hasAndroidSdk

include(":shared", ":desktopApp")
if (hasAndroidSdk) include(":androidApp")
