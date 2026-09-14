#!/usr/bin/env bash
# backup do banco do 2ag (RNF07)
#
# gera um arquivo por dia e apaga os mais velhos que o prazo de guarda.
# o prontuario tem guarda minima de 20 anos (Lei 13.787/2018), entao o
# backup do dia nunca substitui o do dia anterior: eles se acumulam
#
# uso:
#   DATABASE_PASSWORD=... ./scripts/backup-banco.sh [pasta-de-destino]

set -euo pipefail

DESTINO="${1:-./backups}"
BANCO="${DATABASE_NAME:-doisag}"
USUARIO="${DATABASE_USER:-admindoisag}"
SERVIDOR="${DATABASE_HOST:-localhost}"
PORTA="${DATABASE_PORT:-5432}"
DIAS_DE_GUARDA="${BACKUP_DIAS:-30}"

if [ -z "${DATABASE_PASSWORD:-}" ]; then
    echo "defina DATABASE_PASSWORD antes de rodar" >&2
    exit 1
fi

mkdir -p "$DESTINO"
ARQUIVO="$DESTINO/doisag-$(date +%Y-%m-%d-%H%M).dump"

# o formato custom (-Fc) eh o que o pg_restore le, e ja sai comprimido
PGPASSWORD="$DATABASE_PASSWORD" pg_dump \
    --host "$SERVIDOR" \
    --port "$PORTA" \
    --username "$USUARIO" \
    --format custom \
    --file "$ARQUIVO" \
    "$BANCO"

echo "backup gravado em $ARQUIVO"

# some com o que passou do prazo de guarda desta pasta
find "$DESTINO" -name 'doisag-*.dump' -type f -mtime "+$DIAS_DE_GUARDA" -delete
echo "backups com mais de $DIAS_DE_GUARDA dias foram removidos desta pasta"
