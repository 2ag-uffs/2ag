# banco de dados do 2ag

guia para instalar e configurar o postgres para a api do 2ag. com docker nada disso é necessário: o `docker compose up` sobe o banco sozinho (veja o [README da raiz](../README.md)).

## primeiro passo: instalar o postgresql

### windows

1. **download**: baixe o instalador no site oficial do postgresql
2. **instalação**: execute o instalador e siga as instruções
    - quando ele pedir uma senha para o usuário **postgres**, anote essa senha
    - deixe marcada a opção de instalar as **command line tools**
3. **path**: adicione a pasta `bin` do postgres às variáveis de ambiente para usar os comandos no terminal. o caminho costuma ser `c:\program files\postgresql\<versão>\bin`

### macos

```bash
brew install postgresql
brew services start postgresql
```

### linux

```bash
sudo apt update
sudo apt install postgresql postgresql-contrib
```

## segundo passo: criar o usuário e o banco da aplicação

entre no `psql`:

- **windows**: `psql -U postgres`, com a senha criada na instalação
- **macos**: `psql postgres`
- **linux**: `sudo -u postgres psql`

lá dentro, crie o usuário da aplicação e o banco:

```sql
create user admindoisag with password '<senha_para_a_aplicacao>';
create database doisag owner admindoisag;
```

escolha uma senha forte e anote. para sair do `psql`, use `\q`.

## terceiro passo: configurar a api

a senha do banco não fica em arquivo, ela vem de variável de ambiente:

```bash
export DATABASE_PASSWORD='<a senha que você criou para o admindoisag>'
export JWT_SECRET="$(openssl rand -base64 48)"
export SESSION_SECURE_COOKIE=false
```

para ter contas de teste na primeira subida, exporte também:

```bash
export SEED_DADOS_TESTE=true
```

no intellij, o caminho é `run` → `edit configurations` → `environment variables`, na configuração do `DoisagApplication`. a lista completa de variáveis está no [`backend/README.md`](../backend/README.md#configuração).

## quarto passo: subir a api

```bash
cd backend/doisag
./mvnw spring-boot:run
```

na primeira subida o **flyway** cria as tabelas a partir das migrações em `backend/doisag/src/main/resources/db/migration/`. com `SEED_DADOS_TESTE=true` também são criadas as contas de teste, todas com a senha `Senha@123`: `admin@email.com`, `prescritor@email.com` e `paciente@email.com`.

> para rodar os **testes** nada disso é necessário: `./mvnw clean install` usa h2 em memória.

## banco criado antes de 13/09/2026

as migrações foram consolidadas numa base única nessa data. um banco local criado antes precisa ser recriado, senão o flyway recusa a subida:

```sql
drop database doisag;
create database doisag owner admindoisag;
```

## modelagem de 2025

as pastas `conceptual-model/`, `logical-model/` e `physical-model/` guardam a modelagem feita em 2025. **são registro histórico, não a fonte de verdade**: o esquema atual vive nas migrações do flyway e já diverge desses arquivos. não use os scripts dessas pastas para criar banco.

para um passo a passo focado em linux, veja o [`passo-a-passo-banco.md`](./passo-a-passo-banco.md).
