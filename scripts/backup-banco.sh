#!/usr/bin/env bash
# backup do banco do 2ag (RNF07)
#
# gera um arquivo por execucao e apaga os mais velhos que o prazo de guarda.
# o prontuario tem guarda minima de 20 anos (Lei 13.787/2018), entao o
# backup do dia nunca substitui o do dia anterior: eles se acumulam
#
# o arquivo sai cifrado com a chave publica do age (BACKUP_AGE_PUBKEY no .env):
# so quem tem a chave privada, guardada fora do servidor, consegue abrir
#
# uso, na pasta do compose (o pg_dump roda dentro do container do banco):
#   ./scripts/backup-banco.sh [pasta-de-destino]
#
# com o postgres instalado na propria maquina, sem compose:
#   BACKUP_VIA_COMPOSE=false DATABASE_PASSWORD=... ./scripts/backup-banco.sh [pasta-de-destino]
#
# BACKUP_SEM_CIFRA=true grava o .dump em claro, so pra teste no proprio computador

set -euo pipefail

# le uma variavel do .env do compose, q eh onde o servidor guarda a configuracao
# n usa source pq senha com aspas ou cifrao quebraria
fromEnvFile() {
    grep "^$1=" .env 2>/dev/null | cut -d= -f2- || true
}

DESTINO="${1:-./backups}"
VIA_COMPOSE="${BACKUP_VIA_COMPOSE:-true}"
BANCO="${DATABASE_NAME:-$(fromEnvFile POSTGRES_DB)}"
BANCO="${BANCO:-doisag}"
USUARIO="${DATABASE_USER:-$(fromEnvFile POSTGRES_USER)}"
USUARIO="${USUARIO:-admindoisag}"
SERVIDOR="${DATABASE_HOST:-localhost}"
PORTA="${DATABASE_PORT:-5432}"
DIAS_DE_GUARDA="${BACKUP_DIAS:-30}"
CHAVE_PUBLICA="${BACKUP_AGE_PUBKEY:-$(fromEnvFile BACKUP_AGE_PUBKEY)}"
SEM_CIFRA="${BACKUP_SEM_CIFRA:-false}"

# pelo compose o pg_dump roda dentro do container e n precisa de porta nem de senha
if [ "$VIA_COMPOSE" != "true" ] && [ -z "${DATABASE_PASSWORD:-}" ]; then
    echo "defina DATABASE_PASSWORD antes de rodar (ou rode pelo compose, sem BACKUP_VIA_COMPOSE=false)" >&2
    exit 1
fi

# sem a chave publica nenhum backup sai, pra nenhum prontuario ir em claro pro bucket ou pendrive
if [ "$SEM_CIFRA" != "true" ]; then
    if [ -z "$CHAVE_PUBLICA" ]; then
        echo "defina BACKUP_AGE_PUBKEY no .env (BACKUP_SEM_CIFRA=true so pra teste no proprio computador)" >&2
        exit 1
    fi
    if ! command -v age > /dev/null; then
        echo "instale o age (apt install age) pra cifrar o backup" >&2
        exit 1
    fi
fi

# o formato custom (-Fc) eh o que o pg_restore le, e ja sai comprimido
dumpDoBanco() {
    if [ "$VIA_COMPOSE" = "true" ]; then
        # o -T eh obrigatorio: com terminal o compose corrompe o dump q sai pela saida padrao
        docker compose exec -T banco pg_dump --username "$USUARIO" --format custom "$BANCO"
    else
        PGPASSWORD="$DATABASE_PASSWORD" pg_dump --host "$SERVIDOR" --port "$PORTA" \
            --username "$USUARIO" --format custom "$BANCO"
    fi
}

mkdir -p "$DESTINO"
ARQUIVO="$DESTINO/doisag-$(date +%Y-%m-%d-%H%M).dump"
if [ "$SEM_CIFRA" != "true" ]; then
    ARQUIVO="$ARQUIVO.age"
fi

# o backup pela metade fica com outro nome e some se der erro, senao passaria por backup bom
trap 'rm -f "$ARQUIVO.parcial"' EXIT

# o dump vai direto pro age, entao o arquivo em claro nunca toca o disco
if [ "$SEM_CIFRA" = "true" ]; then
    dumpDoBanco > "$ARQUIVO.parcial"
else
    dumpDoBanco | age -r "$CHAVE_PUBLICA" > "$ARQUIVO.parcial"
fi
mv "$ARQUIVO.parcial" "$ARQUIVO"
echo "backup gravado em $ARQUIVO"

# some com o que passou do prazo de guarda desta pasta
find "$DESTINO" -name 'doisag-*.dump*' -type f -mtime "+$DIAS_DE_GUARDA" -delete
echo "backups com mais de $DIAS_DE_GUARDA dias foram removidos desta pasta"
