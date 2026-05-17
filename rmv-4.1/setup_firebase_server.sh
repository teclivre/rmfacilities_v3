#!/bin/bash
# =============================================================================
# setup_firebase_server.sh
# Configura firebase-admin no servidor de produção
# Uso: ./setup_firebase_server.sh /caminho/para/serviceAccountKey.json
# =============================================================================

set -e

KEY_FILE="${1:-}"

if [[ -z "$KEY_FILE" || ! -f "$KEY_FILE" ]]; then
  echo "Uso: $0 /caminho/para/serviceAccountKey.json"
  echo ""
  echo "Como obter o arquivo:"
  echo "  1. Acesse https://console.firebase.google.com"
  echo "  2. Selecione seu projeto → Configurações do projeto"
  echo "  3. Aba 'Contas de serviço' → 'Gerar nova chave privada'"
  echo "  4. Salve o JSON baixado e passe o caminho aqui."
  exit 1
fi

DEST="/etc/rmfacilities/firebase_service_account.json"
ENV_FILE="/etc/rmfacilities/env.conf"

echo "==> Criando diretório de configuração..."
mkdir -p /etc/rmfacilities

echo "==> Copiando chave Firebase para $DEST ..."
cp "$KEY_FILE" "$DEST"
chmod 600 "$DEST"

echo "==> Escrevendo variável de ambiente em $ENV_FILE ..."
# Remove linha antiga se existir
if [[ -f "$ENV_FILE" ]]; then
  sed -i '/^FIREBASE_CREDENTIALS_JSON=/d' "$ENV_FILE"
fi
echo "FIREBASE_CREDENTIALS_JSON=$DEST" >> "$ENV_FILE"

echo ""
echo "==> Instalando firebase-admin..."
pip install "firebase-admin>=6.3.0"

echo ""
echo "================================================================"
echo "  CONCLUÍDO!"
echo "================================================================"
echo ""
echo "Adicione ao seu ambiente de execução (gunicorn/systemd/Railway):"
echo ""
echo "  FIREBASE_CREDENTIALS_JSON=$DEST"
echo ""
echo "Se usar Railway/Render/Heroku, cole o CONTEÚDO do JSON na"
echo "variável de ambiente FIREBASE_CREDENTIALS_JSON (string JSON)."
echo ""
echo "Depois reinicie o servidor: sudo systemctl restart rmfacilities"
echo "================================================================"
