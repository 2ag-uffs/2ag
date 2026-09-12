# 2ag

sistema livre para acompanhamento terapêutico longitudinal de pacientes em tratamento com óleo de Cannabis sativa.

o 2ag digitaliza o ciclo de cuidado de uma clínica que prescreve fitocanabinoides: triagem por anamnese, registro de consulta e prescrição, aplicação das escalas clínicas padronizadas com cálculo dos escores, envio automatizado dos formulários de acompanhamento ao longo dos 90 dias de tratamento e visualização gráfica da evolução dos sintomas.

## projeto de extensão universitária

sistema desenvolvido como ação de extensão no componente GCH1993 — Projeto de Integração de Extensão da Universidade Federal da Fronteira Sul (UFFS), campus Chapecó.

## o que você precisa instalar

o jeito mais fácil é com docker, aí você não instala mais nada:

- docker e docker compose

se preferir rodar na mão, sem docker:

- jdk 17 (não precisa instalar o maven, o repo já tem o `mvnw`)
- node 18 ou mais novo
- postgres

---

## rodando com docker

**1.** copia o arquivo de exemplo das configurações:

```bash
cp .env.example .env
```

**2.** abre o `.env` e preenche duas coisas:

- `POSTGRES_PASSWORD` — inventa uma senha
- `JWT_SECRET` — a chave que assina o login. pra gerar uma:

```bash
openssl rand -base64 48
```

essas duas não têm valor padrão de propósito. sem elas a aplicação não sobe, o que é melhor do que subir com uma senha publicada no github.

**3.** sobe tudo:

```bash
docker compose up
```

pronto, o banco, a api e a tela sobem juntos.

- tela: http://localhost:5173
- api: http://localhost:8080

pra parar é `ctrl+c`. os dados do banco ficam num volume, então não somem quando você desliga.

---

## rodando na mão, sem docker

### 1. o banco

entra no postgres e cria o usuário e o banco:

```sql
create user admindoisag with password 'a_senha_que_voce_quiser';
create database doisag owner admindoisag;
```

tem um passo a passo mais detalhado em [`database/README.md`](./database/README.md).

### 2. a api

as senhas vêm de variável de ambiente, não de arquivo. exporta as duas e sobe:

```bash
cd backend/doisag
export DATABASE_PASSWORD='a_senha_que_voce_criou'
export JWT_SECRET="$(openssl rand -base64 48)"
export SEED_DADOS_TESTE=true
./mvnw spring-boot:run
```

no intellij essas variáveis vão em `run` → `edit configurations` → `environment variables`.

o `SEED_DADOS_TESTE=true` cria dois usuários pra você testar sem cadastrar na mão:

| quem | email | senha |
| :--- | :--- | :--- |
| prescritora | prescritor@email.com | 123456 |
| paciente | paciente@email.com | 123456 |

### 3. a tela

em outro terminal:

```bash
cd frontend
npm install
npm run dev
```

abre em http://localhost:5173.

---

## rodando os testes

```bash
cd backend/doisag
./mvnw clean install
```

não precisa do postgres pra isso, os testes usam um banco em memória. se passar numa máquina limpa, está certo.

---

## onde fica cada coisa

```
backend/     a api, em java com spring boot
frontend/    a tela, em react
database/    os modelos do banco e o passo a passo de instalação
docs/        os requisitos e as escalas clínicas que a clínica usa
```

o documento de requisitos é o [`docs/requisitos-v2.md`](./docs/requisitos-v2.md). é lá que está o que o sistema precisa fazer e como cada escala é calculada.

---

## umas coisas boas de saber

- o paciente se cadastra sozinho, usando o código do prescritor dele. conta de prescritor é criada pela clínica, não por autocadastro.
- cada clínica roda a própria instalação. os dados não se misturam porque nem ficam no mesmo lugar.
- nunca comita arquivo com dado de paciente aqui: formulário preenchido, planilha de acompanhamento, exportação de prontuário. o `.gitignore` pega os casos mais comuns, mas confere o `git diff` antes de mandar.

---

## licença

[agpl-3.0](./LICENSE). qualquer clínica pode baixar, usar e mudar. quem mudar e oferecer como serviço precisa publicar as mudanças também.
