#!/usr/bin/env bash
# sobe a pilha do compose com um .env de mentira e confere o caminho q so existe no docker:
# healthcheck da api, proxy do nginx, fallback da spa, criacao do admin, login e um POST com cookie
# (o POST eh o unico jeito de pegar PUBLIC_URL errado, pq GET passa sem conferir a origem)
set -euo pipefail

PUBLIC_URL="http://localhost:5173"
ADMIN_EMAIL="admin-ci@email.com"
ADMIN_PASSWORD="SenhaDoCi@2026-teste"

cat > .env <<EOF
POSTGRES_DB=doisag
POSTGRES_USER=admindoisag
POSTGRES_PASSWORD=senha-do-banco-do-ci
JWT_SECRET=chave-de-mentira-do-ci-com-tamanho-suficiente-pra-assinar-0123456789
SEED_DADOS_TESTE=false
SESSION_SECURE_COOKIE=false
ADMIN_EMAIL=$ADMIN_EMAIL
ADMIN_PASSWORD=$ADMIN_PASSWORD
PUBLIC_URL=$PUBLIC_URL
EOF

cleanup() {
    echo "=== log da api ==="
    docker compose logs --no-color api | tail -80 || true
    docker compose down -v || true
}
trap cleanup EXIT

# o --wait espera o healthcheck da api, q ja depende do banco
docker compose up -d --wait --wait-timeout 180

# o web n tem healthcheck, entao o nginx pode levar um instante a mais
for attempt in $(seq 1 30); do
    if curl -sf -o /dev/null "$PUBLIC_URL/"; then
        break
    fi
    sleep 2
done

echo "=== spa e proxy ==="
curl -sf "$PUBLIC_URL/" | grep -q "<div id=\"root\"" || { echo "a pagina inicial n veio"; exit 1; }
# fallback da spa: rota do front devolve o index e n 404
curl -sf "$PUBLIC_URL/entrar" | grep -q "<div id=\"root\"" || { echo "o fallback da spa n funciona"; exit 1; }
curl -sf "$PUBLIC_URL/api/health" | grep -q "UP" || { echo "a api n responde pelo nginx"; exit 1; }

echo "=== admin criado na subida ==="
docker compose logs --no-color api | grep -q "conta administrativa criada" || { echo "o admin n foi criado"; exit 1; }

echo "=== login ==="
login_status=$(curl -s -o /tmp/login.json -w "%{http_code}" -c /tmp/cookies.txt \
    -H "Content-Type: application/json" -H "Origin: $PUBLIC_URL" \
    -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" \
    "$PUBLIC_URL/api/auth/login")
[ "$login_status" = "200" ] || { echo "login respondeu $login_status: $(cat /tmp/login.json)"; exit 1; }
grep -q "session" /tmp/cookies.txt || { echo "o login n gravou o cookie da sessao"; exit 1; }

echo "=== POST com cookie passa pela conferencia de origem ==="
create_status=$(curl -s -o /tmp/create.json -w "%{http_code}" -b /tmp/cookies.txt \
    -H "Content-Type: application/json" -H "Origin: $PUBLIC_URL" \
    -d '{"name":"Prescritor do CI","email":"prescritor-ci@email.com","cpf":"52998224725","profession":"Médico","registryType":"CRM","registryNumber":"12345"}' \
    "$PUBLIC_URL/api/admin/prescribers")
[ "$create_status" = "201" ] || { echo "o POST autenticado respondeu $create_status: $(cat /tmp/create.json)"; exit 1; }

echo "=== POST de outra origem eh recusado ==="
other_status=$(curl -s -o /dev/null -w "%{http_code}" -b /tmp/cookies.txt \
    -H "Content-Type: application/json" -H "Origin: http://outro-site.exemplo" \
    -d '{"active":true}' \
    "$PUBLIC_URL/api/admin/prescribers/1/active")
[ "$other_status" = "403" ] || { echo "requisicao de outra origem respondeu $other_status em vez de 403"; exit 1; }

echo "pilha do compose sobe e responde de ponta a ponta"
