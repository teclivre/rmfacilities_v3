#!/bin/bash
set -e

export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/tools:$PATH"

PROJ_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJ_DIR"

echo "▶ Limpando build anterior..."
./gradlew clean

echo "▶ Compilando APK release..."
./gradlew assembleRelease

APK="$PROJ_DIR/app/build/outputs/apk/release/app-release.apk"

if [ -f "$APK" ]; then
    echo ""
    echo "✅ APK gerado com sucesso:"
    echo "   $APK"
    echo ""
    # Se tiver celular conectado via USB, instala automaticamente
    if "$ANDROID_HOME/platform-tools/adb" devices | grep -q "device$"; then
        echo "📱 Celular detectado. Instalando..."
        "$ANDROID_HOME/platform-tools/adb" install -r "$APK"
        echo "✅ Instalado no celular!"
    else
        echo "ℹ️  Nenhum celular conectado via USB. Instale o APK manualmente."
    fi
else
    echo "❌ APK não encontrado. Verifique os erros acima."
    exit 1
fi
