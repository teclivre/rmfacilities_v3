#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"

if [[ ! -f "$APK_PATH" ]]; then
  echo "APK não encontrado em: $APK_PATH"
  echo "Execute antes: ./build_apk.sh"
  exit 1
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "ADB não encontrado no PATH."
  echo "APK já gerado em: $APK_PATH"
  exit 0
fi

if [[ -z "$(adb devices | awk 'NR>1 && $2=="device" {print $1}')" ]]; then
  echo "Nenhum dispositivo Android conectado/autorizado."
  echo "APK pronto para instalação manual: $APK_PATH"
  exit 0
fi

echo "Instalando APK..."
adb install -r "$APK_PATH"
echo "Instalação concluída."
