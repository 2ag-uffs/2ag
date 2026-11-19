#!/usr/bin/env bash
# restauracao do banco do 2ag a partir de um backup (RNF07)
#
# backup que ninguem nunca restaurou nao e backup, e so um arquivo. este
# script serve tanto pro teste de restauracao quanto pro dia ruim
#
# eh tudo ou nada: o backup entra primeiro num banco novo (<banco>_restauracao) e so toma o
# nome do banco de destino qnd entrou inteiro. o q estava no destino n eh apagado, fica
# guardado como <banco>_antes_<data>, e se qualquer passo falhar o destino continua como estava
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

echo "isto troca o banco '$BANCO' inteiro pelo conteudo do backup"
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

# roda um programa do postgres: pelo compose dentro do container do banco, senao o da propria maquina
noBanco() {
    if [ "$VIA_COMPOSE" = "true" ]; then
        docker compose exec -T banco "$@"
    else
        PGPASSWORD="$DATABASE_PASSWORD" PGHOST="$SERVIDOR" PGPORT="$PORTA" "$@"
    fi
}

# liga no banco postgres pq banco com alguem ligado nele n troca de nome
sql() {
    noBanco psql --username "$USUARIO" --dbname postgres --no-psqlrc --quiet -tAc "$1"
}

NOVO="${BANCO}_restauracao"
GUARDADO="${BANCO}_antes_$(date +%Y%m%d_%H%M%S)"

apagaONovo() {
    noBanco dropdb --username "$USUARIO" --if-exists --force "$NOVO" > /dev/null 2>&1 || true
}

JA_EXISTIA=$(sql "select count(*) from pg_database where datname = '$BANCO'")

# o backup entra num banco novo, q some se o script parar antes da troca
# apaga antes tbm pq pode ter sobra de uma restauracao q morreu no meio
apagaONovo
trap apagaONovo EXIT
if ! noBanco createdb --username "$USUARIO" "$NOVO"; then
    echo "nao deu pra criar o banco '$NOVO' e o banco '$BANCO' continua como estava: sem compose o usuario '$USUARIO' precisa poder criar banco" >&2
    exit 1
fi

# sem --clean pq o banco novo nasce vazio, e o primeiro erro para tudo em vez de seguir pela metade
if ! abreOBackup | noBanco pg_restore --username "$USUARIO" --dbname "$NOVO" --exit-on-error --no-owner; then
    echo "o backup nao entrou inteiro e o banco '$BANCO' continua como estava: confira o arquivo e a chave" >&2
    exit 1
fi

# as trocas de nome vao num comando so, q o postgres roda como uma transacao: ou valem as duas ou nenhuma
TROCA="alter database \"$NOVO\" rename to \"$BANCO\""
if [ "$JA_EXISTIA" = "1" ]; then
    TROCA="alter database \"$BANCO\" rename to \"$GUARDADO\"; $TROCA"
fi
if ! sql "$TROCA"; then
    echo "a troca nao aconteceu e o banco '$BANCO' continua como estava: se a api esta ligada nele, pare (docker compose stop api) e rode de novo" >&2
    exit 1
fi
trap - EXIT

echo "banco '$BANCO' restaurado a partir de $ARQUIVO"
if [ "$JA_EXISTIA" = "1" ]; then
    echo "o banco que estava no lugar ficou guardado como '$GUARDADO': apague com dropdb depois de conferir o restaurado"
fi
echo "suba a api e confira o /api/health e uma tela com dado clinico antes de dar o teste por bom"
