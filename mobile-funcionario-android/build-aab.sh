#!/bin/bash
# ============================================================
# build-aab.sh — Gera AAB release pronto para a Google Play
# Uso: ./build-aab.sh
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

PROPS="app/version.properties"

# --- Lê versão atual ---
CURRENT_CODE=$(grep "VERSION_CODE" "$PROPS" | cut -d= -f2)
MAJOR=$(grep "VERSION_MAJOR" "$PROPS" | cut -d= -f2)
MINOR=$(grep "VERSION_MINOR" "$PROPS" | cut -d= -f2)
PATCH=$(grep "VERSION_PATCH" "$PROPS" | cut -d= -f2)
VERSION_NAME="$MAJOR.$MINOR.$PATCH"

echo "========================================"
echo "  RM Funcionário — Build AAB Release"
echo "  Versão: $VERSION_NAME (code $CURRENT_CODE)"
echo "========================================"
echo ""

# --- Confirma antes de buildar ---
read -p "Gerar AAB para a versão $VERSION_NAME (code $CURRENT_CODE)? [S/n] " RESP
RESP="${RESP:-S}"
if [[ "$RESP" != "S" && "$RESP" != "s" ]]; then
    echo "Cancelado."
    exit 0
fi

echo ""
echo "▶ Executando bundleRelease..."
./gradlew bundleRelease

AAB="app/build/outputs/bundle/release/app-release.aab"

if [[ ! -f "$AAB" ]]; then
    echo "❌ AAB não encontrado em: $AAB"
    exit 1
fi

SIZE=$(du -sh "$AAB" | cut -f1)

echo ""
echo "========================================"
echo "  ✅ AAB gerado com sucesso!"
echo "  Arquivo : $AAB"
echo "  Tamanho : $SIZE"
echo "  Versão  : $VERSION_NAME (code $CURRENT_CODE)"
echo "========================================"
echo ""
echo "📤 Próximos passos para publicar na Google Play:"
echo "   1. Acesse: https://play.google.com/console"
echo "   2. App > Produção (ou Teste Interno)"
echo "   3. Criar nova versão > Fazer upload do AAB"
echo "   4. Arquivo: $(realpath "$AAB")"
echo ""
