# 2ag

sistema livre para acompanhamento terapêutico longitudinal de pacientes em tratamento com óleo de Cannabis sativa. O 2ag digitaliza o ciclo de cuidado de uma clínica que prescreve fitocanabinoides: triagem por anamnese, registro de consulta e prescrição, aplicação das escalas clínicas padronizadas com cálculo dos escores, envio automatizado dos formulários de acompanhamento ao longo dos 90 dias de tratamento e visualização gráfica da evolução dos sintomas.

## projeto

sistema desenvolvido como ação de extensão no componente GCH1993 — Projeto de Integração de Extensão da Universidade Federal da Fronteira Sul (UFFS), campus Chapecó.

o que o sistema precisa fazer está em [`docs/requisitos-v2.md`](./docs/requisitos-v2.md).

## o que você precisa instalar

o jeito mais fácil é com docker:

- docker e docker compose

para rodar sem docker:

- jdk 17 (o maven não precisa, o repositório já tem o `mvnw`)
- node 20 ou mais novo
- postgres

---

## rodando com docker

**1.** copie o arquivo de exemplo das configurações:

```bash
cp .env.example .env
```

**2.** abra o `.env` e preencha:

- `POSTGRES_PASSWORD`: uma senha para o banco
- `JWT_SECRET`: a chave que assina a sessão. para gerar uma, use `openssl rand -base64 48`
- `ADMIN_EMAIL` e `ADMIN_PASSWORD`: a conta administrativa, criada na primeira subida. é ela que cria as contas de prescritor
- `PUBLIC_URL`: o endereço onde as pessoas abrem o sistema. vai nos links enviados por e-mail
- `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD` e `MAIL_FROM`: o servidor de e-mail que manda o link de senha nova. sem `MAIL_HOST`, a mensagem aparece só no log da api

`POSTGRES_PASSWORD` e `JWT_SECRET` não têm valor padrão de propósito: sem elas a aplicação não sobe.

**3.** suba tudo:

```bash
docker compose up
```

abra http://localhost:5173. o nginx do front entrega a tela e repassa as chamadas de `/api` para o backend, então só essa porta fica exposta. o banco não fica acessível de fora da rede do compose.

os dados ficam num volume e não somem quando os containers param. para parar, use `ctrl+c`.

> num servidor com https, troque `SESSION_SECURE_COOKIE` para `true` no `.env`. em `false` o cookie da sessão também trafega sem https, o que só serve para testar no próprio computador.

---

## rodando sem docker

### 1. o banco

```sql
create user admindoisag with password 'a_senha_que_voce_quiser';
create database doisag owner admindoisag;
```

há um passo a passo mais detalhado em [`database/README.md`](./database/README.md).

### 2. a api

```bash
cd backend/doisag
export DATABASE_PASSWORD='a_senha_que_voce_criou'
export JWT_SECRET="$(openssl rand -base64 48)"
export SESSION_SECURE_COOKIE=false
export SEED_DADOS_TESTE=true
./mvnw spring-boot:run
```

a api sobe em http://localhost:8080/api. no intellij, as variáveis vão em `run` → `edit configurations` → `environment variables`.

### 3. a tela

em outro terminal:

```bash
cd frontend
npm install
npm run dev
```

abra http://localhost:5173. em desenvolvimento o vite repassa `/api` para a porta 8080, do mesmo jeito que o nginx faz no docker.

### sem postgres, só para olhar as telas

a api também sobe com o banco em memória dos testes, no lugar dos passos 1 e 2. os dados somem quando ela para:

```bash
cd backend/doisag
./mvnw spring-boot:test-run "-Dspring-boot.run.arguments=--spring.profiles.active=test --api.seed.enabled=true --api.session.secure-cookie=false"
```

> **porta 8080 ocupada:** suba a api em outra porta (`export SERVER_PORT=8081`, ou `--server.port=8081` junto dos argumentos acima) e crie o arquivo `frontend/.env.local` com a linha `API_URL=http://localhost:8081`.

### contas de teste

com `SEED_DADOS_TESTE=true` a api cria três contas, todas com a senha `Senha@123`:

| perfil | nome | e-mail |
| :--- | :--- | :--- |
| administrador | Administrador de Teste | admin@email.com |
| prescritor | Ana Lima | prescritor@email.com |
| paciente | Maria Souza | paciente@email.com |

na primeira subida o seed também cria dados fictícios para demonstração: horários de atendimento, consultas, prescrições, dois meses de diário da Maria e mais quatro pacientes (`joao.almeida`, `renata.dias`, `paulo.nunes` e `carla.menezes`, todos `@email.com` com a mesma senha). nomes, cpfs e históricos são inventados.

o seed nunca pode ser ligado em produção.

> **banco local antigo:** as migrações foram consolidadas numa base única em 13/09/2026. quem já tinha um banco de antes dessa data precisa recriá-lo: `docker compose down -v` com docker, ou `drop database doisag;` seguido de `create database doisag owner admindoisag;` sem docker.

---

## rodando os testes

```bash
cd backend/doisag
./mvnw clean install
```

não precisa de postgres: os testes usam banco em memória.

```bash
cd frontend
npm run lint
npm run build
```

o ci do github roda os três a cada push e também confere se as imagens do docker continuam montando.

---

## backup

o banco tem script de backup e de restauração em [`scripts/`](./scripts), e o procedimento está em [`docs/backup-e-restauracao.md`](./docs/backup-e-restauracao.md).

```bash
export DATABASE_PASSWORD='a_senha_do_banco'
./scripts/backup-banco.sh /var/backups/2ag
```

o backup do dia não substitui o do dia anterior, porque o prontuário tem guarda mínima de 20 anos. e backup que ninguém restaurou não vale: o documento tem a tabela onde cada teste de restauração fica registrado.

---

## onde fica cada coisa

```
backend/     a api, em java com spring boot
frontend/    a tela, em react
database/    a modelagem de 2025 e o passo a passo de instalação do banco
docs/        requisitos, escalas clínicas, identidade visual e documentos da extensão
```

---

## umas coisas boas de saber

- o paciente cria a própria conta pelo link de convite que o prescritor gera no sistema. conta de prescritor é criada pelo administrador, nunca por autocadastro.
- o administrador cuida só das contas: ele não vê prontuário.
- nenhum dado clínico é apagado, e toda criação, alteração e abertura de prontuário fica na trilha de auditoria. o prescritor consulta a trilha de cada paciente pela lista de pacientes, e o administrador consulta em Auditoria, sem ver nome de paciente.
- o paciente pede consulta só nos horários de atendimento que o prescritor cadastra, e o horário fica reservado até o prescritor responder.
- paciente e prescritor exportam o histórico em CSV, e a página de impressão salva em PDF pelo próprio navegador. a exportação do prescritor tem modo anônimo para pesquisa.
- cada clínica roda a própria instalação. os dados não se misturam porque nem ficam no mesmo lugar.
- nunca comite arquivo com dado de paciente: formulário preenchido, planilha de acompanhamento, exportação de prontuário. o `.gitignore` pega os casos mais comuns, mas confira o `git diff` antes de enviar.

---

## licença

[agpl-3.0](./LICENSE). qualquer clínica pode baixar, usar e mudar. quem mudar e oferecer como serviço precisa publicar as mudanças também.
