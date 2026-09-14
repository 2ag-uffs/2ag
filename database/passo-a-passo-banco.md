# postgres para o 2ag no linux

guia rápido para quem usa **linux (debian ou ubuntu)** e ainda não tem o postgresql instalado. para os outros sistemas, veja o [`README.md`](./README.md) desta pasta.

## instalar

```bash
sudo apt update
sudo apt install postgresql postgresql-contrib
```

confira se o serviço está ativo:

```bash
sudo systemctl status postgresql
```

se não aparecer `active (running)`, ative:

```bash
sudo systemctl start postgresql
```

## criar o usuário e o banco

entre no console:

```bash
sudo -u postgres psql
```

crie o usuário da aplicação e o banco. anote a senha, ela é usada no próximo passo:

```sql
CREATE USER admindoisag WITH PASSWORD '<sua_senha>';
CREATE DATABASE doisag OWNER admindoisag;
```

saia com `\q`.

## configurar a api

a senha não vai em arquivo versionado. exporte as variáveis antes de subir a api:

```bash
export DATABASE_PASSWORD='<a senha que você criou acima>'
export JWT_SECRET="$(openssl rand -base64 48)"
export SESSION_SECURE_COOKIE=false
export SEED_DADOS_TESTE=true
```

`DATABASE_PASSWORD` e `JWT_SECRET` não têm valor padrão: a api não sobe sem elas, de propósito.

## subir a api

```bash
cd backend/doisag
./mvnw spring-boot:run
```

a api fica em `http://localhost:8080/api`. só o **jdk 17** é obrigatório: o `mvnw` baixa o maven sozinho.

## contas de teste

com `SEED_DADOS_TESTE=true`, a primeira subida cria três contas, todas com a senha `Senha@123`:

| perfil | nome | e-mail |
| :--- | :--- | :--- |
| administrador | Administrador de Teste | `admin@email.com` |
| prescritor | Ana Lima | `prescritor@email.com` |
| paciente | Maria Souza | `paciente@email.com` |

junto vêm dados fictícios para demonstração: consultas, prescrições, diário da Maria e mais quatro pacientes (`joao.almeida`, `renata.dias`, `paulo.nunes` e `carla.menezes`, todos `@email.com` com a mesma senha).

o seed nunca pode ser ligado em produção.
