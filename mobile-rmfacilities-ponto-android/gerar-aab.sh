#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [[ ! -f keystore.properties ]]; then
  echo "Erro: crie keystore.properties a partir de keystore.properties.example para gerar o AAB assinado."
  exit 1
fi

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
  echo "Erro: Android SDK não encontrado."
  exit 1
fi
printf 'sdk.dir=%s\n' "$SDK_DIR" > local.properties

./gradlew --no-daemon bundleRelease
AAB="$(find app/build/outputs/bundle/release -maxdepth 1 -type f -name '*.aab' | sort | tail -n 1)"
if [[ -z "$AAB" ]]; then
  echo "Erro: AAB release não encontrado."
  exit 1
fi
echo "AAB RMFacilities Ponto pronto para a Play Store: $SCRIPT_DIR/$AAB"