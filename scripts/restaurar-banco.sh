#!/usr/bin/env bash
# restauracao do banco do 2ag a partir de um backup (RNF07)
#
# backup que ninguem nunca restaurou nao e backup, e so um arquivo. este
# script serve tanto pro teste de restauracao quanto pro dia ruim
#
# uso:
#   DATABASE_PASSWORD=... ./scripts/restaurar-banco.sh backups/doisag-2026-09-14-0300.dump [banco-de-destino]

set -euo pipefail

ARQUIVO="${1:-}"
BANCO="${2:-${DATABASE_NAME:-doisag}}"
USUARIO="${DATABASE_USER:-admindoisag}"
SERVIDOR="${DATABASE_HOST:-localhost}"
PORTA="${DATABASE_PORT:-5432}"

if [ -z "$ARQUIVO" ] || [ ! -f "$ARQUIVO" ]; then
    echo "informe o arquivo de backup: ./scripts/restaurar-banco.sh caminho/do/backup.dump [banco]" >&2
    exit 1
fi

if [ -z "${DATABASE_PASSWORD:-}" ]; then
    echo "defina DATABASE_PASSWORD antes de rodar" >&2
    exit 1
fi

echo "isto apaga e recria os dados do banco '$BANCO' em $SERVIDOR:$PORTA"
read -r -p "digite o nome do banco para confirmar: " CONFIRMACAO
if [ "$CONFIRMACAO" != "$BANCO" ]; then
    echo "nome diferente, nada foi feito"
    exit 1
fi

# --clean recria o que ja existe e --if-exists evita erro no banco vazio
PGPASSWORD="$DATABASE_PASSWORD" pg_restore \
    --host "$SERVIDOR" \
    --port "$PORTA" \
    --username "$USUARIO" \
    --dbname "$BANCO" \
    --clean \
    --if-exists \
    --no-owner \
    "$ARQUIVO"

echo "banco '$BANCO' restaurado a partir de $ARQUIVO"
echo "suba a api e confira o /api/health e uma tela com dado clinico antes de dar o teste por bom"
