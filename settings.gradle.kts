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

// La interfaz de la app de celulares (Android e iPhone) se comparte en :remoteUi.
// Se arma donde hay SDK de Android o en una Mac (iPhone necesita Xcode).
val isMac = System.getProperty("os.name").startsWith("Mac")
gradle.extra["isMac"] = isMac

include(":shared", ":desktopApp")
if (hasAndroidSdk || isMac) include(":remoteUi")
if (hasAndroidSdk) include(":androidApp")
