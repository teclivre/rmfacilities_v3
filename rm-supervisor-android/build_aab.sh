#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AAB_PATH="$PROJECT_DIR/app/build/outputs/bundle/release/app-release.aab"
KEYSTORE_PROPS="$PROJECT_DIR/keystore.properties"

if [[ ! -x "$PROJECT_DIR/gradlew" ]]; then
  echo "Erro: gradlew não encontrado ou sem permissão de execução em $PROJECT_DIR"
  exit 1
fi

if [[ ! -f "$KEYSTORE_PROPS" ]]; then
  echo "Erro: arquivo keystore.properties não encontrado em: $KEYSTORE_PROPS"
  echo "Copie keystore.properties.example para keystore.properties e preencha os dados."
  exit 1
fi

store_file="$(grep -E '^storeFile=' "$KEYSTORE_PROPS" | cut -d'=' -f2- || true)"
store_password="$(grep -E '^storePassword=' "$KEYSTORE_PROPS" | cut -d'=' -f2- || true)"
key_alias="$(grep -E '^keyAlias=' "$KEYSTORE_PROPS" | cut -d'=' -f2- || true)"
key_password="$(grep -E '^keyPassword=' "$KEYSTORE_PROPS" | cut -d'=' -f2- || true)"

if [[ -z "$store_file" || -z "$store_password" || -z "$key_alias" || -z "$key_password" ]]; then
  echo "Erro: keystore.properties incompleto. Campos obrigatórios:"
  echo "- storeFile"
  echo "- storePassword"
  echo "- keyAlias"
  echo "- keyPassword"
  exit 1
fi

if [[ "$store_file" = /* ]]; then
  keystore_path="$store_file"
else
  keystore_path="$PROJECT_DIR/$store_file"
fi

if [[ ! -f "$keystore_path" ]]; then
  echo "Erro: keystore não encontrado em: $keystore_path"
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

echo "Gerando AAB release assinado..."
"$PROJECT_DIR/gradlew" bundleRelease

if [[ -f "$AAB_PATH" ]]; then
  echo "AAB gerado com sucesso: $AAB_PATH"
else
  echo "Build concluído, mas AAB não encontrado em: $AAB_PATH"
  exit 1
fi
