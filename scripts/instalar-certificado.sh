#!/usr/bin/env bash
# copia o certificado da clinica pra pasta q o nginx do compose le e recarrega o nginx (issue 57)
#
# o certbot chama isso sozinho dps de renovar (--deploy-hook), com RENEWED_LINEAGE apontando
# pra pasta do certificado, tipo /etc/letsencrypt/live/2ag.exemplo.com.br
# tbm serve na mao, com um certificado comprado:
#   ./scripts/instalar-certificado.sh /pasta/com/fullchain.pem/e/privkey.pem
set -euo pipefail

ORIGEM="${1:-${RENEWED_LINEAGE:-}}"
PASTA_DO_COMPOSE="$(cd "$(dirname "$0")/.." && pwd)"

if [ -z "$ORIGEM" ] || [ ! -f "$ORIGEM/fullchain.pem" ] || [ ! -f "$ORIGEM/privkey.pem" ]; then
    echo "informe a pasta com fullchain.pem e privkey.pem: ./scripts/instalar-certificado.sh /etc/letsencrypt/live/seu-dominio" >&2
    exit 1
fi

mkdir -p "$PASTA_DO_COMPOSE/certs"
# o certificado eh publico (o nginx entrega ele a todo mundo), so a chave fica fechada
install -m 644 "$ORIGEM/fullchain.pem" "$PASTA_DO_COMPOSE/certs/fullchain.pem"
install -m 600 "$ORIGEM/privkey.pem" "$PASTA_DO_COMPOSE/certs/privkey.pem"

# o nginx so le o certificado na subida e no reload
docker compose -f "$PASTA_DO_COMPOSE/docker-compose.yml" exec -T web nginx -s reload
echo "certificado instalado em $PASTA_DO_COMPOSE/certs e nginx recarregado"
