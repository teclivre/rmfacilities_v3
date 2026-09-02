#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK_DIR" && -f local.properties ]]; then
  SDK_DIR="$(grep -E '^sdk\.dir=' local.properties | head -n1 | cut -d'=' -f2- | sed 's/\\\\//g')"
fi
if [[ -z "$SDK_DIR" ]]; then
  for candidate in "$HOME/Android/Sdk" "$HOME/Android/sdk" "/opt/android-sdk" "/usr/lib/android-sdk"; do
    if [[ -d "$candidate" ]]; then SDK_DIR="$candidate"; break; fi
  done
fi
if [[ -z "$SDK_DIR" || ! -d "$SDK_DIR" ]]; then
  echo "Erro: Android SDK não encontrado. Defina ANDROID_HOME/ANDROID_SDK_ROOT ou crie local.properties com sdk.dir=..."
  exit 1
fi
printf 'sdk.dir=%s\n' "$SDK_DIR" > local.properties

./gradlew --no-daemon assembleRelease
APK="$(find app/build/outputs/apk/release -maxdepth 1 -type f -name '*.apk' | sort | tail -n 1)"
if [[ -z "$APK" ]]; then
  echo "Erro: APK release não encontrado."
  exit 1
fi
echo "APK RMFacilities Ponto gerado: $SCRIPT_DIR/$APK"
if [[ -x "${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb" ]] && "${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb" devices | grep -q $'\tdevice$'; then
  "${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb" install -r "$APK"
  echo "RMFacilities Ponto instalado no dispositivo conectado."
fi
