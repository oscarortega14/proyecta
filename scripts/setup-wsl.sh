#!/usr/bin/env bash
# Prepara las herramientas de compilación en WSL/Ubuntu, sin sudo (salvo el JDK).
#   Requisito previo:  sudo apt install -y openjdk-17-jdk-headless unzip
# Instala en ~/.local/opt: Gradle (para generar el wrapper) y el SDK de Android.
set -euo pipefail

GRADLE_VERSION=8.10.2
CMDLINE_TOOLS=commandlinetools-linux-11076708_latest.zip
OPT="$HOME/.local/opt"
SDK="$HOME/android-sdk"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

command -v java >/dev/null || { echo "Falta Java: sudo apt install -y openjdk-17-jdk-headless unzip"; exit 1; }
mkdir -p "$OPT" "$SDK"

if [ ! -x "$OPT/gradle-$GRADLE_VERSION/bin/gradle" ]; then
  echo "== Descargando Gradle $GRADLE_VERSION"
  curl -fL -o "$OPT/gradle.zip" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  unzip -q -o "$OPT/gradle.zip" -d "$OPT" && rm "$OPT/gradle.zip"
fi

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "== Descargando Android command-line tools"
  curl -fL -o "$OPT/cmdline.zip" "https://dl.google.com/android/repository/$CMDLINE_TOOLS"
  rm -rf "$SDK/cmdline-tools" && mkdir -p "$SDK/cmdline-tools"
  unzip -q -o "$OPT/cmdline.zip" -d "$SDK/cmdline-tools" && mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm "$OPT/cmdline.zip"
fi

echo "== Instalando plataforma Android 34 y build-tools (te pedirá aceptar las licencias)"
"$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --licenses
"$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" "platforms;android-34" "build-tools;34.0.0"

echo "sdk.dir=$SDK" > "$ROOT/local.properties"
(cd "$ROOT" && "$OPT/gradle-$GRADLE_VERSION/bin/gradle" wrapper --gradle-version "$GRADLE_VERSION" -q)

echo
echo "Listo. Ahora puedes compilar con:  cd $ROOT && ./gradlew test"
