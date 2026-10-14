#!/usr/bin/env bash
# sobe a pilha do compose com um .env de mentira e confere o caminho q so existe no docker:
# tls com certificado autoassinado, http redirecionando, healthcheck da api, proxy do nginx,
# fallback da spa, criacao do admin, login com cookie Secure e um POST com cookie
# (o POST eh o unico jeito de pegar PUBLIC_URL errado, pq GET passa sem conferir a origem)
set -euo pipefail

PUBLIC_URL="https://localhost"
ADMIN_EMAIL="admin-ci@email.com"
ADMIN_PASSWORD="SenhaDoCi@2026-teste"
# o certificado eh autoassinado, entao o curl confia nele de proposito
CURL="curl -s --cacert certs/fullchain.pem"

cat > .env <<EOF
POSTGRES_DB=doisag
POSTGRES_USER=admindoisag
POSTGRES_PASSWORD=senha-do-banco-do-ci
JWT_SECRET=chave-de-mentira-do-ci-com-tamanho-suficiente-pra-assinar-0123456789
SEED_DADOS_TESTE=false
SESSION_SECURE_COOKIE=true
ADMIN_EMAIL=$ADMIN_EMAIL
ADMIN_PASSWORD=$ADMIN_PASSWORD
PUBLIC_URL=$PUBLIC_URL
EOF

cleanup() {
    echo "=== log da api ==="
    docker compose logs --no-color api | tail -80 || true
    echo "=== log do web ==="
    docker compose logs --no-color web | tail -20 || true
    docker compose down -v || true
}
trap cleanup EXIT

# o nginx n sobe sem certificado
./scripts/gerar-certificado-local.sh localhost

# o --wait espera os healthchecks: banco, api e o nginx
docker compose up -d --wait --wait-timeout 180

for attempt in $(seq 1 30); do
    if $CURL -f -o /dev/null "$PUBLIC_URL/"; then
        break
    fi
    sleep 2
done

echo "=== http so redireciona pro https ==="
redirect=$(curl -s -o /dev/null -w "%{http_code} %{redirect_url}" http://localhost/entrar)
[ "$redirect" = "301 https://localhost/entrar" ] || { echo "http n redirecionou: $redirect"; exit 1; }
api_by_http=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost/api/auth/login)
[ "$api_by_http" = "301" ] || { echo "a api respondeu por http com $api_by_http"; exit 1; }

echo "=== spa e proxy ==="
$CURL -f "$PUBLIC_URL/" | grep -q "<div id=\"root\"" || { echo "a pagina inicial n veio"; exit 1; }
# fallback da spa: rota do front devolve o index e n 404
$CURL -f "$PUBLIC_URL/entrar" | grep -q "<div id=\"root\"" || { echo "o fallback da spa n funciona"; exit 1; }
$CURL -f "$PUBLIC_URL/api/health" | grep -q "UP" || { echo "a api n responde pelo nginx"; exit 1; }

echo "=== hsts com includeSubDomains, uma vez so ==="
$CURL -D - -o /dev/null "$PUBLIC_URL/" | grep -qi "^strict-transport-security: max-age=31536000; includeSubDomains" \
    || { echo "o hsts do front n veio como esperado"; exit 1; }
hsts_count=$($CURL -D - -o /dev/null "$PUBLIC_URL/api/health" | grep -ci "^strict-transport-security:")
[ "$hsts_count" = "1" ] || { echo "esperava um cabecalho hsts na api, vieram $hsts_count"; exit 1; }

echo "=== cabecalhos de seguranca na pagina, num arquivo do build e na api ==="
asset=$($CURL "$PUBLIC_URL/" | grep -o '/assets/[^"]*\.js' | head -1)
[ -n "$asset" ] || { echo "n achei arquivo js do build na pagina inicial"; exit 1; }
for path in "/" "$asset" "/api/health"; do
    headers=$($CURL -D - -o /dev/null "$PUBLIC_URL$path")
    for header in "content-security-policy: default-src 'self'" "cross-origin-opener-policy: same-origin" \
            "cross-origin-resource-policy: same-origin" "x-frame-options: deny" "x-content-type-options: nosniff"; do
        echo "$headers" | grep -qi "^$header" || { echo "$path saiu sem $header"; exit 1; }
    done
done

echo "=== admin criado na subida ==="
docker compose logs --no-color api | grep -q "conta administrativa criada" || { echo "o admin n foi criado"; exit 1; }

echo "=== login com cookie Secure ==="
login_status=$($CURL -o /tmp/login.json -w "%{http_code}" -c /tmp/cookies.txt -D /tmp/login-headers.txt \
    -H "Content-Type: application/json" -H "Origin: $PUBLIC_URL" \
    -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" \
    "$PUBLIC_URL/api/auth/login")
[ "$login_status" = "200" ] || { echo "login respondeu $login_status: $(cat /tmp/login.json)"; exit 1; }
grep -q "__Host-session" /tmp/cookies.txt || { echo "o login n gravou o cookie __Host-session"; exit 1; }
set_cookie=$(grep -i "^set-cookie: __Host-session=" /tmp/login-headers.txt || true)
echo "$set_cookie" | grep -q "Secure" || { echo "o cookie da sessao saiu sem Secure: $set_cookie"; exit 1; }
echo "$set_cookie" | grep -q "HttpOnly" || { echo "o cookie da sessao saiu sem HttpOnly: $set_cookie"; exit 1; }
echo "$set_cookie" | grep -qi "SameSite=Strict" || { echo "o cookie da sessao saiu sem SameSite=Strict: $set_cookie"; exit 1; }

echo "=== POST com cookie passa pela conferencia de origem ==="
create_status=$($CURL -o /tmp/create.json -w "%{http_code}" -b /tmp/cookies.txt \
    -H "Content-Type: application/json" -H "Origin: $PUBLIC_URL" \
    -d '{"name":"Prescritor do CI","email":"prescritor-ci@email.com","cpf":"52998224725","profession":"Médico","registryType":"CRM","registryNumber":"12345"}' \
    "$PUBLIC_URL/api/admin/prescribers")
[ "$create_status" = "201" ] || { echo "o POST autenticado respondeu $create_status: $(cat /tmp/create.json)"; exit 1; }

echo "=== POST de outra origem eh recusado ==="
other_status=$($CURL -o /dev/null -w "%{http_code}" -b /tmp/cookies.txt \
    -H "Content-Type: application/json" -H "Origin: https://outro-site.exemplo" \
    -d '{"active":true}' \
    "$PUBLIC_URL/api/admin/prescribers/1/active")
[ "$other_status" = "403" ] || { echo "requisicao de outra origem respondeu $other_status em vez de 403"; exit 1; }

echo "=== backup cifrado pelo compose e restauracao num banco separado ==="
age-keygen -o /tmp/chave-do-backup.txt 2> /tmp/chave-publica.txt
PUBKEY=$(grep -o 'age1[a-z0-9]*' /tmp/chave-publica.txt | head -1)
echo "BACKUP_AGE_PUBKEY=$PUBKEY" >> .env
./scripts/backup-banco.sh /tmp/backups-ci
backup_file=$(ls /tmp/backups-ci/doisag-*.dump.age | head -1)
[ -n "$backup_file" ] || { echo "o backup n gerou arquivo"; exit 1; }
CONFIRMAR_RESTAURACAO=doisag_teste BACKUP_AGE_KEY=/tmp/chave-do-backup.txt \
    ./scripts/restaurar-banco.sh "$backup_file" doisag_teste
restored=$(docker compose exec -T banco psql --username admindoisag --dbname doisag_teste -tAc \
    "select count(*) from users where email = 'prescritor-ci@email.com'")
[ "$restored" = "1" ] || { echo "a conta criada antes do backup n apareceu no banco restaurado"; exit 1; }

# fica no fim pq depois disso o login desse endereco fica travado por um minuto
echo "=== limite do nginx no login pela porta publica ==="
limited=0
for attempt in $(seq 1 15); do
    status=$($CURL -o /dev/null -w "%{http_code}" -H "Content-Type: application/json" -H "Origin: $PUBLIC_URL" \
        -d "{\"email\":\"limite-$attempt@email.com\",\"password\":\"senha-errada\"}" "$PUBLIC_URL/api/auth/login")
    if [ "$status" = "429" ]; then
        limited=$((limited + 1))
    fi
done
# e-mail diferente a cada tentativa, entao quem barra eh o nginx e n o limitador da api
[ "$limited" -gt 0 ] || { echo "15 logins seguidos e nenhum 429: o limit_req do nginx n esta valendo"; exit 1; }

echo "pilha do compose sobe em https e responde de ponta a ponta"
