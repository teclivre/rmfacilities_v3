#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"

if [[ ! -x "$PROJECT_DIR/gradlew" ]]; then
  echo "Erro: gradlew não encontrado ou sem permissão de execução em $PROJECT_DIR"
  exit 1
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "Aviso: JAVA_HOME não está definido. O Gradle pode usar o Java do sistema."
fi

if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" ]]; then
  echo "Aviso: ANDROID_HOME/ANDROID_SDK_ROOT não definido. Garanta que o SDK esteja configurado no Android Studio."
fi

echo "Limpando build anterior..."
"$PROJECT_DIR/gradlew" clean

echo "Gerando APK debug..."
"$PROJECT_DIR/gradlew" assembleDebug

if [[ -f "$APK_PATH" ]]; then
  echo "APK gerado com sucesso: $APK_PATH"
else
  echo "Build concluído, mas APK não encontrado em: $APK_PATH"
  exit 1
fi
