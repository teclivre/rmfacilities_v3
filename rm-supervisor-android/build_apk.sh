#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
LOCAL_PROPERTIES="$PROJECT_DIR/local.properties"

if [[ ! -x "$PROJECT_DIR/gradlew" ]]; then
  echo "Erro: gradlew não encontrado ou sem permissão de execução em $PROJECT_DIR"
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
    # local.properties can contain escaped slashes (sdk.dir=/home/user/Android/Sdk)
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

echo "Limpando build anterior..."
"$PROJECT_DIR/gradlew" clean

echo "Gerando APK debug..."
"$PROJECT_DIR/gradlew" assembleDebug

if [[ -f "$APK_PATH" ]]; then
  echo "APK gerado com sucesso: $APK_PATH"
  exit 0
fi

echo "Caminho padrão não encontrado. Procurando APK no diretório de outputs..."
mapfile -t APK_FILES < <(find "$PROJECT_DIR/app/build/outputs/apk" -type f -name '*.apk' | sort)

if [[ ${#APK_FILES[@]} -gt 0 ]]; then
  echo "APK(s) encontrado(s):"
  for apk in "${APK_FILES[@]}"; do
    echo "- $apk"
  done
  exit 0
fi

echo "Build finalizado, mas nenhum APK foi encontrado em app/build/outputs/apk"
echo "Dica: rode manualmente './gradlew assembleDebug --stacktrace' para ver detalhes."
exit 1
