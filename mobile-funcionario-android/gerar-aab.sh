#!/usr/bin/env bash
# ============================================================
#  Gera o Android App Bundle (AAB) assinado para a Play Store
#  Uso: ./gerar-aab.sh
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

PROPS="app/version.properties"
KEYSTORE_PROPS="keystore.properties"
LOCAL_PROPERTIES="local.properties"
AAB_DIR="app/build/outputs/bundle/release"

if [[ ! -f "$PROPS" ]]; then
  echo "ERRO: arquivo de versão não encontrado: $PROPS"
  exit 1
fi

if [[ ! -x "./gradlew" ]]; then
  chmod +x ./gradlew || true
fi

if [[ ! -x "./gradlew" ]]; then
  echo "ERRO: gradlew não encontrado ou sem permissão de execução."
  exit 1
fi

if [[ ! -f "$KEYSTORE_PROPS" ]]; then
  echo "ERRO: $KEYSTORE_PROPS não encontrado."
  echo "Crie o arquivo com storeFile, storePassword, keyAlias e keyPassword."
  exit 1
fi

STORE_FILE="$(grep -E '^storeFile=' "$KEYSTORE_PROPS" | cut -d'=' -f2- || true)"
STORE_PASSWORD="$(grep -E '^storePassword=' "$KEYSTORE_PROPS" | cut -d'=' -f2- || true)"
KEY_ALIAS="$(grep -E '^keyAlias=' "$KEYSTORE_PROPS" | cut -d'=' -f2- || true)"
KEY_PASSWORD="$(grep -E '^keyPassword=' "$KEYSTORE_PROPS" | cut -d'=' -f2- || true)"

if [[ -z "$STORE_FILE" || -z "$STORE_PASSWORD" || -z "$KEY_ALIAS" || -z "$KEY_PASSWORD" ]]; then
  echo "ERRO: $KEYSTORE_PROPS incompleto. Campos obrigatórios:"
  echo "- storeFile"
  echo "- storePassword"
  echo "- keyAlias"
  echo "- keyPassword"
  exit 1
fi

KEYSTORE_PATH="app/$STORE_FILE"
if [[ ! -f "$KEYSTORE_PATH" ]]; then
  echo "ERRO: keystore não encontrado em: $KEYSTORE_PATH"
  exit 1
fi

SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK_DIR" && -f "$LOCAL_PROPERTIES" ]]; then
  sdk_from_local="$(grep -E '^sdk\.dir=' "$LOCAL_PROPERTIES" | head -n1 | cut -d'=' -f2- || true)"
  if [[ -n "$sdk_from_local" ]]; then
    SDK_DIR="${sdk_from_local//\\/}"
  fi
fi
if [[ -z "$SDK_DIR" ]]; then
  for candidate in "$HOME/Android/Sdk" "$HOME/Android/sdk" "/opt/android-sdk" "/usr/lib/android-sdk"; do
    if [[ -d "$candidate" ]]; then
      SDK_DIR="$candidate"
      break
    fi
  done
fi
if [[ -z "$SDK_DIR" || ! -d "$SDK_DIR" ]]; then
  echo "ERRO: Android SDK não encontrado."
  echo "Defina ANDROID_HOME/ANDROID_SDK_ROOT ou ajuste $LOCAL_PROPERTIES"
  exit 1
fi

if [[ ! -f "$LOCAL_PROPERTIES" ]]; then
  printf 'sdk.dir=%s\n' "$SDK_DIR" > "$LOCAL_PROPERTIES"
fi

CURRENT_CODE="$(grep 'VERSION_CODE' "$PROPS" | cut -d= -f2)"
MAJOR="$(grep 'VERSION_MAJOR' "$PROPS" | cut -d= -f2)"
MINOR="$(grep 'VERSION_MINOR' "$PROPS" | cut -d= -f2)"
PATCH="$(grep 'VERSION_PATCH' "$PROPS" | cut -d= -f2)"
CURRENT_VERSION="$MAJOR.$MINOR.$PATCH"

echo ""
echo "=========================================="
echo "  RM Facilities — Gerar AAB (Play Store)  "
echo "=========================================="
echo "Versão atual (antes do build): $CURRENT_VERSION (code $CURRENT_CODE)"
echo "SDK: $SDK_DIR"
echo "Diretório: $SCRIPT_DIR"
echo ""

echo "▶ Executando bundleRelease..."
if ! ./gradlew --no-daemon --stacktrace bundleRelease; then
  echo "Falha no bundleRelease. Tentando novamente com --info..."
  ./gradlew --no-daemon --stacktrace --info bundleRelease
fi

# O build.gradle incrementa versão no build, então relê os valores reais.
REAL_CODE="$(grep 'VERSION_CODE' "$PROPS" | cut -d= -f2)"
REAL_MAJOR="$(grep 'VERSION_MAJOR' "$PROPS" | cut -d= -f2)"
REAL_MINOR="$(grep 'VERSION_MINOR' "$PROPS" | cut -d= -f2)"
REAL_PATCH="$(grep 'VERSION_PATCH' "$PROPS" | cut -d= -f2)"
REAL_VERSION="$REAL_MAJOR.$REAL_MINOR.$REAL_PATCH"

EXPECTED_AAB="$AAB_DIR/rmfuncionario-release-v${REAL_VERSION}-${REAL_CODE}.aab"
AAB_PATH=""

if [[ -f "$EXPECTED_AAB" ]]; then
  AAB_PATH="$EXPECTED_AAB"
fi

if [[ -z "$AAB_PATH" ]]; then
  AAB_PATH="$(find "$AAB_DIR" -maxdepth 1 -type f -name '*.aab' 2>/dev/null | sort -r | head -1 || true)"
fi

if [[ -z "$AAB_PATH" || ! -f "$AAB_PATH" ]]; then
  echo "ERRO: AAB não encontrado após o build. Pasta verificada: $AAB_DIR"
  exit 1
fi

SIZE="$(du -sh "$AAB_PATH" | cut -f1)"
ABS_AAB="$(cd "$(dirname "$AAB_PATH")" && pwd)/$(basename "$AAB_PATH")"

echo ""
echo "=========================================="
echo "  ✓ AAB gerado com sucesso!"
echo "  Arquivo : $AAB_PATH"
echo "  Tamanho : $SIZE"
echo "  Versão  : $REAL_VERSION (code $REAL_CODE)"
echo "=========================================="
echo ""
echo "Próximos passos:"
echo "  1. Acesse https://play.google.com/console"
echo "  2. Vá em Produção (ou Teste interno) > Criar nova versão"
echo "  3. Faça upload do arquivo: $ABS_AAB"
echo ""
