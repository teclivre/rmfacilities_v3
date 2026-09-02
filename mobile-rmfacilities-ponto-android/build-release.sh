#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"
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
