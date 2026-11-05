# Proyecta Remoto para iPhone / iPad: guía para continuar en la Mac

Este documento tiene **todo el contexto** para seguir creando la app de iPhone desde la Mac
(a mano o con Claude Code: el archivo `CLAUDE.md` de la raíz se carga solo y apunta aquí).

---

## 1. En qué estado está (noviembre 2026, versión 2.4.0)

La app de iPhone **reutiliza la misma interfaz que la de Android**: todas las pantallas viven en el
módulo compartido `remoteUi/` (Compose Multiplatform). Ya está hecho y probado en Android:

| Hecho | Dónde |
|---|---|
| Pantallas (En pantalla, Canciones, Biblia con explorador, Lista, Más), clave, cronómetro, nombre en pantalla… | `remoteUi/src/commonMain/kotlin/app/proyecta/remote/` |
| Conexión WebSocket con el PC (`RemoteClient`), reconexión, clave guardada por computador | `remoteUi/.../RemoteClient.kt`, `ProyectaRemoto.kt` |
| Enlaces `proyecta://connect?host=…&pin=…` (botón "Abrir en la app" del control web) | `parseConnectLink` en `ProyectaRemoto.kt` |
| Escribir la dirección del PC a mano (si la búsqueda automática no funciona) | `ManualAddress` en `RemoteApp.kt` |
| Avisos cortos ("Agregada a la lista") iguales en ambos sistemas | `Notice.kt` |
| Lo propio de Android (búsqueda UDP, fotos, preferencias) | `remoteUi/src/androidMain/.../Platform.android.kt` |
| **Lo propio del iPhone (sin compilar todavía: se escribió en Linux)** | `remoteUi/src/iosMain/kotlin/app/proyecta/remote/` |
| Proyecto de Xcode (XcodeGen), app en SwiftUI que muestra la interfaz de Kotlin | `iosApp/project.yml`, `iosApp/ProyectaRemoto/ProyectaRemotoApp.swift` |
| Ícono 1024×1024 sin transparencia | `iosApp/ProyectaRemoto/Assets.xcassets/AppIcon.appiconset/` |
| Pruebas comunes (fecha del próximo domingo, enlaces, clave por computador) | `remoteUi/src/commonTest/` |

**Pendiente en la Mac** (en este orden):

1. Compilar por primera vez y corregir lo que falle en `iosMain` (se escribió sin poder compilarlo).
2. Probar en el simulador y en un iPhone real contra el PC con Proyecta.
3. Búsqueda automática del PC en iPhone (ver §5: dos caminos).
4. Subir fotos/videos a Anuncios desde el iPhone (ver §6). Mientras no esté, el botón no aparece.
5. Capturas, ficha y envío a App Store Connect / TestFlight (§8).

---

## 2. Cómo está armado el proyecto

```
proyecta/
├── shared/        Kotlin Multiplatform (JVM, Android, iOS): modelos, protocolo, búsqueda, Biblia
├── remoteUi/      Compose Multiplatform (Android + iOS): TODA la app del celular
│   ├── commonMain/   pantallas, RemoteClient, enlaces, avisos
│   ├── androidMain/  OkHttp, UDP, selector de fotos, SharedPreferences
│   └── iosMain/      Darwin (Ktor), NSUserDefaults, IosEntry.kt (puerta para Swift)
├── androidApp/    Solo la Activity de Android + variantes github/play + actualizador (github)
├── iosApp/        Solo la ventana de iPhone (SwiftUI) + project.yml
└── desktopApp/    Programa de Windows (operador, proyección, servidor Ktor, control web)
```

- `settings.gradle.kts` incluye `:remoteUi` donde hay SDK de Android **o en una Mac**; `:androidApp`
  solo con SDK de Android. En la Mac no hace falta el SDK de Android para el iPhone.
- `gradle.properties` tiene `kotlin.native.ignoreDisabledTargets=true`: en Linux/Windows los objetivos
  iOS se saltan sin error.
- Kotlin 2.3.21, Compose Multiplatform 1.10.3, Ktor **cliente 3.1.3** (3.6 trae OkHttp 5, que en Android
  exige compileSdk 37; en iOS se usa el motor Darwin de la misma versión).
- Las funciones de Kotlin de `IosEntry.kt` se ven en Swift como `IosEntryKt.MainViewController()` y
  `IosEntryKt.openUrl(url:)`. El framework se llama `ProyectaRemotoUI` (estático).

### Protocolo con el PC (no cambia)
- WebSocket `ws://IP:8765/ws`, mensajes JSON `ClientMessage` / `ServerMessage` (`shared/.../Protocol.kt`).
- `GET http://IP:8765/info` → `{"name": "<nombre del PC>"}` (sirve para comprobar una dirección).
- Búsqueda: UDP broadcast a puerto 8766 con `DISCOVERY_REQUEST`; el PC responde `DiscoveryReply`.
- Clave (PIN de 4 o 6 números): `ClientMessage.Login`; el PC bloquea por IP tras varios intentos.
- Subir anuncios: `POST http://IP:8765/anuncios?nombre=…` con cabecera `X-Proyecta-Pin`
  (solo jpg/png/webp/mp4/mov).

---

## 3. Preparar la Mac (una vez)

```bash
# Xcode desde la App Store (16 o más nuevo) y abrirlo una vez para aceptar la licencia
xcode-select --install                 # herramientas de línea de comandos
brew install --cask temurin@17         # JDK 17 (Gradle lo necesita)
brew install xcodegen                  # genera el .xcodeproj a partir de iosApp/project.yml
git clone https://github.com/oscarortega14/proyecta.git && cd proyecta
# (o, si ya estaba: git pull)
```

Si Gradle no encuentra Java: `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`.

## 4. Compilar y correr

```bash
# 1) ¿Compila la parte de iPhone? (lo más probable es que haya que corregir algo en iosMain)
./gradlew :remoteUi:compileKotlinIosSimulatorArm64
./gradlew :remoteUi:linkDebugFrameworkIosSimulatorArm64
./gradlew :remoteUi:iosSimulatorArm64Test      # corre las pruebas comunes en el simulador

# 2) Proyecto de Xcode
cd iosApp && xcodegen && open ProyectaRemoto.xcodeproj
```

En Xcode:
1. Target **ProyectaRemoto → Signing & Capabilities → Team**: elegir el equipo de desarrollador
   (o escribir el Team ID en `DEVELOPMENT_TEAM` de `project.yml` para no repetirlo).
2. Elegir un simulador de iPhone y ▶. La fase "Compilar Kotlin (remoteUi)" llama a
   `./gradlew :remoteUi:embedAndSignAppleFrameworkForXcode` (la primera vez tarda varios minutos).
3. En el simulador: la búsqueda automática aún no funciona → "Escribir la dirección del computador"
   y poner la IP del PC (aparece en Proyecta → "Conectar tablets y celulares"). El simulador usa la red
   de la Mac, así que la Mac debe estar en el mismo Wi-Fi que el PC.
4. iPhone real: conectarlo por cable, activar **Modo de desarrollador** (Ajustes → Privacidad y
   seguridad), elegirlo en Xcode y ▶. La primera vez iOS pregunta "¿Permitir buscar dispositivos en la
   red local?" → **Permitir** (si se niega, no podrá hablar con el PC: Ajustes → Proyecta Remoto → Red local).
5. Probar el enlace: en Safari del iPhone abrir el control web `http://IP:8765` y tocar "Abrir en la app"
   (o escribir `proyecta://connect?host=IP` en Safari).

### Problemas probables al compilar `iosMain` por primera vez
- `platform.Foundation.timeIntervalSince1970`: si no compila el import, usar
  `NSDate().timeIntervalSince1970` sin import (o `kotlin.time.Clock.System.now().toEpochMilliseconds()`).
- `NSCalendar.components(...)`: puede pedir `@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)`.
- Si Xcode no encuentra `ProyectaRemotoUI`: revisar que exista
  `remoteUi/build/xcode-frameworks/Debug/iphonesimulator*/ProyectaRemotoUI.framework` tras compilar.
- Versión: `project.yml` tiene `MARKETING_VERSION: "2.4.0"` y `CURRENT_PROJECT_VERSION: "20400"`.
  Mantenerlos iguales a `proyecta.version` de `gradle.properties` (2.4.0 → 20400, como Android).

---

## 5. Búsqueda automática del PC en iPhone

En Android se envía un broadcast UDP a 255.255.255.255:8766. En iOS 14+ **enviar broadcast/multicast
requiere el permiso especial de Apple** `com.apple.developer.networking.multicast`. Por eso
`discoverServers()` en `Platform.ios.kt` devuelve una lista vacía y la persona escribe la dirección.

Dos caminos (se pueden hacer los dos):

**A. Sin pedir nada a Apple: recorrer la subred por HTTP** (recomendado primero)
- Averiguar la IP propia del iPhone en Wi-Fi (interfaz `en0`) con `getifaddrs` (posix, cinterop).
- Probar `GET http://<prefijo>.1…254:8765/info` en paralelo (≈ 32 a la vez, tiempo límite ≈ 700 ms,
  plugin `HttpTimeout` de Ktor). Los que respondan son PCs con Proyecta (`DiscoveryReply.name`).
- Solo necesita el permiso de red local que ya está en `Info.plist` (`NSLocalNetworkUsageDescription`).
- Hacer esta lógica en `commonMain` (recibe el prefijo) para poder probarla también en Android, y dejar
  en `iosMain` solo la lectura de la IP.

**B. El permiso de multicast de Apple**
- Pedirlo en https://developer.apple.com/contact/request/networking-multicast (explicar: "la app busca
  en la red local el programa Proyecta del computador de la iglesia con un broadcast UDP al puerto 8766").
- Cuando lo aprueben: agregar el entitlement al App ID y a un archivo `.entitlements`
  (`project.yml → targets.ProyectaRemoto.entitlements`), e implementar `discoverServers` con sockets
  posix (`socket`, `setsockopt SO_BROADCAST`, `sendto`, `recvfrom`) o `Network.framework`.

---

## 6. Subir fotos y videos (Anuncios) desde el iPhone

`rememberMediaPicker` en iOS devuelve `null` (el botón no aparece). Para hacerlo:
- Presentar `PHPickerViewController` (fotos y videos) desde el `UIViewController` de Compose
  (`LocalUIViewController.current`).
- Con el resultado, `loadFileRepresentation` → copiar a un archivo temporal → `PickedFile(nombre, tamaño)`
  cuyo `open()` lea el archivo por partes como `ByteReadChannel` (no cargar el video entero en memoria).
- `RemoteClient.uploadAnnouncement(file)` ya hace el resto (es común).
- PHPicker no necesita permiso de fotos; si se usa la cámara hay que agregar `NSCameraUsageDescription`.

---

## 7. Detalles de la app que conviene saber

- La app es siempre oscura (`UIUserInterfaceStyle: Dark`, `darkColorScheme()`).
- La pantalla no se apaga mientras la app está abierta (`isIdleTimerDisabled`), igual que en Android.
- `safeDrawingPadding()` en `ProyectaRemoto` deja libre la isla dinámica, la barra de inicio y el teclado.
- La clave del PC se guarda en `NSUserDefaults` con prefijo `proyecta.` y solo se envía al mismo
  computador donde se aceptó (`pinHost`).
- ATS: `NSAllowsLocalNetworking` permite `http://` / `ws://` a la red local. No hay conexiones a internet
  (la versión de iPhone no tiene actualizador propio: se actualiza por la App Store).
- `ITSAppUsesNonExemptEncryption = false`: no hay que responder la pregunta de cifrado en cada envío.

---

## 8. App Store Connect y TestFlight

1. **Identificador**: developer.apple.com → Certificates, IDs & Profiles → Identifiers → App ID
   `app.proyecta.remote` (el mismo que en Android). Sin capacidades especiales (salvo multicast si lo dan).
2. **App Store Connect → Apps → +**: Nombre "Proyecta Remoto" (si está tomado: "Proyecta Remoto Iglesia"),
   idioma principal Español, bundle `app.proyecta.remote`, SKU `proyecta-remoto`.
3. **Subir una versión**: en Xcode, destino "Any iOS Device (arm64)" → Product → Archive → Distribute App
   → App Store Connect. (Firmado automático con el Team.)
4. **TestFlight**: los probadores internos (hasta 100, de tu equipo) la reciben al instante sin revisión.
   Externos (hasta 10 000, con enlace público) necesitan una revisión beta corta. **En Apple no existe la
   prueba obligatoria de 12 personas × 14 días de Google Play.**
5. **Ficha**: reutilizar los textos de `docs/play/ficha.md` (nombre, subtítulo ≤ 30 caracteres:
   "Control de letras y Biblia", descripción, palabras clave: iglesia,proyección,letras,biblia,control
   remoto,videobeam,alabanza). URL de privacidad:
   https://github.com/oscarortega14/proyecta/blob/main/docs/privacidad.md ; URL de soporte: la del repositorio.
6. **Privacidad de la app**: "No se recopilan datos" (todo va solo al PC del usuario en su red local).
7. **Clasificación por edad**: todo "Ninguno" → 4+. Categoría: Productividad (secundaria: Estilo de vida).
8. **Capturas obligatorias**: iPhone 6,9" (1320×2868 o 1290×2796) y, como el proyecto también es para
   iPad (`TARGETED_DEVICE_FAMILY: "1,2"`), iPad 13" (2064×2752). Se pueden sacar del simulador
   (⌘S) conectado al PC de prueba. Si no se quiere iPad al principio: `TARGETED_DEVICE_FAMILY: "1"`.
9. **Notas para el revisor (muy importante)**: la app no hace nada sin el programa del PC, y Apple suele
   rechazar por "no pudimos probarla" (guía 2.1). Poner en *App Review Information → Notes*:
   > Proyecta Remoto is the remote control for Proyecta, a free, open-source Windows program
   > (https://github.com/oscarortega14/proyecta) that projects song lyrics and Bible verses in churches.
   > The app connects only to that program on the same Wi-Fi network; it has no accounts and collects no data.
   > A demo video showing the app controlling the projection is here: <enlace a video en YouTube sin listar>.
   Y grabar ese video corto (pantalla del iPhone + el videobeam/PC cambiando).

---

## 9. Otras tiendas (estado al pasar a la Mac)

- **Google Play** (cuenta personal creada después de nov. 2023 → prueba cerrada con **12 probadores
  durante 14 días** antes de producción):
  - Ficha, gráfico y capturas: `docs/play/`. App bundle: `./gradlew :androidApp:bundlePlayRelease`
    (variante `play`, sin actualizador propio).
  - Al crear la app en Play Console elegir **"usar mi propia clave de firma"** (la clave está en
    `~/.config/proyecta` del PC, fuera del repositorio) y subir con PEPK.
  - Falta: crear la app en Play Console, juntar 12 Gmail de probadores, subir el `.aab`.
- **Microsoft Store** (gratis para personas; Microsoft vuelve a firmar el paquete, así Smart App Control
  de Windows deja de bloquear Proyecta):
  - `./gradlew :desktopApp:packageMsix` en Windows (usa `makeappx` del NuGet Microsoft.Windows.SDK.BuildTools).
  - Falta: crear la cuenta en Partner Center, reservar el nombre y copiar *Product identity*
    (`Package/Identity/Name`, `Publisher`, `PublisherDisplayName`) a `msix.*` en `gradle.properties`
    (ahora dicen PENDIENTE), volver a generar el MSIX y enviarlo.
