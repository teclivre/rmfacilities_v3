#!/usr/bin/env bash
# ============================================================
#  Gera o Android App Bundle (AAB) assinado para a Play Store
#  Uso: ./gerar-aab.sh
# ============================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Android SDK
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"

echo ""
echo "=========================================="
echo "  RM Facilities — Gerar AAB (Play Store)  "
echo "=========================================="
echo ""
echo "SDK: $ANDROID_HOME"
echo "Diretório: $SCRIPT_DIR"
echo ""

# Verifica se keystore existe
if [ ! -f "app/rmfacilities-v2.keystore" ]; then
  echo "ERRO: Keystore não encontrado em app/rmfacilities-v2.keystore"
  exit 1
fi

echo "▶ Executando bundleRelease..."
./gradlew bundleRelease

AAB_PATH="app/build/outputs/bundle/release/app-release.aab"

if [ -f "$AAB_PATH" ]; then
  SIZE=$(du -sh "$AAB_PATH" | cut -f1)
  echo ""
  echo "=========================================="
  echo "  ✓ AAB gerado com sucesso!"
  echo "  Arquivo : $AAB_PATH"
  echo "  Tamanho : $SIZE"
  echo "=========================================="
  echo ""
  echo "Próximos passos:"
  echo "  1. Acesse https://play.google.com/console"
  echo "  2. Vá em Produção (ou Teste interno) > Criar nova versão"
  echo "  3. Faça upload do arquivo acima"
  echo ""
else
  echo "ERRO: AAB não encontrado após o build."
  exit 1
fi
