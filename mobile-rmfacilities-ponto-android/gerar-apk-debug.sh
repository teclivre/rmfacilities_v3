#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"
./gradlew --no-daemon assembleDebug
APK="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' | sort | tail -n 1)"
if [[ -z "$APK" ]]; then
  echo "Erro: APK debug não encontrado."
  exit 1
fi
echo "APK RMFacilities Ponto gerado: $SCRIPT_DIR/$APK"
