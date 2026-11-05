# Selah — contexto para Claude Code

Programa libre (MIT) para proyectar letras de canciones y la Biblia en iglesias, con control remoto
desde celulares. Repositorio público: github.com/oscarortega14/selah. El dueño habla español;
**la interfaz, los comentarios y los mensajes de commit van en español**, en lenguaje sencillo
(lo usan voluntarios de la iglesia, no técnicos).

**Si estás en la Mac para hacer la app de iPhone: lee `docs/IOS.md` primero** (estado, pasos y pendientes).

## Módulos
- `shared/` — KMP (JVM, Android, iOS): modelos, protocolo WebSocket (`Protocol.kt`), Biblia, búsqueda,
  `Releases` (actualizaciones), `mergeState`.
- `desktopApp/` — Windows (Compose Desktop + servidor Ktor 3.6.1 en el puerto 8765, UDP 8766):
  operador, ventana de proyección, control web/PWA (`resources/web/`), vista de escenario, página OBS.
- `remoteUi/` — Compose Multiplatform (Android + iOS): **toda** la app del celular (pantallas + `RemoteClient`).
  `androidMain`/`iosMain` solo tienen lo de cada sistema (`Platform.*.kt`, `IosEntry.kt`).
- `androidApp/` — la Activity + variantes `github` (se actualiza sola desde GitHub Releases) y `play`
  (Google Play, sin actualizador). minSdk 24 (tablet de la iglesia con Android 7), target/compile 36.
- `iosApp/` — SwiftUI mínimo + `project.yml` (XcodeGen). El .xcodeproj no se versiona.

## Versiones y herramientas
Kotlin 2.3.21, Compose MP 1.10.3, AGP 8.13.2, Gradle 8.14.6, JDK 17. Ktor **cliente** 3.1.3 en el celular
(3.6 trae OkHttp 5 → compileSdk 37). La versión de todo está en `gradle.properties` (`selah.version`);
Android: versionCode = a*10000+b*100+c. iOS: `iosApp/project.yml` (mantener igual a mano).

## Comandos
```bash
./gradlew test                                   # pruebas (desktop, shared, remoteUi, android)
./gradlew :desktopApp:run                        # programa de escritorio
./gradlew :androidApp:assembleGithubRelease      # APK (variante github)
./gradlew :androidApp:bundlePlayRelease          # AAB para Google Play
./gradlew :desktopApp:packageMsi                 # instalador Windows (en Windows)
./gradlew :desktopApp:packageMsix                # paquete para Microsoft Store (en Windows)
./gradlew :remoteUi:linkDebugFrameworkIosSimulatorArm64   # iPhone (solo en Mac)
cd iosApp && xcodegen                            # proyecto de Xcode
```

## Reglas importantes
- **Nunca** subir al repositorio claves ni contraseñas: `~/.config/selah/` (clave Ed25519 de firma
  de actualizaciones, keystore de Android y sus contraseñas), `desktopApp/pixabay.key`, Team ID privado.
- Las actualizaciones del PC se verifican con firma Ed25519 (`tools/Firma.java`, clave pública en
  `desktopApp/.../Updater.kt`). La clave privada vive solo en el PC con Windows/WSL del dueño.
- **Publicar una versión en GitHub Releases, en una tienda o hacer push de etiquetas requiere permiso
  explícito del dueño cada vez.**
- Commits con el correo privado de GitHub del dueño (`…@users.noreply.github.com`), ya configurado.
- No cambiar ajustes de seguridad de los dispositivos de prueba (p. ej. "fuentes desconocidas").
- Cada mejora lleva pruebas; lo visual se comprueba también en un dispositivo real cuando se puede.
- El PC es la única fuente de verdad: el operador, el control web y las apps mandan `ClientMessage`
  y reciben `ServerMessage.State`. Al agregar algo al protocolo, mantener compatibilidad con apps viejas
  (campos con valor por defecto; `Hello(features)`).

## Seguridad (no romper)
Clave (PIN) con bloqueo por IP + límite global (`PinGuard`), comprobación de Origin/Host, marcos de 64 KB,
10 conexiones por IP, 20 mensajes/s, textos recortados (`NetGuard`), subidas solo jpg/png/webp/mp4/mov con
cuota de 5 GB, `SELAH_UPDATE_URL` solo para localhost.

## Tiendas (estado)
Ver §9 de `docs/IOS.md`: Google Play (falta crear la app, 12 probadores × 14 días), Microsoft Store
(falta la cuenta de Partner Center y la identidad del paquete), App Store (en curso desde la Mac).
