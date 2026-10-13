#!/usr/bin/env bash
# certificado autoassinado pra rodar o compose no proprio computador ou no ci (issue 57)
# o nginx n sobe sem certificado, e no computador n tem dominio pra pedir um de verdade
# no servidor da clinica o certificado eh de verdade e nunca passa por aqui
#
# uso, na pasta do compose:
#   ./scripts/gerar-certificado-local.sh [nome-do-host]
set -euo pipefail

HOST="${1:-localhost}"
PASTA="certs"

if [ -f "$PASTA/fullchain.pem" ] && [ -f "$PASTA/privkey.pem" ] && [ "${SOBRESCREVER:-false}" != "true" ]; then
    echo "ja existe certificado em $PASTA/, nada foi feito (SOBRESCREVER=true pra trocar)"
    exit 0
fi

mkdir -p "$PASTA"
# se o openssl falhar nada fica pela metade
trap 'rm -f "$PASTA/privkey.pem" "$PASTA/fullchain.pem"; echo "o openssl falhou e nada ficou gravado" >&2' ERR
# MSYS_NO_PATHCONV: o git bash do windows transformaria o /CN= num caminho; no linux a variavel n faz nada
MSYS_NO_PATHCONV=1 openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -days 30 \
    -subj "/CN=$HOST" -addext "subjectAltName=DNS:$HOST" \
    -keyout "$PASTA/privkey.pem" -out "$PASTA/fullchain.pem"
chmod 600 "$PASTA/privkey.pem"

echo "certificado autoassinado pra $HOST em $PASTA/, vale 30 dias"
echo "o navegador vai avisar q ele n eh confiavel: aceite o aviso, mas n instale ele como confiavel"
