#!/usr/bin/env bash
# ============================================================
#  Gera o APK de DEBUG para testes no dispositivo/emulador
#  NÃO usa keystore — instalação direta via adb ou arquivo
#  A cada execução o build.gradle incrementa VERSION_CODE e VERSION_PATCH
#  (mesmo comportamento do build-aab.sh).
#  Uso: ./gerar-apk-debug.sh
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"

PROPS="app/version.properties"

# --- Lê versão atual (antes do bump) ---
CURRENT_CODE=$(grep "^VERSION_CODE"  "$PROPS" | cut -d= -f2)
MAJOR=$(grep "^VERSION_MAJOR" "$PROPS" | cut -d= -f2)
MINOR=$(grep "^VERSION_MINOR" "$PROPS" | cut -d= -f2)
PATCH=$(grep "^VERSION_PATCH" "$PROPS" | cut -d= -f2)
VERSION_NAME="${MAJOR}.${MINOR}.${PATCH}"

echo ""
echo "============================================"
echo "  RM Facilities — APK DEBUG (para testes)  "
echo "============================================"
echo ""
echo "SDK         : $ANDROID_HOME"
echo "Dir         : $SCRIPT_DIR"
echo "Versão atual: $VERSION_NAME (code $CURRENT_CODE)"
echo "▶ Após o build será incrementada automaticamente pelo build.gradle"
echo ""

echo "▶ Limpando build anterior..."
./gradlew clean

echo ""
echo "▶ Gerando APK debug..."
./gradlew assembleDebug

# Relê version.properties para pegar a versão real após o bump
REAL_CODE=$(grep "^VERSION_CODE"  "$PROPS" | cut -d= -f2)
REAL_MAJOR=$(grep "^VERSION_MAJOR" "$PROPS" | cut -d= -f2)
REAL_MINOR=$(grep "^VERSION_MINOR" "$PROPS" | cut -d= -f2)
REAL_PATCH=$(grep "^VERSION_PATCH" "$PROPS" | cut -d= -f2)
REAL_VERSION="${REAL_MAJOR}.${REAL_MINOR}.${REAL_PATCH}"

APK_DIR="app/build/outputs/apk/debug"

# Procura primeiro pelo APK com nome versionado gerado pelo build.gradle
APK_FINAL=$(find "$APK_DIR" -name "rmfuncionario-debug-v${REAL_VERSION}-${REAL_CODE}.apk" 2>/dev/null | head -1)

# Fallback: qualquer APK na pasta
if [ -z "$APK_FINAL" ]; then
    APK_FINAL=$(find "$APK_DIR" -name "*.apk" 2>/dev/null | head -1)
fi

echo ""
if [ -n "$APK_FINAL" ] && [ -f "$APK_FINAL" ]; then
    SIZE=$(du -sh "$APK_FINAL" | cut -f1)
    APK_NAME=$(basename "$APK_FINAL")
    echo "============================================"
    echo "  ✅ APK gerado com sucesso!"
    echo "  Arquivo : $SCRIPT_DIR/$APK_FINAL"
    echo "  Tamanho : $SIZE"
    echo "  Versão  : $REAL_VERSION (code $REAL_CODE)"
    echo "============================================"
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
