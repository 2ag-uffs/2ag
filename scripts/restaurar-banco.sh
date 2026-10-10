#!/usr/bin/env bash
# restauracao do banco do 2ag a partir de um backup (RNF07)
#
# backup que ninguem nunca restaurou nao e backup, e so um arquivo. este
# script serve tanto pro teste de restauracao quanto pro dia ruim
#
# uso, na pasta do compose (o pg_restore roda dentro do container do banco):
#   BACKUP_AGE_KEY=/caminho/da/chave-privada.txt ./scripts/restaurar-banco.sh backups/doisag-2026-09-14-0300.dump.age [banco-de-destino]
#
# com o postgres instalado na propria maquina, sem compose:
#   BACKUP_VIA_COMPOSE=false DATABASE_PASSWORD=... BACKUP_AGE_KEY=... ./scripts/restaurar-banco.sh arquivo [banco-de-destino]
#
# o script pede o nome do banco digitado por extenso antes de mexer em qualquer coisa.
# sem terminal (cron, ci) a confirmacao vem na variavel CONFIRMAR_RESTAURACAO=nome-do-banco

set -euo pipefail

# le uma variavel do .env do compose, q eh onde o servidor guarda a configuracao
fromEnvFile() {
    grep "^$1=" .env 2>/dev/null | cut -d= -f2- || true
}

ARQUIVO="${1:-}"
VIA_COMPOSE="${BACKUP_VIA_COMPOSE:-true}"
BANCO="${2:-${DATABASE_NAME:-$(fromEnvFile POSTGRES_DB)}}"
BANCO="${BANCO:-doisag}"
USUARIO="${DATABASE_USER:-$(fromEnvFile POSTGRES_USER)}"
USUARIO="${USUARIO:-admindoisag}"
SERVIDOR="${DATABASE_HOST:-localhost}"
PORTA="${DATABASE_PORT:-5432}"
CHAVE_PRIVADA="${BACKUP_AGE_KEY:-}"

if [ -z "$ARQUIVO" ] || [ ! -f "$ARQUIVO" ]; then
    echo "informe o arquivo de backup: ./scripts/restaurar-banco.sh caminho/do/backup.dump.age [banco]" >&2
    exit 1
fi

if [ "$VIA_COMPOSE" != "true" ] && [ -z "${DATABASE_PASSWORD:-}" ]; then
    echo "defina DATABASE_PASSWORD antes de rodar (ou rode pelo compose, sem BACKUP_VIA_COMPOSE=false)" >&2
    exit 1
fi

# backup cifrado pede a chave privada do age, q fica fora do servidor
case "$ARQUIVO" in
    *.age)
        if [ -z "$CHAVE_PRIVADA" ] || [ ! -f "$CHAVE_PRIVADA" ]; then
            echo "o backup esta cifrado: aponte BACKUP_AGE_KEY pro arquivo da chave privada do age" >&2
            exit 1
        fi
        ;;
esac

echo "isto apaga e recria os dados do banco '$BANCO'"
if [ -n "${CONFIRMAR_RESTAURACAO:-}" ]; then
    CONFIRMACAO="$CONFIRMAR_RESTAURACAO"
elif [ -t 0 ]; then
    read -r -p "digite o nome do banco para confirmar: " CONFIRMACAO
else
    echo "sem terminal, confirme com CONFIRMAR_RESTAURACAO=$BANCO" >&2
    exit 1
fi
if [ "$CONFIRMACAO" != "$BANCO" ]; then
    echo "nome diferente, nada foi feito"
    exit 1
fi

# o arquivo em claro nunca toca o disco: o age abre e manda direto pro pg_restore
abreOBackup() {
    case "$ARQUIVO" in
        *.age) age -d -i "$CHAVE_PRIVADA" "$ARQUIVO" ;;
        *) cat "$ARQUIVO" ;;
    esac
}

# --clean recria o que ja existe e --if-exists evita erro no banco vazio
if [ "$VIA_COMPOSE" = "true" ]; then
    # o banco de destino precisa existir: o do teste de restauracao eh criado aqui
    if ! docker compose exec -T banco psql --username "$USUARIO" --dbname postgres -tAc \
            "select 1 from pg_database where datname = '$BANCO'" | grep -q 1; then
        docker compose exec -T banco createdb --username "$USUARIO" "$BANCO"
    fi
    abreOBackup | docker compose exec -T banco pg_restore --username "$USUARIO" --dbname "$BANCO" \
        --clean --if-exists --no-owner
else
    abreOBackup | PGPASSWORD="$DATABASE_PASSWORD" pg_restore --host "$SERVIDOR" --port "$PORTA" \
        --username "$USUARIO" --dbname "$BANCO" --clean --if-exists --no-owner
fi

echo "banco '$BANCO' restaurado a partir de $ARQUIVO"
echo "suba a api e confira o /api/health e uma tela com dado clinico antes de dar o teste por bom"
