# Selah

Programa para proyectar letras de canciones y la Biblia en la iglesia, con control remoto desde celulares y tablets en la misma red Wi-Fi.

| Módulo | Qué es | Tecnología |
|---|---|---|
| `shared/` | Modelos, protocolo, lector de canciones, búsqueda en la Biblia | Kotlin Multiplatform (JVM + Android + iOS) |
| `desktopApp/` | Ventana del operador + ventana de proyección + servidor para los remotos | Compose Desktop + Ktor (WebSocket) |
| `remoteUi/` | Pantallas del control remoto, compartidas entre Android e iPhone | Compose Multiplatform + Ktor client |
| `androidApp/` | Control remoto para Android 7 o superior (solo la ventana; usa `remoteUi`) | Android |
| `iosApp/` | Control remoto para iPhone/iPad (en construcción: ver [docs/IOS.md](docs/IOS.md)) | SwiftUI + `remoteUi` |

## Cómo funciona

```
 Tablet / celular  ──WebSocket ws://IP:8765/ws──▶  Computador (Selah)  ──▶  Videobeam
   (androidApp)        órdenes: siguiente,              (desktopApp)           (2.º monitor)
                       canción, cita bíblica…
                  ◀── estado actual y biblioteca ──
```

El computador es la única fuente de verdad (`Presenter`). La ventana del operador y todos los remotos mandan las mismas órdenes (`ClientMessage`) y reciben el mismo estado (`ServerMessage.State`), así que siempre están sincronizados.

## Biblioteca

Selah crea la carpeta `Selah` en la carpeta personal del usuario (en Windows: `C:\Users\<usuario>\Selah`).

### Canciones: `Selah/canciones/*.txt`

```
# Título de la canción

Primera estrofa, línea 1
Primera estrofa, línea 2

Coro:
Línea del coro
Línea del coro
```

- La línea con `#` es el título (si falta, se usa el nombre del archivo).
- Cada bloque separado por una línea en blanco es una diapositiva.
- Si la primera línea de un bloque termina en `:` (ej. `Coro:`), es la etiqueta.
- Acepta archivos guardados en UTF-8 o en ANSI (Bloc de notas).

### Biblia: `Selah/biblia/`

Se usa el primer archivo `.txt` o `.json` de la carpeta (orden alfabético).

**Recomendado:** la Reina-Valera 1909 (dominio público) de eBible.org en formato VPL
(`https://ebible.org/Scriptures/spaRV1909_vpl.zip`): copia `spaRV1909_vpl.txt` a la carpeta,
por ejemplo como `Reina-Valera 1909.txt`. Cualquier Biblia VPL de eBible.org con los 66 libros sirve.

También acepta JSON:

```json
{"name": "Reina-Valera 1909", "books": [{"name": "Génesis", "chapters": [["En el principio…", "…"]]}]}
```

o una lista de 66 libros en orden canónico: `[{"abbrev": "gn", "chapters": [[…]]}, …]`.

Ojo con los derechos de autor: la RVR1960, NVI, NTV, etc. tienen licencia. La **Reina-Valera 1909** es de dominio público.

Citas aceptadas: `Juan 3:16`, `jn 3 16`, `Sal 23`, `1 Co 13:4-7`, `Génesis 1`.

### Orden de canción (opcional)

Debajo del título, una línea `Orden:` arma la secuencia repitiendo secciones sin copiar el texto:

```
# Alabad a Jehová
Orden: Inicio, Coro, Pregón 1, Coro, Pregón 2, Coro
```

`Inicio` es lo que va antes de la primera etiqueta. Con esto, "Siguiente" recorre la canción en ese orden.
El botón "+ Orden" del editor la agrega con las secciones existentes.

## Funciones

- **Logo de la iglesia** (tecla `L` o botón "Logo"): pantalla de espera con el logo sobre el fondo. Se elige en
  "Fondos y letra…" con "Usar de logo" (mejor un PNG con fondo transparente).
- **Alertas**: aviso en una franja roja abajo, sin tapar la letra (se desplaza si es largo).
- **Cuenta regresiva**: "Comenzamos en 05:00" en el videobeam y en la vista de escenario.
- **Buscar en la Biblia por frase**: escribe palabras ("amó Dios mundo") en vez de una cita y presiona "Buscar".
- **Atajos de teclado**: → ↓ Espacio siguiente · ← ↑ anterior · `C` coro · `1`–`9` secciones · `B` negro ·
  `L` logo · `N` siguiente de la lista · `Esc` mostrar letra (o salir de un campo de texto) · Av Pág / Re Pág (clickers).
- **Fondos incluidos y animados** (dibujados por el programa, sin archivos ni licencias): Luz celestial (predeterminado),
  Aurora, Bokeh dorado, Atardecer, Noche estrellada, Océano, Calvario y Destellos. Interruptor "Animación".
- **Fondos de video en bucle** (`.mp4`, `.mov`, `.webm`…) en `Selah/fondos`, reproducidos con el VLC incluido
  (licencia de VLC en `vlc/COPYING.txt`). Loops gratis para iglesias: open.life.church, Igniter Media, Hillsong.
  Si transmiten el culto, revisen la licencia de cada video.
- **Fondos y letra** (botón "Fondos y letra…"): fondos de color o degradado incluidos, o tus propias imágenes
  `.jpg`/`.png` en `Selah/fondos` (mejor 1920×1080); oscurecer la imagen, color y tamaño de la letra, sombra.
  Se guarda en `Selah/tema.json`. Fondo propio por canción con la línea `Fondo: archivo.jpg` debajo del título
  (o el botón "Solo esta canción").
- **Editor de canciones** con vista previa en vivo (botones "Editar" y "+ Nueva").
- **Saltos por sección**: botones Coro · Pregón 1 · Puente… en el PC, la tablet y el control web.
- **Lista del culto**: prepara el orden de canciones y lecturas ("+ Lista"); "Siguiente elemento ▶" pasa al próximo.
  Se guarda en `Selah/lista-del-culto.json`.
- **Control remoto web**: `http://IP:8765` en cualquier celular o tablet (Android o iPhone), sin instalar nada.
- **Vista de escenario**: `http://IP:8765/escenario.html` — estrofa actual grande, la siguiente, la hora y
  mensajes del operador ("Repetir coro") que no salen en el videobeam.
- **Letras para transmisión (OBS)**: `http://IP:8765/obs.html` como "Fuente de navegador" (fondo transparente).
  Parámetros: `?pos=bottom|center|top&size=44&bg=1` (bg=1 agrega una franja oscura detrás del texto).

Cualquiera conectado a la misma red puede controlar la proyección: úsalo en la red de la iglesia, no en una red pública.

## Antes se llamaba Proyecta

Hasta la versión 2.4 el programa se llamaba **Proyecta**. Al actualizar no se pierde nada:

- El instalador nuevo reemplaza al viejo (mismo `upgradeUuid`) y los PCs con la 2.4 encuentran la
  actualización solos (GitHub redirige el repositorio viejo al nuevo).
- La primera vez que se abre Selah, la carpeta `Proyecta` del usuario pasa a llamarse `Selah`, con
  todas las canciones, Biblias, fondos, listas y ajustes. Si Windows no deja moverla, se sigue usando la vieja.
- La regla del firewall de la 2.4 sigue sirviendo (no vuelve a pedir permiso).
- La app de Android cambió de paquete (`app.selah.remote`): es una app nueva, hay que instalarla
  una vez y se puede borrar "Proyecta Remoto".
- Para no romper nada entre versiones, la red sigue usando `PROYECTA?` (búsqueda), la cabecera
  `X-Proyecta-Pin` y la firma `proyecta-msi-v1:`; la app y el control web aceptan enlaces
  `selah://` y `proyecta://`.

## Instalar en un PC nuevo (sin pasos manuales)

1. Doble clic en `Selah-<versión>.msi`. Trae Java, el reproductor de video (VLC) y la Biblia Reina-Valera 1909.
2. Abrir Selah y presionar **"Permitir"** en el aviso "Conectar tablets y celulares" (y "Sí" en la ventana de Windows).
   Crea la regla del firewall para la red local (TCP 8765 y UDP 8766). Si se omite, hay un botón "Permitir conexiones".
3. **Importar…** las canciones (archivos `.txt` o un `.zip` hecho con **Exportar…**).

En la tablet o el celular: abrir la app. Encuentra sola al PC en la red (si el router le cambia la IP, lo vuelve a buscar).

## Uso

- Doble monitor: la proyección se abre a pantalla completa en el segundo monitor (el videobeam).
- Teclado y clickers de presentación: **Av Pág / Re Pág** para avanzar o retroceder, **Ctrl+B** pantalla negra.
- La app de la tablet o el celular busca sola a Selah en la red y muestra el nombre del computador; no hay que escribir direcciones.
- No pongas el código del programa en una carpeta llamada `Selah` dentro de la carpeta personal: Windows no distingue mayúsculas y se mezclaría con la biblioteca.

## Compilar

Requisitos: JDK 17 o más nuevo (Gradle descarga solo el Java 21 del programa de escritorio y el 17 del celular). Para Android, además el SDK de Android (`scripts/setup-wsl.sh` lo instala en WSL/Ubuntu).

```bash
./gradlew test                                  # pruebas
SELAH_VPL=/ruta/spaRV1909_vpl.txt ./gradlew :desktopApp:test   # incluye la Biblia real
SELAH_RENDER_DIR=/tmp/out SELAH_BG=/ruta/fondo.png ./gradlew :desktopApp:test --tests '*RenderPreviewTest*'  # PNGs de la proyección
./gradlew :desktopApp:run                       # abrir el programa de escritorio
./gradlew :androidApp:assembleGithubRelease     # APK del control remoto
adb install -r androidApp/build/outputs/apk/github/release/androidApp-github-release.apk
./gradlew :androidApp:bundlePlayRelease         # paquete para Google Play
./gradlew :desktopApp:packageMsi                # instalador para Windows (ejecutar en Windows; descarga WiX solo)
# -> desktopApp/build/compose/binaries/main/msi/Selah-<versión>.msi (incluye Java: no hace falta instalarlo)
```
