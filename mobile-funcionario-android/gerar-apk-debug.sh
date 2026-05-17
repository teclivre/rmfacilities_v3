#!/usr/bin/env bash
# ============================================================
#  Gera o APK de DEBUG para testes no dispositivo/emulador
#  NÃO usa keystore — instalação direta via adb ou arquivo
#  Uso: ./gerar-apk-debug.sh
# ============================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"

# Lê versão do version.properties
PROPS="$SCRIPT_DIR/app/version.properties"
if [ -f "$PROPS" ]; then
    MAJOR=$(grep "^VERSION_MAJOR" "$PROPS" | cut -d= -f2)
    MINOR=$(grep "^VERSION_MINOR" "$PROPS" | cut -d= -f2)
    PATCH=$(grep "^VERSION_PATCH" "$PROPS" | cut -d= -f2)
    VCODE=$(grep "^VERSION_CODE"  "$PROPS" | cut -d= -f2)
    VERSION_NAME="${MAJOR}.${MINOR}.${PATCH}"
else
    VERSION_NAME="debug"
    VCODE="?"
fi

APK_DIR="app/build/outputs/apk/debug"

echo ""
echo "============================================"
echo "  RM Facilities — APK DEBUG (para testes)  "
echo "============================================"
echo ""
echo "SDK     : $ANDROID_HOME"
echo "Dir     : $SCRIPT_DIR"
echo "Versão  : $VERSION_NAME (code $VCODE)"
echo ""

echo "▶ Limpando build anterior..."
./gradlew clean

echo ""
echo "▶ Gerando APK debug..."
./gradlew assembleDebug

echo ""
APK_FINAL=$(find "$APK_DIR" -name "*.apk" 2>/dev/null | head -1)
if [ -n "$APK_FINAL" ] && [ -f "$APK_FINAL" ]; then
    SIZE=$(du -sh "$APK_FINAL" | cut -f1)
    APK_NAME=$(basename "$APK_FINAL")
    echo "✅ APK gerado com sucesso!"
    echo "   Arquivo : $SCRIPT_DIR/$APK_FINAL"
    echo "   Tamanho : $SIZE"
    echo ""
    echo "── Opções de instalação ──────────────────────"
    echo "  • Via adb (cabo USB / wireless):"
    echo "    adb install -r \"$SCRIPT_DIR/$APK_FINAL\""
    echo ""
    echo "  • Copiar para o Desktop:"
    echo "    cp \"$SCRIPT_DIR/$APK_FINAL\" ~/Desktop/$APK_NAME"
    echo "──────────────────────────────────────────────"
else
    echo "❌ APK não encontrado. Verifique os erros acima."
    exit 1
fi
