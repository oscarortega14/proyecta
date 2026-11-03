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
            packageVersion = "1.0.0"
            windows {
                menuGroup = "Proyecta"
                dirChooser = true
                shortcut = true
            }
        }
    }
}
