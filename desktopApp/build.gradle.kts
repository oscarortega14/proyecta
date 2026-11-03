import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin { jvmToolchain(17) }

val ktor = "3.0.1"

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("io.ktor:ktor-server-core:$ktor")
    implementation("io.ktor:ktor-server-cio:$ktor")
    implementation("io.ktor:ktor-server-websockets:$ktor")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.slf4j:slf4j-nop:2.0.16")
    // Fondos de video: usa el VLC instalado en el PC (si no está, se ignoran los videos).
    implementation("uk.co.caprica:vlcj:4.8.3")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-client-cio:$ktor")
    testImplementation("io.ktor:ktor-client-websockets:$ktor")
}

compose.desktop {
    application {
        mainClass = "app.proyecta.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Proyecta"
            packageVersion = "1.2.0"
            description = "Proyección de canciones y Biblia con control remoto"
            vendor = "Proyecta"
            copyright = "MIT License"
            // Se incluye el entorno Java completo: el PC de la iglesia no necesita instalar nada más.
            includeAllModules = true
            windows {
                menuGroup = "Proyecta"
                dirChooser = true
                shortcut = true
                perUserInstall = false
                // Fijo para siempre: así una versión nueva reemplaza a la anterior al instalarse.
                upgradeUuid = "7f0c7a52-3b1e-4c58-9f0e-2b6f3a9d8e41"
            }
        }
    }
}
