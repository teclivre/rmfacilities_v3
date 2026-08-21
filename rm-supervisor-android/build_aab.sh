#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KEYSTORE_PROPS="$PROJECT_DIR/keystore.properties"
LOCAL_PROPERTIES="$PROJECT_DIR/local.properties"
PROPS="$PROJECT_DIR/app/version.properties"
AAB_DIR="$PROJECT_DIR/app/build/outputs/bundle/release"

if [[ ! -x "$PROJECT_DIR/gradlew" ]]; then
  chmod +x "$PROJECT_DIR/gradlew" || true
fi

if [[ ! -x "$PROJECT_DIR/gradlew" ]]; then
  echo "Erro: gradlew não encontrado ou sem permissão de execução em $PROJECT_DIR"
  exit 1
fi

if [[ ! -f "$PROPS" ]]; then
  echo "Erro: version.properties não encontrado em: $PROPS"
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

if ! command -v java >/dev/null 2>&1; then
  echo "Erro: Java não encontrado no PATH. Instale JDK 17+ e tente novamente."
  exit 1
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "Aviso: JAVA_HOME não está definido. O Gradle pode usar o Java do sistema."
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

if [[ -z "$SDK_DIR" ]]; then
  echo "Erro: ANDROID_SDK_ROOT/ANDROID_HOME não definido."
  echo "Defina uma dessas variáveis com o caminho do Android SDK e tente novamente."
  echo "Exemplo: export ANDROID_SDK_ROOT=\"$HOME/Android/Sdk\""
  exit 1
fi

if [[ ! -d "$SDK_DIR" ]]; then
  echo "Erro: Android SDK não encontrado em: $SDK_DIR"
  exit 1
fi

if [[ ! -f "$LOCAL_PROPERTIES" ]]; then
  echo "Criando local.properties com sdk.dir..."
  printf 'sdk.dir=%s\n' "$SDK_DIR" > "$LOCAL_PROPERTIES"
fi

run_gradle_task() {
  local task="$1"
  if ! "$PROJECT_DIR/gradlew" --no-daemon --stacktrace "$task"; then
    echo "Falha ao executar '$task'. Tentando novamente com --no-daemon --info..."
    "$PROJECT_DIR/gradlew" --no-daemon --stacktrace --info "$task"
  fi
}

echo "Limpando build anterior..."
run_gradle_task clean

echo "Gerando AAB release assinado..."
run_gradle_task bundleRelease

# O build.gradle.kts incrementa versão durante build; relê valores finais.
real_code="$(grep 'VERSION_CODE' "$PROPS" | cut -d'=' -f2 || true)"
real_major="$(grep 'VERSION_MAJOR' "$PROPS" | cut -d'=' -f2 || true)"
real_minor="$(grep 'VERSION_MINOR' "$PROPS" | cut -d'=' -f2 || true)"
real_patch="$(grep 'VERSION_PATCH' "$PROPS" | cut -d'=' -f2 || true)"
real_version="${real_major}.${real_minor}.${real_patch}"

expected_aab="$AAB_DIR/rmsupervisor-release-v${real_version}-${real_code}.aab"
aab_path=""

if [[ -f "$expected_aab" ]]; then
  aab_path="$expected_aab"
fi

if [[ -z "$aab_path" ]]; then
  aab_path="$(find "$AAB_DIR" -maxdepth 1 -type f -name '*.aab' 2>/dev/null | sort -r | head -1 || true)"
fi

if [[ -n "$aab_path" && -f "$aab_path" ]]; then
  size="$(du -sh "$aab_path" | cut -f1)"
  echo "AAB gerado com sucesso: $aab_path"
  echo "Versão final: $real_version (code $real_code)"
  echo "Tamanho: $size"
else
  echo "Build concluído, mas AAB não encontrado em: $AAB_DIR"
  exit 1
fi
