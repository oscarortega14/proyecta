import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.net.URI
import java.security.MessageDigest

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin { jvmToolchain(17) }

val ktor = "3.0.1"

/** Versión de Proyecta: va al instalador y a la app (para avisar cuando hay una más nueva). */
val appVersion = "2.2.1"

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
    // Códigos QR para conectar celulares escaneando (ventana "Conectar dispositivos").
    implementation("com.google.zxing:core:3.5.3")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-client-cio:$ktor")
    testImplementation("io.ktor:ktor-client-websockets:$ktor")
}

// --- Clave de la API de Pixabay (buscador de fondos) ---
// No se sube al repositorio: se lee de desktopApp/pixabay.key o de la variable PIXABAY_KEY
// y se escribe en un recurso generado al compilar.
val generatedResources = layout.buildDirectory.dir("generated/proyecta-resources")
val writeBuildConfig by tasks.registering {
    val keyFile = layout.projectDirectory.file("pixabay.key").asFile
    val key = (System.getenv("PIXABAY_KEY") ?: keyFile.takeIf { it.exists() }?.readText())?.trim().orEmpty()
    inputs.property("pixabayKeyHash", key.hashCode())
    inputs.property("appVersion", appVersion)
    outputs.dir(generatedResources)
    doLast {
        val f = generatedResources.get().file("proyecta-build.properties").asFile
        f.parentFile.mkdirs()
        f.writeText("pixabay.key=$key\nversion=$appVersion\n")
    }
}
sourceSets.main { resources.srcDir(writeBuildConfig) }

// --- VLC incluido (solo Windows): reproduce los fondos de video sin instalar nada aparte ---
// Se descarga el VLC portable oficial una vez, se verifica su SHA-256 y se extrae solo lo
// necesario para reproducir video (sin interfaz, idiomas, red ni codificadores).
val vlcVersion = "3.0.21"
val vlcSha256 = "a0b7ec02b50adf6417eed014fb8df50af39690505a4225b85b3dc2ed17d14843"
val vlcZip = layout.buildDirectory.file("vlc-download/vlc-$vlcVersion-win64.zip")
val appResources = layout.buildDirectory.dir("appResources")

val downloadVlc by tasks.registering {
    outputs.file(vlcZip)
    doLast {
        val zip = vlcZip.get().asFile
        fun sha(f: File) = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
        if (!zip.exists() || sha(zip) != vlcSha256) {
            zip.parentFile.mkdirs()
            val url = "https://download.videolan.org/pub/videolan/vlc/$vlcVersion/win64/vlc-$vlcVersion-win64.zip"
            logger.lifecycle("Descargando VLC $vlcVersion…")
            URI(url).toURL().openStream().use { input -> zip.outputStream().use { input.copyTo(it) } }
            check(sha(zip) == vlcSha256) { "La firma SHA-256 de VLC no coincide: descarga dañada o alterada" }
        }
    }
}

val prepareVlc by tasks.registering(Sync::class) {
    dependsOn(downloadVlc)
    val keepPluginDirs = listOf("codec", "demux", "video_chroma", "video_output", "video_filter", "packetizer", "stream_filter", "misc", "d3d11", "d3d9")
    from(zipTree(vlcZip)) {
        include("vlc-$vlcVersion/libvlc.dll", "vlc-$vlcVersion/libvlccore.dll", "vlc-$vlcVersion/COPYING.txt", "vlc-$vlcVersion/AUTHORS.txt")
        include(keepPluginDirs.map { "vlc-$vlcVersion/plugins/$it/**" })
        include("vlc-$vlcVersion/plugins/access/libfilesystem_plugin.dll")
        // codificadores (solo sirven para crear video) y subtítulos avanzados
        exclude("**/libx264*", "**/libx265*", "**/libx26410b*", "**/liblibass*", "**/plugins.dat")
        eachFile { path = path.removePrefix("vlc-$vlcVersion/") }
        includeEmptyDirs = false
    }
    into(appResources.map { it.dir("windows/vlc") })
}

compose.desktop {
    application {
        mainClass = "app.proyecta.desktop.MainKt"
        // Tope de memoria: sin él Java puede crecer hasta 1/4 de la RAM antes de limpiar.
        // Con 512 MB sobra para proyectar con video y la app nunca se infla en un PC modesto.
        jvmArgs += listOf("-Xmx512m", "-XX:MaxMetaspaceSize=192m")
        nativeDistributions {
            appResourcesRootDir.set(appResources)
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Proyecta"
            packageVersion = appVersion
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

// El VLC incluido entra en el instalador de Windows y en `run` (para probar).
tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(prepareVlc) }

// En Windows las pruebas usan el mismo VLC incluido que la app empaquetada.
if (System.getProperty("os.name").startsWith("Windows")) {
    tasks.withType<Test>().configureEach {
        dependsOn(prepareVlc)
        systemProperty("compose.application.resources.dir", appResources.get().dir("windows").asFile.absolutePath)
        System.getenv("PROYECTA_VIDEO")?.let { environment("PROYECTA_VIDEO", it) }
    }
}
