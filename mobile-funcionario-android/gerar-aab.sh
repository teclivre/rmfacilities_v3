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
if [ ! -f "app/rmfacilities-release.keystore" ]; then
  echo "ERRO: Keystore não encontrado em app/rmfacilities-release.keystore"
  exit 1
fi

# Lê a senha em runtime (não fica salva no histórico do terminal)
read -rsp "🔑 Senha do keystore (rmfacilities-release.keystore): " KS_PASS
echo ""
read -rsp "🔑 Senha da chave (Key password, mesma se não souber diferença): " KEY_PASS
echo ""
echo ""

echo "▶ Executando bundleRelease..."
./gradlew bundleRelease \
  -Pandroid.injected.signing.store.file="$(pwd)/app/rmfacilities-release.keystore" \
  -Pandroid.injected.signing.store.password="$KS_PASS" \
  -Pandroid.injected.signing.key.alias="$(grep keyAlias keystore.properties | cut -d= -f2)" \
  -Pandroid.injected.signing.key.password="$KEY_PASS"

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
