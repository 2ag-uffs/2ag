# Checklist de segurança do 2AG

Conferência dos 20 itens do checklist de segurança sobre o 2AG, feita em 01/10/2026 sobre o commit `e33dcc6` (`main`), lendo o código de ponta a ponta: os 22 controllers e seus DTOs, os três serviços de acesso, o `SecurityFilter`, o `OriginCheckFilter`, as migrações, o `docker-compose.yml`, o nginx do front, o histórico completo do git e as configurações do repositório no GitHub (`gh api repos/2ag-uffs/2ag`). Os itens 3 e 4 vêm de checklists de Supabase/Firebase (chave pública e row level security) e foram traduzidos para o que significam aqui: o banco está alcançável de fora ou com credencial exposta, e toda leitura ou escrita de dado de paciente passa por uma conferência de vínculo na API.

**Veredito:** o núcleo está certo e provado por teste — sessão só no servidor, matriz de papéis varrida pelo `RouteRolesTest`, vínculo prescritor/paciente conferido em toda rota clínica, bcrypt em todo caminho de senha, nenhuma query montada com texto, nenhuma entidade atravessando a API e cadastro fechado por convite. O que pesa é o transporte: não existe HTTPS em nenhum ponto da pilha, e isso sozinho carrega os três itens de gravidade alta (5, 9 e 19), porque anula o `Secure` do cookie, o HSTS e a cifra de tudo que trafega. Depois do TLS, o que resta de concreto são três oráculos de senha sem limitador (trocar senha, trocar e-mail e o reset feito pelo administrador), dois caminhos de tomada de conta pelo administrador que já têm card (#61, #62), uma lista de pacientes que manda CPF e endereço sem nenhuma tela usar, e um processo de dependências em que o backend inteiro é invisível para o Dependabot. Nenhuma correção é reescrita: a maioria cabe em um commit pequeno em ponto já mapeado.

| Situação | Quantidade |
| :--- | ---: |
| ok | 6 |
| parcial | 12 |
| falta | 1 |
| não se aplica | 1 |

| nº | Item | Situação | Gravidade | Card |
| ---: | :--- | :--- | :--- | :--- |
| 1 | Esconder API Keys | parcial | baixa | #18, #103 · #114 |
| 2 | Limpar secrets do git | parcial | baixa | #18 · #115 |
| 3 | Banco acessível de fora / credencial exposta | ok | baixa | #17, #18 |
| 4 | Escopo por dono na API (o "RLS" daqui) | ok | baixa | #29, #27 · #124 |
| 5 | Criptografia de dados | parcial | alta | #57, #17, #18, #19 · #117 |
| 6 | Auth server side | ok | baixa | #57, #109 |
| 7 | Restringir acessos | parcial | média | #61, #62, #83, #84 |
| 8 | Bloquear mass assignment | ok | nenhuma | #124 |
| 9 | Proteger cookies | parcial | alta | #57, #103, #109 · #118 |
| 10 | Hash nas senhas | ok | baixa | #61, #84 |
| 11 | Rate limit | parcial | média | #82, #94, #111 · #119, #120 |
| 12 | Bot protection | parcial | baixa | #82, #102 · #119 |
| 13 | Queries parametrizadas | ok | nenhuma | — |
| 14 | Validação dos inputs | parcial | baixa | #111, #82 · #120, #121 |
| 15 | Vazar conteúdo | parcial | média | #82, #97, #109, #111 · #120, #123 |
| 16 | Restringir uploads | não se aplica | nenhuma | #111 |
| 17 | Trim respostas de API | parcial | média | #109, #61, #37 · #122, #124 |
| 18 | Security headers | parcial | baixa | #57, #102, #111 |
| 19 | Forçar HTTPS | falta | alta | #57, #103, #18, #102 |
| 20 | Scan de dependências | parcial | média | #115, #116 |

---

## 1 — Esconder API Keys

**Situação.** parcial, gravidade baixa.

**Evidência.** O 2AG não usa chave de serviço de terceiro; os segredos de verdade são três e vêm todos do ambiente sem valor padrão: `application.yml:19` (`password: ${DATABASE_PASSWORD}`), `:53` (`secret: ${JWT_SECRET}`) e `:36-39` (`MAIL_*`, padrão vazio); `:70-71` (`ADMIN_*`) e `:79` (`PUBLIC_URL`) seguem o mesmo padrão. O `docker-compose.yml:14-16` e `:39-63` só repassam o `.env`, e o `.env.example:14,21,34,51` entrega os campos vazios. O `.gitignore:19-28` ignora `.env`, `.env.*`, `application-local*`, `application-secrets.yml`, `*.pem`, `*.key`, `*.p12` e `*.jks`. No front, `grep` por `import.meta.env`, `VITE_` e `process.env` em `frontend/src` não retorna nada; o `vite.config.js:8-9` carrega as variáveis só para o proxy de desenvolvimento, nada entra no `define`, e o `frontend/.env.local` (só `API_URL`) não é rastreado (`frontend/.gitignore:14`). Não existe actuator no `pom.xml`, então não há `/actuator/env`; o `/api/health` é controller próprio e devolve só `UP`/`DOWN` (`HealthController.java:22`). O h2 e o `spring-boot-h2console` são escopo `test` (`pom.xml:67-75`) e o devtools é `optional` (`:44-46`), logo nada disso vai para o jar. O `TokenService.java:31-34` usa o `JWT_SECRET` só para montar a chave HMAC e nunca o imprime; o jjwt recusa chave com menos de 256 bits, então segredo curto derruba a API em vez de assinar fraco.

O que sobra: a única credencial fixa no código é `DevDataSeed.java:31` (`DEV_PASSWORD = "Senha@123"`, contas `admin@email.com`, `prescritor@email.com`, `paciente@email.com`), publicada em `README.md:111-117`, `database/README.md:71`, `database/passo-a-passo-banco.md:65` e `docs/qa/plano-de-testes.md:20`. Ela só sobe com `api.seed.enabled=true` (`DevDataSeed.java:25`), padrão `false` em `application.yml:74`, `docker-compose.yml:43` e `.env.example:24`, mas não há nenhuma trava contra produção além da frase em prosa do `README.md:121`. Dois furos de build: `backend/doisag/.dockerignore:2-4` não exclui `src/main/resources/application-local*.yml` (ignorado no git, mas o `Dockerfile:13` faz `COPY src ./src` e cozinharia um override com senha real dentro do jar); e `frontend/.dockerignore:5` exclui só `.env`, não `.env.*` nem `*.local`, com o `Dockerfile:8` fazendo `COPY . .` — hoje inofensivo porque `API_URL` não tem prefixo `VITE_` e não entra no bundle. O `docs/backup-e-restauracao.md:33` sugere `DATABASE_PASSWORD='...'` inline na linha do cron, que não é furo (crontab do dono, env não aparece no `ps`), mas repete a senha fora do `.env`.

**Risco real para este sistema.** Baixo. O cenário é um `SEED_DADOS_TESTE=true` esquecido ou digitado errado no `.env` do servidor: na primeira subida nasce um administrador com senha publicada em repositório público, e o administrador cria prescritor. Exige editar o `.env` na mão, porque o exemplo já vem `false`. O furo do `.dockerignore` só vira problema se alguém criar `application-local.yml` com senha real na mesma máquina que roda `docker compose build`.

**Correção concreta.**

1. Trava do seed em produção, em `DevDataSeed`:

```java
@Value("${api.public-url}")
private String publicUrl;

@Override
public void run(ApplicationArguments args) {
    // seed de teste nunca sobe num endereco publico
    if (publicUrl.startsWith("https://")) {
        log.warn("seed ignorado pq o endereco publico eh https");
        return;
    }
    ...
}
```

2. `backend/doisag/.dockerignore`: acrescentar `src/main/resources/application-local*.yml` e `src/main/resources/application-secrets.yml`.
3. `frontend/.dockerignore`: trocar `.env` por `.env*` e acrescentar `*.local`.
4. No guia de deploy (#18) deixar escrito que `SEED_DADOS_TESTE` fica fora do `.env` do servidor e que o backup lê a senha do `.env` em vez de repeti-la no cron.

**Card.** #18 (guia de deploy, o corpo já diz que o seed nunca liga) e #103 (mesmo mecanismo de `.env.example` vencendo o padrão do compose). Nenhum card cobre a trava do seed nem os `.dockerignore`: #114.

---

## 2 — Limpar secrets do git

**Situação.** parcial, gravidade baixa.

**Evidência.** O repositório é público (`gh repo view`: `visibility=PUBLIC`), com 161 commits em `main`, 0 forks e sem proteção de branch. `git log --all --diff-filter=A --name-only | grep -i env` lista apenas `.env.example` e um `frontend/.env.example` que existiu entre `c5c9a8b` e `bddc60e` com um único conteúdo, `VITE_API_URL=http://localhost:8080`. Nenhum `.env`, `.pem`, `.key`, `.jks` ou `.p12` jamais entrou, e o `.gitignore` já os ignorava desde o primeiro commit `1f49dc1`, antes de importar o protótipo. A varredura de linhas adicionadas em toda a história (fora lockfiles) por `JWT_SECRET=`, `*_PASSWORD=`, `secret:`, `password:`, `BEGIN PRIVATE KEY`, `AKIA`, `ghp_`, `sk-`, `eyJ`, `AIza`, host real ou IP fora de localhost acha **um** segredo real: `api.security.token.secret: minha-api-super-secreta-e-protegida-0123456789-com-uma-chave-bem-grande` em `backend/doisag/src/main/resources/application.yml:25` no commit `4c711a2` (12/09/2026 17:46, importação do protótipo de 2025), removido em `85a2dc5` quatro minutos depois; `git grep` no HEAD não acha. O mesmo protótipo tinha um seed que gravava a senha do prescritor em texto puro (`DoisagApplication.java:42`, `setPassword("123456")` sem encode), retirado em `3e47583` no mesmo dia. No HEAD, `database/physical-model/script-insert.sql:4,5,13` guarda três hashes bcrypt de usuários de teste do protótipo — arquivo morto, sem referência e sem `$2a$` em nenhuma migração do Flyway. Só constantes de teste restam: `application-test.yml:26` (chave fixa de teste), `AdminPrescribersTest.java:42`, `ci.yml:39,53` (`doisag-ci` num Postgres descartável do runner). O `docs/scales/anamnese.xlsx` é exportação de Google Forms em branco (23 strings, só cabeçalho), e `*(respostas)*.xlsx` está no `.gitignore`. No GitHub, `secret_scanning`, `secret_scanning_push_protection` e `non_provider_patterns` estão todos `disabled`.

**Risco real para este sistema.** A chave JWT do protótipo é pública para sempre. Como o `application.yml` atual exige `JWT_SECRET` do ambiente sem padrão, só vira problema se quem fizer o deploy copiar o valor antigo ou se algum servidor do protótipo de 2025 ainda estiver no ar com ela (não há sinal disso: a organização `2ag-uffs` não tem outro repositório e `gh search repos doisag` não acha nada). Reescrever o histórico seria barato (0 forks), mas não compensa: o valor era placeholder de desenvolvimento que o código atual nunca lê, e o GitHub continua servindo o commit antigo por SHA até o suporte purgar. Os hashes do `script-insert.sql` são de contas que nunca existiram no banco atual.

**Correção concreta.**

1. No guia de deploy (#18) e no comentário do `.env.example:20`: gerar com `openssl rand -base64 48` e **nunca reaproveitar chave ou senha de versão antiga do repositório**.
2. Ligar em Settings > Code security: Secret scanning e Push protection (grátis em repositório público; ou `gh api -X PATCH repos/2ag-uffs/2ag` com `security_and_analysis`). Assim um `.env` colado por engano é barrado no push.
3. Apagar `database/physical-model/script-insert.sql` ou trocar os hashes por `'<hash>'`, já que nada usa o arquivo.

**Card.** #18 e #103 (ambos ganham a frase sobre não reaproveitar). As configurações do GitHub estão no #115 (junto com o item 20).

---

## 3 — Banco acessível de fora / credencial exposta

**Situação.** ok, gravidade baixa.

**Evidência.** No `docker-compose.yml`, o serviço `banco` (`:9-27`) não tem bloco `ports:` — e o comentário em `:22` mostra que foi decisão, não esquecimento — e o `api` (`:29-71`) também não; só o `web` publica `5173:80` (`:81-82`). A API fala com o banco pelo nome do serviço na rede interna (`:39`, `jdbc:postgresql://banco:5432/${POSTGRES_DB}`). Não existe `docker-compose.override.yml`, `network_mode: host` nem `POSTGRES_HOST_AUTH_METHOD` em lugar nenhum. A senha vem de `POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}` (`:16`) sem padrão, `.env.example:14` vazio, e a imagem do Postgres se recusa a subir sem ela; `application.yml:19` idem. Nome do banco e usuário (`doisag`/`admindoisag`, `.env.example:11-12`) são públicos, mas não são segredo. O container da API roda sem root (`backend/doisag/Dockerfile:22-23`). Pela API não vaza: sem actuator, h2 em escopo `test`, `/api/health` roda `select 1` e devolve só o status (`HealthController.java:22-30`), nenhum código de `main` lê `System.getenv` nem imprime a URL do datasource. No histórico, o compose chegou a publicar `5432:5432` em `85a2dc5` (12/09) e tirou no dia seguinte em `bddc60e`, antes de existir servidor. O `ci.yml:42-43` publica 5432 só no runner descartável. Ressalva já conhecida: `scripts/backup-banco.sh:16-17` e `scripts/restaurar-banco.sh:15-16` assumem `localhost:5432`, que não existe no host com este compose (`OPS-05` em `docs/auditoria-da-api.md:68-74`, card #17).

**Risco real para este sistema.** Hoje baixo. O risco é operacional: quando o backup falhar com "connection refused", a saída mais rápida é acrescentar `ports: - "5432:5432"` no `banco`, e aí o Postgres fica aberto na internet com usuário conhecido, barreira só de senha e sem TLS — e porta publicada pelo Docker em Linux passa por cima do ufw/iptables `INPUT`.

**Correção concreta.** Fechar o #17 rodando o dump pelo container, sem porta publicada e sem `PGPASSWORD` no host:

```sh
# em scripts/backup-banco.sh, no lugar do pg_dump por host
docker compose exec -T banco pg_dump -U "$USUARIO" --format custom "$BANCO" > "$ARQUIVO"
# e no restaurar
docker compose exec -T banco pg_restore --dbname "$BANCO" -U "$USUARIO" --clean < "$ARQUIVO"
```

Se um dia precisar de porta no host, só em loopback: `ports: - "127.0.0.1:5432:5432"`. No guia #18, escrever "nunca publicar a porta do banco". Endurecimento opcional e pequeno, cabe no #111: duas redes no compose (`frente`: web + api; `fundo`: api + banco), para o nginx não alcançar `banco:5432`.

**Card.** #17 (backup com compose) e #18 (guia de deploy).

---

## 4 — Escopo por dono na API (o "RLS" daqui)

**Situação.** ok, gravidade baixa.

**Evidência.** Não há RLS no Postgres (a API usa o dono do banco, `application.yml:16-19`) e não precisa: a conferência por linha fica num único ponto e é aplicada em toda rota com paciente. `PatientAccessService.java:34-51`: paciente só o próprio id (`:41-43`), prescritor só quem está na carteira dele via `existsByIdAndPrescriberId` (`:46-48`, `PatientRepository.java:22`), admin e qualquer outro principal recebem `false` (`:50`). Consulta e prescrição resolvem para o paciente dono antes de aplicar a mesma regra (`:61-78`); escala respondida e anamnese idem em `ScaleResponseAccessService.java:23-32` e `AssessmentAccessService.java:23-33`, e id inexistente dá 403 em vez de 404 para ninguém enumerar. Todo handler com dado de paciente usa um desses beans no `@PreAuthorize`: `PatientsController:46-60`, `ClinicalHistoryController:34-53`, `PatientScalesController:38-62`, `ExportController:35-74` (os cinco CSV), `ProgressReportController:39-65`, `TreatmentProtocolController:25-44`, `AuditController:26`, `DashboardController:32`, `AppointmentsController:46,100-136`, `ConsultationController:31-48`, `PrescriptionsController:33-48`, `ScalesController:90-131`, `AnamnesisController:43-57`. As rotas sem bean tiram o dono do principal, nunca de parâmetro (`AppointmentsController:58-79`, `ScalesController:75-87`, `AnamnesisController:38-39`, `PatientsController:39-40`); avisos conferem o dono no serviço (`NotificationService.java:69-76`). O único DTO de escrita com id de outro registro é `AppointmentScheduleDTO.patientId`, conferido no `@PreAuthorize` com `#scheduleData.patientId()` (`AppointmentsController:46`).

As precondições que sustentam isso também conferem: `@EnableMethodSecurity` ligado (`SecurityConfigurations.java:21`); o principal é a entidade JPA recarregada do banco a cada requisição (`SecurityFilter.java:60`), numa hierarquia `JOINED` (`Users.java:18-20`), com conta inativa recusada na hora (`:63`); `/admin/**` só ADMIN e `anyRequest()` só PATIENT/PRESCRIBER (`SecurityConfigurations:57-61`); nenhum serviço usa `findAll()`; `Appointment.patient` e `.prescriber` são `nullable=false` (`Appointment.java:135-139`), então `canAccessAppointment` não vira 500 por NPE; nginx só repassa `/api/` (`frontend/nginx.conf:15-16`). Testes: `PatientLinkTest.java:87-170` cobre prescritor A contra paciente de B em 15 leituras e 15 escritas, paciente A contra B e admin, `:189-217` são controles positivos (dono recebe 200) e `:219-269` manda corpo apontando para o paciente B e prova que o registro continua do A. Execução local em 01/10/2026: `PatientLinkTest` 14/14, `RouteRolesTest` 8/8; o `ci.yml:23,61` roda a suíte no H2 e num Postgres de verdade, e a execução do HEAD está verde.

**Risco real para este sistema.** Baixo: a imposição está completa, o que falta é cobertura. Estão anotadas mas não entram no `PatientLinkTest`: `/patients/{id}/export/*.csv` (cinco), `/patients/{id}/audit-events`, `/patients/{id}/progress/comments`, `/patients/{id}/archive` e `/reactivate`, `/appointments/{id}/no-show`, `/scales/responses/{id}/review` e `/annul`, `/anamneses/{id}` GET/PUT/annul e `GET /scales/mental-state-exam/appointments/{id}`. Mais importante: a guarda estrutural `RouteRolesTest.everyRouteDeclaresTheRolesThatCanUseIt` (`:103-120`) só exige `hasRole(`/`hasAnyRole(` — uma rota nova `GET /patients/{patientId}/foo` anotada só com `hasRole('PRESCRIBER')` passa no teste e vaza entre prescritores. Nota de desenho: `canAccessAppointment` resolve pelo prescritor **atual** do paciente e não pelo da consulta (`PatientAccessService:59-67`); hoje dá no mesmo porque paciente não troca de prescritor, mas se transferência entrar um dia o histórico inteiro passa para o novo e o antigo perde tudo — precisa estar decidido com a clínica (#27).

**Correção concreta.** O conserto mais barato é estrutural, no mesmo `RouteRolesTest`: para cada rota cujo caminho tenha `{patientId}`, `{appointmentId}` ou `{id}` e não comece com `/admin/` nem `/notifications/` (pulando `/invites/{token}`, `/scales/definitions/{slug}` e `/scales/{slug}/responses`, que não são id de registro), assertar que a regra contém `"Access."`. Isso fecha o buraco no momento em que a rota nasce. Depois, acrescentar as rotas listadas acima ao `PatientLinkTest` (o record `ClinicalRecords` já tem quase tudo, só falta guardar o id da anamnese) e mover o id do MEEM de `prescriberOnlyReadRoutes` (`:310-314`) para `sharedReadRoutes`, já que `GET /scales/responses/{id}` aceita PATIENT via `scaleAccess` (`ScalesController:90`).

**Card.** #29 (qa: permissões e isolamento, CT-07 a CT-11) e #27 (decisão sobre transferência). Os testes automatizados de tranca estão no #124.

---

## 5 — Criptografia de dados

**Situação.** parcial, gravidade alta — quase toda carregada pelo #57; depois do HTTPS o residual cai para média.

**Evidência.** *Em trânsito, fora:* nenhum TLS na pilha. `frontend/nginx.conf:4` só tem `listen 80;`, `docker-compose.yml:81-82` publica `5173:80`, e `grep` por `ssl_certificate`, `certbot`, `caddy`, `traefik` e `listen 443` no repositório inteiro não retorna nada (o commit `65e977b` é só cabeçalho). O próprio projeto exige em `docs/requisitos-v2.md:502` (RNF05) "toda conexão externa por HTTPS/TLS", e a auditoria interna registra a falta como `OPS-01` (`docs/auditoria-da-api.md:30-41`). *Em trânsito, dentro:* nginx → API em http (`nginx.conf:16`) e API → Postgres sem TLS (`compose:39`), mas os dois ficam na rede interna do compose no mesmo host sem porta publicada — proporcional. *SMTP:* `application.yml:42` liga `mail.smtp.starttls.enable`, mas não existe `mail.smtp.starttls.required` em lugar nenhum, então o STARTTLS é oportunista: um servidor (ou alguém no meio) que responda sem TLS faz o link de senha nova e o `MAIL_PASSWORD` irem em claro; o `LogEmailSender.java:23-27` confirma que o link só trafega no corpo do e-mail. *Em repouso, senhas e códigos:* bcrypt em `SecurityConfigurations.java:74-76`; convite e reset guardam só o SHA-256 do código (`SecureTokens.java:27-31`, `V2__convite_de_paciente.sql:8`, `V6__recuperacao_de_senha.sql:8`). *Em repouso, banco:* volume Docker comum `dados-do-banco` (`compose:21,85`); nada no repositório, README ou docs fala de disco cifrado. *Em repouso, backup:* `scripts/backup-banco.sh:29-35` faz `pg_dump --format custom` (comprimido, não cifrado) e `docs/backup-e-restauracao.md:40` manda a cópia sair do servidor para outro disco ou bucket sem falar em cifrar; #17 e #19 não cobrem isso. *Campos sensíveis:* `users.cpf varchar(255) unique` (`V1__esquema_base.sql:15`) e cerca de 36 colunas `TEXT` clínicas em texto puro (`V1:72-77,94-104,158-178,414`, mais V10/V12/V13); o único `AttributeConverter` é o `ScaleAnswersConverter` (JSON). A trilha de auditoria (`V7:7-24`) guarda só ids, operação e tipo. O CSV nominal leva nome, CPF, e-mail, telefone e endereço (`ExportService.java:36`). O `.gitignore:14` tem `/backups/`, então um dump no destino padrão não entra no git por acidente.

**Risco real para este sistema.** Hoje o risco é o trânsito: senha, cookie, prontuário e CSV em claro no wi-fi da clínica. Depois disso, o risco em repouso é um dump inteiro (prontuário de todo mundo, CPF, anamnese) saindo do servidor para bucket ou pendrive sem cifra, ou o disco ser levado. Cifra por campo **não** é a resposta proporcional: não protege do ataque mais provável (API comprometida com a chave no mesmo ambiente), quebraria o `existsByCpf` (`PatientService.java:86`, `PrescriberService.java:59`), o CSV nominal e o histórico, e traria rotação de chave e migração de dezenas de colunas para um código que quer ser simples. Para até 300 usuários num servidor próprio, o conjunto "apto" da LGPD (art. 46) é TLS + disco cifrado + backup cifrado + o controle de acesso que já existe.

**Correção concreta.**

1. HTTPS: #57 (item 19).
2. `application.yml`, dentro de `spring.mail.properties`, uma linha: `"[mail.smtp.starttls.required]": ${MAIL_SMTP_STARTTLS:true}` (servidor local de teste com `MAIL_SMTP_STARTTLS=false` continua desligando os dois).
3. Backup cifrado com `age` (um binário; a chave pública fica no servidor e a privada fora dele, então quem leva o servidor não lê os backups antigos):

```sh
# em backup-banco.sh, logo depois do pg_dump
age -r "$BACKUP_AGE_PUBKEY" -o "$ARQUIVO.age" "$ARQUIVO" && rm "$ARQUIVO"
# a limpeza passa a procurar doisag-*.dump.age
# em restaurar-banco.sh
age -d -i "$BACKUP_AGE_KEY" "$ARQUIVO" | pg_restore --dbname "$BANCO" ...
```

`BACKUP_AGE_PUBKEY` no `.env.example` e uma frase no `docs/backup-e-restauracao.md`. Se não quiserem instalar nada, `gpg --batch --symmetric --cipher-algo AES256 --passphrase-file` faz o mesmo.

4. No guia #18: disco ou volume do servidor cifrado (LUKS ou a cifra do provedor) e a chave privada do backup guardada fora do servidor.
5. Registrar a decisão de não cifrar por campo e revê-la só se o banco for para host compartilhado ou nuvem de terceiro.

**Card.** #57, #17, #18, #19 e #103 (o `SESSION_SECURE_COOKIE` anda junto com o TLS). STARTTLS obrigatório e backup cifrado estão no #117.

---

## 6 — Auth server side

**Situação.** ok, gravidade baixa.

**Evidência.** Toda decisão fica no servidor. `SecurityConfigurations.java:41-61` é stateless, com lista fechada de oito rotas públicas (login, logout, register, password-reset request/confirm, `GET /invites/{token}`, `GET /consent-term`, `GET /health`), `/admin/**` só ADMIN, `/auth/me` e `/profile/**` qualquer papel logado e `anyRequest().hasAnyRole(PATIENT, PRESCRIBER)` na linha 61 — nega por padrão. Existe uma cadeia só: `grep` por `WebSecurityCustomizer`, `ignoring(`, `FilterRegistrationBean` e `@Order` em `src/main` volta vazio, e sem actuator, h2 ou devtools no jar não há nada respondendo por fora dela. O `SecurityFilter.java:57-98` recarrega a conta do banco em toda requisição (`:60`), recusa conta inativa (`:63`), token emitido antes de `passwordChangedAt` (`:69-71`), token emitido até `sessionsEndedAt` (`:76-78`) e sessão passada do máximo contado do login (`:81-83`); token adulterado ou vencido cai no `catch` e vira 401 (`:93-97`). O `TokenService.java:34` usa `Keys.hmacShaKeyFor`, que lança `WeakKeyException` abaixo de 32 bytes, e o claim `role` (`:51`) nunca é usado para autorizar: as authorities vêm da entidade do banco (`SecurityFilter:86`, `Users.java:165-167`). `/auth/register` é público mas só aceita convite de uso único travado no banco (`PatientService.java:76-78`), e o prescritor vem do convite (`:90`), não do corpo. O logout só encerra sessões do principal autenticado (`AuthenticationController.java:52-59`). O front não guarda nada da sessão: `api.js:86` carrega o usuário de `GET /auth/me` na abertura, `grep` por `localStorage`/`sessionStorage` em `frontend/src` só acha o tema, e o guard de rota (`route-guards.js:11-22`) diz no próprio comentário que a API confere tudo de novo. Testes: `SessionTest.java:211-218,249-257,270-274`, `ProfileTest.java:233-234`, `PasswordResetTest.java:236-237`, `AdminPrescribersTest.java:156-157`, `ProtectedRoutesTest.java:60-91` (token forjado, assinatura errada, vencido), `RouteRolesTest.java:122-131` (toda rota privada dá 401 sem sessão).

**Risco real para este sistema.** Baixo. Dois detalhes: (1) `PasswordService.java:49` trunca `passwordChangedAt` para segundos enquanto o token guarda milissegundos (`TokenService:52`), então uma sessão emitida no mesmo segundo da troca sobrevive — e como a renovação (`SecurityFilter:89-91`) empurra o `issuedAt` para frente, ela dura até o máximo de 12 h; janela menor que 1 s, exige atacante já logado naquele instante. (2) `SecurityFilter.java:47-52` aceita `Authorization: Bearer` em produção, não só nos testes, e esse caminho pula o `OriginCheckFilter`; como o token nunca sai do cookie httpOnly, não abre nada hoje, mas é superfície a mais. Fora isso, até o #57 fechar, senha e prontuário trafegam em texto puro, o que anula o `Secure` do cookie e o resto da cadeia.

**Correção concreta.** Em `PasswordService.applyNewPassword`, trocar `truncatedTo(ChronoUnit.SECONDS)` por `ChronoUnit.MILLIS`, igual ao `AuthService.endSessions` (`:73`), e atualizar o comentário da linha 48; o `SecurityFilter:104` compara com `isBefore` estrito, então o cookie novo gravado logo depois (`ProfileController:70-71`) continua valendo. Opcional: só ler o header `Authorization` quando `api.session.accept-bearer=true`, ligado apenas no `application-test.yml`. Fechar o #57 é o pré-requisito de tudo isso valer.

**Card.** #57; a truncagem já está no #109 (AUTZ-08).

---

## 7 — Restringir acessos

**Situação.** parcial, gravidade média.

**Evidência.** A matriz de papéis está no servidor e é verificada por varredura. São 85 rotas (`grep` de `@Get/Post/Put/DeleteMapping` em `controller/`), 77 privadas — 64 com `@PreAuthorize` no método e 13 que herdam a regra da classe (`AdminPrescriberController`, `NotificationController`, `ProfileController`) — e 8 públicas. O `RouteRolesTest.java:102-120` percorre todo `RequestMappingInfo` e quebra o build se uma rota privada vier sem regra; `:134-151` prova que o admin recebe 403 em tudo que não é `/admin/**`, `/profile` ou `/auth/me`; `:153-194` paciente 403 em registrar consulta, prescrever, MEEM, designar escala e protocolo; `:196-209` prescritor 403 em `/admin/*`; `PatientLinkTest.java:173-186` admin 403 em todas as leituras clínicas. Há duas camadas no admin: além do `@PreAuthorize`, `SecurityConfigurations.java:57` põe `/admin/**` em `hasRole ADMIN` e `:61` tira ADMIN do `anyRequest`, então um controller novo sem anotação já barraria o admin fora de administração. `PatientAccessService.java:50` devolve `false` para qualquer principal que não seja Patient ou Prescriber. Recursos sem `patientId` na URL amarram no principal: avisos (`NotificationService.java:69-76`), agenda e pedidos do prescritor (`AppointmentsController.java:83-97`), disponibilidade (`AvailabilityController.java:28-37`), convite (`PatientInviteController.java:31-32`), perfil (`ProfileController.java:42-70`, nenhuma rota recebe id), painel com `isSelf` (`DashboardController.java:22`). `/admin/audit-events` só devolve quem fez o que e quando, com `patientId` numérico e sem nome nem conteúdo (`AuditEventDTO.java:9-18`). O prescritor do paciente é gravado uma vez no cadastro (`PatientService.java:99`) e nunca muda. Sessão roubada não se entrincheira: trocar senha e trocar e-mail exigem a senha atual (`PasswordService.java:34`, `ProfileService.java:57`) e qualquer troca derruba as sessões.

O que fura: (a) #61 — o admin escolhe a senha inicial do prescritor (`PrescriberCreateDTO.java:101` ainda tem o campo `password`, `PrescriberService.java:73` grava) e pode entrar como ele e abrir toda a carteira; a trilha registra como se fosse o prescritor. (b) `POST /admin/prescribers/{id}/password-reset` devolve o link de senha nova no corpo (`AdminPrescriberController.java:55-60`, `PrescriberService.java:107-110`), mitigado pela confirmação da senha do admin (`:98`) e pelo evento `REDEFINICAO_DE_SENHA` na trilha (`:108`), mas ainda é caminho para trocar a senha de um prescritor ativo sem ele saber. (c) #62 — não existe `PUT /admin/patients/{id}/active`; arquivar não tira o acesso. Menor do que parece: paciente arquivado já perde os caminhos de escrita (`AppointmentService.java:136`), a sessão dura 120 min sem uso e 12 h desde o login (`application.yml:62-64`), e o próprio paciente derruba tudo pedindo link de senha nova; o que falta é a clínica travar uma conta comprometida sem depender do paciente. (d) #83 — convite vazado vale 7 dias (`PatientInviteService.java:25`) e o prescritor não lista nem cancela. (e) #84 — `AdminAccountCreator.java:47-48` nunca reaplica `ADMIN_PASSWORD` depois da primeira subida, e o front não expõe `/profile/password` ao admin; a API já aceita ADMIN em `/profile/**`, então é só tela.

**Risco real para este sistema.** Médio. A regra central "admin não vê prontuário" é furada por tomada de conta: pelo #61 o administrador conhece a senha de todo prescritor que criou, e pelo reset no corpo da resposta consegue trocar a de um prescritor ativo. O resto são contas que ficam vivas além do que deveriam.

**Correção concreta.**

- (#61) Tirar o campo `password` do `PrescriberCreateDTO`; em `PrescriberService.create` gravar um hash de senha aleatória (`SecureTokens.createRandomToken`) e chamar `passwordResetService.createLinkFor(prescriber)`, devolvendo o link na resposta igual já faz o reset. O prescritor define a própria senha e o admin nunca a conhece.
- (reset) Em `PrescriberService.startPasswordReset`, só devolver `resetLink` no DTO quando o `EmailSender` for o de log (sem `MAIL_HOST`); com SMTP configurado, devolver `null` e deixar o e-mail entregar. Manter `recordPasswordReset`.
- (#62) `PUT /admin/patients/{id}/active` reusando o padrão `changeActive` + `ChangeActiveDTO`; o `SecurityFilter:63` já derruba a sessão.
- (#83) `GET /invites` (lista do prescritor logado) e `DELETE /invites/{id}` que grava `usedAt`, com `hasRole('PRESCRIBER')` e `findByIdAndPrescriberId`.
- (#84) Ligar a tela de perfil para o admin. Extra sem tela: `AdminAccountCreator` reaplicar o hash quando `ADMIN_PASSWORD` mudar (comparar com `passwordEncoder.matches` e chamar `PasswordService.applyNewPassword`), assim trocar no `.env` e subir de novo já rotaciona a senha.

**Card.** #61, #62, #83, #84 (#95 é funcional, não de acesso). O link de reset no corpo cabe como nota no #61.

---

## 8 — Bloquear mass assignment

**Situação.** ok, gravidade nenhuma.

**Evidência.** Nenhum controller recebe entidade JPA no `@RequestBody`: os 30 `@RequestBody` em `controller/*.java` são records da pasta `dto`, e o único tipo de `model` em assinatura de controller é o `@AuthenticationPrincipal`. Não existe `@ModelAttribute`, `@InitBinder`, `DataBinder`, `BeanUtils.copyProperties`, `ModelMapper` nem `MapStruct` em `src/main`, e nenhum método de controller tem parâmetro POJO sem anotação (o vetor clássico, em que o Spring amarra pela query string). Não há `spring-data-rest` nem `@RepositoryRestResource`. Os DTOs de escrita só têm campo que a pessoa pode mexer: `RegisterDTO.java:16-52` sem `role`/`active`/`prescriberId` (comentário na linha 15 explica que o prescritor vem do convite, `PatientService.java:90-99`); `ProfileUpdateDTO.java:13-26` só nome, nascimento, telefone e endereço, copiados um a um em `ProfileService.java:43-49`; `ConsultationRecordDTO.java:15-39` sem `patientId`/`prescriberId`/`status`, com `ConsultationService.java:70-74` setando paciente pelo path gated, prescritor pelo principal e status fixo; `TreatmentProtocolCreateDTO.java:14-27` sem paciente/prescritor/active (`TreatmentProtocolService.java:79-83`); `PrescriberCreateDTO.java:13-46` só admin e sem `active`/`role`. Os demais serviços também copiam campo por campo (`PrescriberService:63-74`, `AnamnesisService:110-132`, `PrescriptionService:68-101`, `AppointmentService:104-120`, `AvailabilityService:50-62`). `role` nem é campo: `Users.getRole()` é abstrato (`Users.java:160`) e resolvido pela subclasse JPA `JOINED`; `active` nasce `true` em `Users.java:38` e só muda por `ChangeActiveDTO` com `hasRole('ADMIN')`. O único DTO com FK, `AppointmentScheduleDTO.patientId`, é barrado no `@PreAuthorize`. Campo desconhecido é descartado: o Jackson 3.1.5 nasce com `FAIL_ON_UNKNOWN_PROPERTIES=false` (conferido com `javap` no jar) e o `spring-boot-jackson 4.1.1` ainda o desliga explicitamente — dois padrões independentes dizendo a mesma coisa, e o `application.yml` não mexe em `spring.jackson`. A única entrada de forma aberta, o `Map<String,Object> answers` do `ScaleResponseCreateDTO`, é filtrada contra o catálogo (`ScaleResponseService.java:293-304` rejeita chave desconhecida, `:311` rejeita item calculado, `:283-288` e `ScaleScorer.java:191-194` refazem escore e campos calculados no servidor) e o paciente é resetado pelo principal (`:120`). Já há teste: `PatientLinkTest.java:220-268` manda `"patient":{"id":B}` e `"appointment":{"id":B}` e confere que o registro continua do A. No histórico, os `@RequestBody` de entidade saíram em `3e47583`, `c5c9a8b` e `76bb490`.

**Risco real para este sistema.** Nenhum realista. O único efeito colateral de ignorar campo desconhecido é que um erro de digitação no front some em silêncio, assunto de QA.

**Correção concreta.** Nada obrigatório. Para trancar o futuro, um teste pequeno (em `PatientSignUpTest` e `ProfileTest`) que manda `{"role":"ADMIN","active":false,"prescriberId":1,"id":999}` junto com um corpo válido em `/auth/register` e `/profile` e confere que role, active e prescritor não mudaram.

**Card.** Nenhum. O teste de tranca entra no #124 (item 4).

---

## 9 — Proteger cookies

**Situação.** parcial, gravidade alta (pelo mesmo motivo do item 19).

**Evidência.** O cookie em si está certo. `SessionCookieService.java:60-68` monta o único cookie da aplicação (`session`) com `httpOnly(true)`, `secure(secureCookie)`, `sameSite("Strict")`, `path("/")`, sem `Domain` e `maxAge` igual à duração da sessão; não existe outro `ResponseCookie`, `addCookie` ou `HttpSession` em `src/main`, e a cadeia é `STATELESS` (`SecurityConfigurations.java:42`), então não há `JSESSIONID`. Os três escritores passam pelo mesmo método: login, registro e troca de senha do próprio usuário (`ProfileController.java:65-72`); o reset por e-mail não grava sessão. CSRF: o filtro do Spring está desligado (`:41`) e é compensado por `SameSite=Strict` mais o `OriginCheckFilter.java:45-74` — POST/PUT/PATCH/DELETE com cookie só passa se `Origin` for exatamente a origem do `PUBLIC_URL` ou, sem `Origin`, se o `Referer` começar com ela; sem os dois, 403; a API nem sobe com `PUBLIC_URL` inválido (`:36-39`). `OriginCheckTest.java:64-128` cobre outro site, subdomínio parecido, sem cabeçalho e todos os métodos; login-CSRF por formulário cross-site morre em 415 (`ErrorHandler.java:118-121`) e não há CORS configurado. Vida útil: 120 min renováveis (`TokenService:55`, `SecurityFilter:28,89-92`) com teto de 12 h desde o login (`:81`); logout grava `sessionsEndedAt` e limpa com `Max-Age=0` (`AuthService:71-76`, `SessionCookieService:43-45`); cookie guardado não volta (`SessionTest.java:200-208,250-267`). O token nunca aparece no corpo (`SessionUserDTO.java:6`) nem em log, e o front não tem `document.cookie` nem `credentials:` no fetch.

O que falta: `Secure` tem padrão `true` em `application.yml:66` e `docker-compose.yml:46`, mas o `.env.example:28` entrega `SESSION_SECURE_COOKIE=false`, e o valor explícito vence o padrão do compose (#103); e não há TLS (#57). O `SessionTest.java:113-118` só vê `Secure` porque o `application-test.yml` não tem bloco `api.session`, então ele não protege contra a configuração de deploy, e o CI não sobe a pilha (#102). `passwordChangedAt` truncado em segundo (`PasswordService.java:49`, #109 AUTZ-08). O mesmo JWT vale via `Authorization: Bearer` (`SecurityFilter:48-51`), caminho que pula o `OriginCheck` (`OriginCheckFilter:58-62`). Detalhe de desenho: o cookie é persistente (`Max-Age=7200`), não de sessão do navegador, então fechar o navegador num computador compartilhado da clínica não encerra nada; os limites de 2 h e 12 h cobrem.

**Risco real para este sistema.** O cookie viaja em texto puro: sem TLS e com `Secure=false` saindo de fábrica, quem estiver no wi-fi da clínica copia o cookie e usa a sessão por até 2 h sem uso ou 12 h desde o login, com acesso ao prontuário — e como o mesmo JWT vale como Bearer, o token copiado nem precisa passar pelo origin check. Depois do HTTPS, a proteção fica boa.

**Correção concreta.**

1. Junto com o #57: `SESSION_SECURE_COOKIE=true` no `.env.example`, com comentário avisando que em localhost tem que ser `false` (#103).
2. Depois do HTTPS, prefixo `__Host-` quando `secureCookie` for `true`, em `SessionCookieService`:

```java
// o navegador recusa cookie __Host- sem Secure, com Domain ou fora de Path=/
// entao configuracao errada no servidor aparece na hora
private String cookieName() {
    return secureCookie ? "__Host-" + COOKIE_NAME : COOKIE_NAME;
}
```

usado no `addCookie` e no `readToken`.

3. Conferir na subida, no estilo do `OriginCheckFilter:36-39`: `PUBLIC_URL` com `https` e `secure-cookie=false` recusa subir (ou ao menos avisa), e `http` com `true` também (login ia falhar em silêncio, porque o navegador descarta cookie `Secure` em http fora de localhost). É exatamente o erro que o #103 chama de invisível.
4. `passwordChangedAt` em milissegundo (#109).
5. Opcional: só aceitar `Bearer` com `api.session.accept-bearer=true` no perfil de teste.

**Card.** #57, #103, #109. `__Host-` e a conferência na subida estão no #118.

---

## 10 — Hash nas senhas

**Situação.** ok, gravidade baixa.

**Evidência.** `SecurityConfigurations.java:73-76` cria `new BCryptPasswordEncoder()` (bcrypt `$2a`, custo 10, salt aleatório por senha), via `spring-security-crypto 7.1.1`. `grep setPassword(` em `src/main` só acha chamadas com `passwordEncoder.encode` (`PasswordService.java:47`, `PrescriberService.java:73`, `PatientService.java:98`, `AdminAccountCreator.java:57`, `DevDataSeed.java:71,110`, `DevDemoData.java:442`). `Users.java:171-175` tem `@JsonIgnore` no `getPassword`; `PasswordExposureTest` bate em `/patients`, `/patients/{id}`, `/profile` e `/scales/responses/{id}` e `SessionTest.java:110` confere que `$.password` não sai no login. Regra: `PasswordRules.java:9` exige 8 a 64 caracteres com maiúscula, número e símbolo, aplicada por `@Pattern` em `RegisterDTO:46`, `ChangePasswordDTO:13`, `PasswordResetDTO:12` e `PrescriberCreateDTO:22`; a troca exige a senha atual e recusa repetir (`PasswordService.java:34-39`). Login compara contra um hash fantasma quando o e-mail não existe (`AuthService.java:36,50`). Nenhum `log.*` do `main` imprime senha ou token; `LogEmailSender.java:23-27` só mostra o link com `EMAIL_LOG_TEXT=true` (padrão `false` em produção, `true` só no `application-test.yml:38`). Tokens de reset e convite: 32 bytes do `SecureRandom` e só o SHA-256 no banco (`SecureTokens.java:21-31`, `PasswordResetService.java:91`, `PatientInviteService.java:44`). O admin já consegue trocar a própria senha pela API (`/profile/**` liberado para ADMIN).

Furos pequenos: (a) `AdminAccountCreator.java:50-52` só avisa no log se `ADMIN_PASSWORD` tem menos de 12 caracteres e não aplica o `PasswordRules`; e com mais de 72 bytes em UTF-8 o `encode` lança `IllegalArgumentException` dentro do `ApplicationRunner` e a API nem sobe. (b) O bcrypt do Spring recusa senha acima de 72 bytes (detalhado no item 14), `LoginDTO.java:10-11` não tem `@Size`, e `ErrorHandler.java:66-69` transforma isso num 400 com mensagem em inglês que não conta no limitador. (c) O token cru de reset vai parar no access log do nginx: o link é `PUBLIC_URL/redefinir-senha?token=<cru>` (`PasswordResetService.java:96`), a tela de cadastro chama `GET /api/invites/<cru>`, e `frontend/nginx.conf` não tem `access_log` nem `log_format` próprio, então vale o `main` da imagem `nginx:alpine`, que grava `$request` com query string. O banco guarda só o hash, mas `docker logs web` tem o token cru durante os 30 minutos de validade. (d) #61 e #84.

**Risco real para este sistema.** Baixo. Hash certo em todo caminho, senha nunca aparece em log nem em resposta, tokens de uso único só como hash. Se o banco vazar, as senhas estão em bcrypt com regra forte, suficiente para 300 usuários. O token no log do nginx exige ler o log do servidor (quem lê já é admin) e vence rápido, mas enfraquece o "só o hash existe".

**Correção concreta.**

1. `@Size(max = 64, message = "A senha pode ter até 64 caracteres")` em `LoginDTO.password`.
2. `PasswordRules.fitsInBcrypt(String password)` conferindo `password.getBytes(UTF_8).length <= 72`, chamado antes do `encode` em `PasswordService.applyNewPassword`, `PatientService.registerPatient` e `PrescriberService.create` (item 14 tem o esboço).
3. `AdminAccountCreator`: aplicar o mesmo `PasswordRules.PATTERN` e `fitsInBcrypt`, não criar a conta quando falhar, com `log.warn` dizendo o motivo.
4. nginx: `log_format` sem query string (`$uri` em vez de `$request`) — #120, item 15.
5. Opcional: `new BCryptPasswordEncoder(12)` (login uns 250 ms, invisível para 300 pessoas; os hashes antigos continuam valendo porque o custo vai dentro do hash).

**Card.** #61, #84.

---

## 11 — Rate limit

**Situação.** parcial, gravidade média.

**Evidência.** *Login (limitado):* `LoginAttemptLimiter.java:44-64`, mapa em memória, 5 erros por (e-mail, endereço) e 20 por endereço somando e-mails, bloqueio de 15 min (`application.yml:54-59`); `isBlocked` roda antes do bcrypt (`AuthService.java:43-46`) e responde 429 (`ErrorHandler.java:144-147`); testes `SessionTest.java:156-183` e `LoginAttemptLimiterTest.java:23-79`. Zera ao reiniciar (uma instância só). O endereço vem de `request.getRemoteAddr()` com `forward-headers-strategy: native` (`application.yml:9`) e o nginx manda `X-Forwarded-For $proxy_add_x_forwarded_for` (`nginx.conf:19`), que **apenda** o cabeçalho do cliente em vez de substituir; sem `server.tomcat.remoteip.*`, vale o `internal-proxies` padrão do Boot (10/8, 172.16-31, 192.168/16, 127/8), então cliente que chega de IP privado (LAN da clínica acessando direto) consegue falsificar o endereço à vontade. Por escolha (`LoginAttemptLimiter.java:15-16`, teste `wrongPasswordsFromAnotherAddressDoNotBlockTheOwner`) não há teto por conta somando endereços: quem tem N endereços tem 5·N chutes por conta a cada 15 min — a senha forte obrigatória (`PasswordRules.java:9`) é o que segura. *Senha nova (meio limitado):* `PasswordResetService.java:30,64-69` limita a 3 pedidos por hora **por conta**; não há limite por endereço, e-mail sem conta só faz um select, e o envio SMTP é síncrono dentro da requisição e da transação (`:57,97`, `SmtpEmailSender.java:30-31`, sem `@Async` em lugar nenhum, timeouts de 5 s em `application.yml:44-46`, 200 threads padrão do Tomcat). *Cadastro (sem limite):* `/auth/register` só é segurado pelo convite de 32 bytes (`PatientService.java:76-79`) e a tentativa que falha não gasta o convite (#82); `GET /invites/{token}` e `POST /invites` sem limite. *Autenticado e sem limite:* pedido de consulta, free-slots, respostas de escala, avisos, export CSV (`ExportController.java:35-74`, monta o dataset inteiro a cada chamada, custo em #99). *nginx:* sem `limit_req` nem `limit_conn`.

**O furo mais concreto:** três rotas conferem a senha atual com bcrypt e **não** passam pelo limitador (o `grep` de `isBlocked`/`registerFailure` só acha `AuthService.java:43-56`): `PUT /profile/password` (`ProfileController.java:66-73` → `PasswordService.java:34`), `POST /admin/prescribers/{id}/password-reset` (`PrescriberService.java:96-100`) e `PUT /profile/email` (`ProfileController.java:52-56` → `ProfileService.java:57`). O último é o pior: quem acerta troca o e-mail de acesso e passa a receber o link de senha nova (`PasswordResetService.java:97` manda para o `user.getEmail()` atual), tomando a conta e o caminho de recuperação de uma vez — o comentário em `EmailChangeDTO.java:8` diz que a senha é pedida justamente por isso. Nenhum teste cobre bloqueio nessas rotas.

**Risco real para este sistema.** Médio. Para 300 usuários o login está bem coberto e os tokens são inchutáveis. O que sobra: (a) força bruta da senha atual a partir de sessão esquecida no PC da recepção ou cookie copiado, uns 10 bcrypt/s por thread sem bloqueio, virando tomada definitiva da conta — lento demais para senha boa, então o alvo real é conta com senha fraca ou previsível; (b) alguém com uma lista de e-mails dispara até 3 e-mails/hora por conta (900/h) pelo SMTP da clínica, e 200 pedidos simultâneos de contas reais param todos os workers por até 5 s; (c) sem `limit_req`, o único freio de quem martela de vários IPs é o limitador do login.

**Correção concreta.**

1. Reusar o `LoginAttemptLimiter` nas três rotas: o controller passa `request.getRemoteAddr()` igual ao login, o serviço chama `isBlocked(user.getEmail(), address)` antes do `matches` e `registerFailure` quando erra, lançando `LoginBlockedException` (já vira 429 no `ErrorHandler`). Testes espelhando `SessionTest.java:156-162`: 5 senhas atuais erradas em `PUT /profile/password` e `PUT /profile/email` → 429 até com a senha certa; o mesmo em `AdminPrescribersTest` para o reset.
2. No mesmo limitador, contar pedido de senha nova por endereço (ex.: 10 em 15 min) antes do select, e mandar o e-mail fora da transação (a mesma coisa que o #94 pede para o job).
3. nginx, no `frontend/nginx.conf` (o `nginx:alpine` inclui `conf.d` dentro do `http{}`, então a zona pode ficar nesse arquivo):

```nginx
# freio por ip: login e senha nova mais apertados q o resto da api
limit_req_zone $binary_remote_addr zone=auth:1m rate=10r/m;
limit_req_zone $binary_remote_addr zone=api:1m rate=20r/s;

server {
    ...
    location /api/auth/ {
        limit_req zone=auth burst=20 nodelay;
        limit_req_status 429;
        proxy_pass http://api:8080;
        # mesmos proxy_set_header do location /api/
        proxy_set_header X-Forwarded-For $remote_addr;  # descarta o q o cliente mandou
    }
    location /api/ {
        limit_req zone=api burst=40 nodelay;
        limit_req_status 429;
        ...
    }
}
```

e trocar a linha 19 por `proxy_set_header X-Forwarded-For $remote_addr;` — uma linha que fecha a falsificação a partir de IP privado.

4. #82 para o cadastro.

**Card.** #82 (cadastro), #94 (e-mail fora da transação), #111 (nginx — o corpo fala de log, cache e rota órfã, não de `limit_req`, então é estender). A força bruta da senha atual e o `limit_req` não têm card: #119 (limitador) e #120 (nginx) (duas).

---

## 12 — Bot protection

**Situação.** parcial, gravidade baixa.

**Evidência.** Nenhum captcha, Turnstile ou honeypot no front nem na API (`grep` por `captcha`, `recaptcha`, `turnstile`, `hcaptcha`, `honeypot`: vazio; `grep` por "bot" só acha "botao"). Rotas públicas que gravam (`SecurityConfigurations.java:44-49`): `/auth/login`, `/auth/logout`, `/auth/register`, `/auth/password-reset/request` e `/confirm`. Cadastro só por convite de 32 bytes, 7 dias, uso único com `select for update` (`PatientInviteService.java:40-49,83-93`, `PatientService.java:76-79`), criado só por prescritor logado — não há cadastro aberto para bot criar conta. Login tem o limitador com resposta igual para e-mail sem conta (`SessionTest.java:164-172`); reset tem 3/h por conta e token de 256 bits. `POST /auth/register` sem convite válido cai em duas consultas baratas sem bcrypt (o `encode` só roda em `PatientService.java:98`, depois do convite e das unicidades), então não há amplificação de CPU. `GET /api/health` fica público pelo nginx e roda `select 1` por chamada, mas o healthcheck do compose chama `localhost:8080` por dentro do container (`docker-compose.yml:67`). Dois pontos nunca verificados de ponta a ponta: o limite por endereço depende do IP real chegar ao Tomcat via `X-Forwarded-For`, e `SessionTest.loginFrom` (`:85-91`) usa `MockMvc.setRemoteAddr`, que passa por fora do `RemoteIpValve`; o CI nunca sobe o compose (#102). E um detalhe para quem calibrar: `registerSuccess` só apaga a chave `email:` (`LoginAttemptLimiter.java:57-59`), a chave `address:` nunca zera com acerto — 20 senhas erradas somadas no NAT da clínica em 15 min travam o consultório inteiro por 15 min.

**Risco real para este sistema.** Baixo. Sem cadastro aberto não há spam de conta; limitador mais senha forte deixam o chute lento mesmo com muitos IPs. Captcha não se justifica para 300 pessoas e atrapalharia paciente idoso no celular. O que sobra é flood de e-mail de senha nova e enumeração, que o item 11 e o #82 resolvem barato. Armadilha para o #57/#18: se o HTTPS for terminado por Caddy/Traefik na frente do serviço `web`, o nginx do compose passa a ver 127.0.0.1 ou 172.x como cliente de todo mundo; se o proxy de fora não acrescentar o IP real, um bot escolhe o próprio "endereço" por requisição e zera as duas chaves do limitador (Caddy e Traefik acrescentam por padrão).

**Correção concreta.**

1. O `limit_req` em `/api/auth/` (item 11) corta a maior parte de bot burro e vale para login, cadastro e senha nova de uma vez.
2. Honeypot simples nos formulários de cadastro e esqueci-senha: um campo `site` escondido por CSS (`position: absolute` fora da tela e `tabindex=-1`, não `display:none`) que `RegisterDTO` e `PasswordResetRequestDTO` recebem como `String` opcional; se vier preenchido, a API responde como se tivesse dado certo (o reset já é 204 sempre; no cadastro, o mesmo 400 genérico do convite inválido) sem gravar nada. Sem dependência nova.
3. Teto global por e-mail bem alto no `LoginAttemptLimiter` (ex.: 100 erros em 15 min somando todos os endereços, terceira chave `email-total:`) para frear botnet sem deixar fácil travar uma vítima.
4. No smoke test do #102: 20 logins errados pela porta pública exigindo 429, o que prova que o IP real chega ao limitador.
5. De graça: `location = /api/health { return 404; }` no nginx, já que nada precisa dele exposto.

**Card.** #82, #102, #57. Honeypot e `email-total` entram no #119.

---

## 13 — Queries parametrizadas

**Situação.** ok, gravidade nenhuma.

**Evidência.** Não existe `nativeQuery`, `createNativeQuery`, `createQuery` nem `EntityManager` em `src/main` — e `git log -S` por esses termos no backend nunca retornou commit: SQL cru montado nunca existiu no projeto. As únicas `@Query` são JPQL com parâmetro nomeado: `AuditEventRepository.java:25-38`, `NotificationRepository.java:27-29`, `PasswordResetRepository.java:18`, `PatientInviteRepository.java:20`, `ScaleTaskRepository.java:48-57`; a concatenação com `+` nesses arquivos só cola pedaços literais. O resto é derived query, sem `Like`, `Containing`, `StartingWith`, `Example.of` nem `ExampleMatcher`, então nem wildcard em `LIKE` existe. O único SQL cru é `HealthController.java:25`, `queryForObject("select 1", Integer.class)`, constante. Nenhuma entidade usa `@Formula`, `@Where`, `@SQLRestriction`, `@Filter`, `@NamedNativeQuery`, `@ColumnTransformer` ou `@Subselect`. Ordenação e paginação nunca vêm da requisição: nenhum controller recebe `Pageable` ou `Sort` (nunca receberam, `git log -S'Pageable'` em `controller/` vazio); `AuditService.java:166-167` e `NotificationService.java:78-79` montam `PageRequest.of(Math.max(page, 0), PAGE_SIZE)` com tamanho fixo; `PrescriberService.java:79` usa `Sort.by("name")` fixo. As únicas `@PathVariable String` são `slug` e `token`: o slug vira enum em `ScaleType.fromSlug` (`:84-88`, desconhecido dá 404) e o token passa por `SecureTokens.hashToken` antes do `findByTokenHash`. Parâmetros de data e enum passam pela conversão do Spring e valor errado vira 400 (`ErrorHandler.java:72-80`, `ErrorHandlingTest.java:121-127`). As migrações não têm placeholder `${}` e não existe `schema.sql`/`data.sql`. Bônus que encosta nos itens 14 e 15: `CsvBuilder.java:40-53` já protege contra injeção de fórmula no CSV (apóstrofo em célula que começa com `=`, `+`, `-`, `@`, tab ou CR).

**Risco real para este sistema.** Nenhum.

**Correção concreta.** Nada a fazer. Manter a regra: nada de `nativeQuery` com string montada e nada de `Pageable`/`Sort` vindo direto do request (se um dia precisar de ordenação pela tela, aceitar um enum fixo e mapear para o `Sort` no serviço). Observação menor para o item 15: `ScaleType.java:88` devolve o slug digitado dentro da mensagem do 404.

**Card.** Nenhum.

---

## 14 — Validação dos inputs

**Situação.** parcial, gravidade baixa.

**Evidência.** *O que está certo:* todos os `@RequestBody` têm `@Valid`, inclusive listas aninhadas (`PrescriptionCreateDTO.java:30-31,43-44`, `TreatmentProtocolCreateDTO.java:25`, `AvailabilityDTO.java:22` com `List<@NotNull @Valid ...>`). E-mail `@Email + @Size(255)` em `RegisterDTO.java:40-43` e `EmailChangeDTO.java:10-13`; CPF `@CPF` em `RegisterDTO:24-26` e `PrescriberCreateDTO:25-27` com normalização em `InputCleaner.java:20-25`; telefone `^[0-9]{10,11}$`; senha `PasswordRules.java:9`; datas `@Past`/`@PastOrPresent`; faixa numérica em peso, altura, duração e dia da semana. Mapeamento de erro: JSON quebrado, enum inválido ou data inválida → 400; path variable de tipo errado → 400; content-type errado → 415; `@Valid` → 400 com lista de campo (`ErrorHandler.java:72-99,118-122`, `ErrorHandlingTest.java:66-135`). As respostas de escala são conferidas item a item contra o catálogo (`ScaleResponseService.java:293-316,351-372,400-402`). Intervalos de data limitados (`AppointmentService.java:336-341`, `AuditService.java:160-162`). Paginação com `page` clampado e `PAGE_SIZE` fixo. Texto clínico longo é de propósito (RN11): colunas `TEXT` e testes gravando 10.000 caracteres (`AnamnesisTest.java:107-111`, `ConsultationRecordTest.java:130-137`). O `CsvBuilder.java:35-54` protege contra fórmula no CSV.

*O que falta:*

1. Nenhum limite de tamanho de corpo em lugar nenhum da pilha: `application.yml` não tem `server.tomcat.max-swallow-size` (e isso nem limita JSON), não há `StreamReadConstraints`, o Jackson 3 aceita string de até 20 M caracteres, e `frontend/nginx.conf:15-21` não tem `client_max_body_size` — vale o padrão de 1 MB do nginx, e só no caminho do Docker; no dev local não há teto.
2. Texto livre sem `@Size` caindo em `varchar(255)`: `PrescriberCreateDTO.name/profession/registryType/registryNumber` (`:12-45` vs `V1:14,41,43,44`), `PrescriberCreateDTO.email` (`:17-19`, `@Email` aceita ~320 caracteres, coluna `V1:16`), `AddressDTO.street/number/city/country` (`:11-24` vs `V1:20-24`), e o `phone` do `PrescriberCreateDTO:32` é `^[0-9]*$` (aceita vazio e sem teto). Estouro vira `DataIntegrityViolationException` → 409 "Este registro já existe ou está em uso" (`ErrorHandler.java:156-160`) em vez de 400 apontando o campo; o repositório já tropeçou nisso (comentário em `PrescriptionCreateDTO.java:19-20`).
3. **Senha válida pela regra estoura o bcrypt.** O `spring-security-crypto 7.1.1` recusa senha acima de 72 bytes (`javap` no jar: `BCrypt.hashpw(byte[],String,boolean)` tem `bipush 72` seguido de `IllegalArgumentException("password cannot be more than 72 bytes")`, e `encodeNonNullPassword`/`matchesNonNull` não capturam). `PasswordRules.java:9` limita 8 a 64 **caracteres** e o comentário diz que acento conta como letra, então uma senha legítima pode ter até 256 bytes. Testado no `jshell` contra o jar: 40 × `ã` + `Senha1!` (47 caracteres, 87 bytes) estoura; 33 × `ã` + `Senha1!` (40 caracteres, 73 bytes) estoura; 64 ASCII passa. Consequência: senha com 37+ letras acentuadas ou 19+ emoji passa por `RegisterDTO`, `PrescriberCreateDTO`, `ChangePasswordDTO` e `PasswordResetDTO` e explode no `encode` (`PatientService.java:98`, `PrescriberService.java:73`, `PasswordService.java:47`) → `handleBusinessRule` (`ErrorHandler.java:66-69`) → 400 com a mensagem crua em inglês, enquanto o front diz que a regra foi cumprida (`password-rules.js:7` só conta `length`). Nos caminhos de `matches` (`AuthService.java:50,55`, `PasswordService.java:34`, `ProfileService.java:57`, `PrescriberService.java:98`) vira esse 400 em vez do 401 "E-mail ou senha inválidos", sem contar no limitador. Nenhum teste cobre senha não-ASCII.
4. Listas sem `@Size(max)`: `PrescriptionCreateDTO.components` e `escalationSteps`, `TreatmentProtocolCreateDTO.items`, `AvailabilityDTO.periods`; cada item vira insert (`PrescriptionService.java:82-92`).
5. `ErrorHandler.java:66-69` devolve `exception.getMessage()` cru para `IllegalArgumentException` — e o ponto 3 mostra que existe caminho com dado do usuário chegando lá. Menores: `cleanAnswers` devolve a chave desconhecida inteira na mensagem do 400; `wholeNumberInRange` usa `intValue()` (um `Long` 4294967297 vira 1); `getAgenda` não tem teto de intervalo como o `MAX_FREE_SLOT_DAYS`.

**Risco real para este sistema.** Com até 300 usuários autenticados, o risco é abuso de recurso e não vazamento: um usuário logado consegue mandar um prontuário de vários MB (TEXT aceita até 1 GB no Postgres) e inchar banco, CSV e log, ou uma prescrição com dezenas de milhares de componentes. O 72 bytes é bug funcional com mensagem errada, não brecha. Os 409 enganosos só confundem quem preenche o formulário. Nada expõe dado de outro paciente.

**Correção concreta.**

- (a) `frontend/nginx.conf`, dentro de `location /api/`: `client_max_body_size 2m;` (#111). Opcional no `application.yml`: `server.tomcat.max-swallow-size: 2MB`.
- (b) `@Size(max = 255)` em `PrescriberCreateDTO.name/profession/registryType/registryNumber/email`, `@Pattern("^[0-9]{10,11}$")` no `phone` igual ao `RegisterDTO`, `@Size(max = 255)` nos cinco campos do `AddressDTO`; no `LoginDTO`, `@Size(max = 255)` no e-mail e `@Size(max = 64)` na senha.
- (c) `@Size(max = 20)` em `components`, `@Size(max = 104)` em `escalationSteps` (bate com o `@Max(104)` da semana), `@Size(max = 10)` em `items`, `@Size(max = 50)` em `periods`.
- (d) Senha e bcrypt:

```java
// PasswordRules
// bcrypt so le os primeiros 72 bytes e o do spring recusa acima disso
// letra com acento e emoji ocupam mais de um byte
public static boolean fitsInBcrypt(String password) {
    return password.getBytes(StandardCharsets.UTF_8).length <= 72;
}
```

chamado antes do `encode` nos três serviços, lançando `InvalidFieldException("password", "A senha ficou longa demais: letra com acento e emoji contam mais, use até 72 bytes")`; em `AuthService.login`, senha que não cabe vira `BadCredentialsException` direto (401 igual senha errada, sem contar no limitador); no front, somar `new TextEncoder().encode(password).length <= 72` à regra de tamanho; teste de cadastro com 40 × `ã` + `Senha1!` esperando 400 com `field=password`.
- (e) Um teste por DTO mandando `"a".repeat(256)` e esperando 400 com o nome do campo, no estilo de `itemDeProtocoloVazioVira400ComOCampo` (`ErrorHandlingTest.java:94`).
- (f) Se quiser teto de sanidade sem ferir a RN11, `@Size(max = 100000)` nos textos clínicos ainda é "sem limite prático".

**Card.** #111 (corpo máximo no nginx), #82 (encosta no 409 de CPF/e-mail). O resto está no #120 e no #121.

---

## 15 — Vazar conteúdo

**Situação.** parcial, gravidade média.

**Evidência.** *O que está certo:* 500 vira corpo fixo e a pilha vai só para o log (`ErrorHandler.java:163-167`); 404 vira "Registro não encontrado" a não ser que a mensagem tenha sido escrita para a pessoa via `NotFoundException.forUser` (`:42-50`); `DataIntegrityViolation` vira 409 genérico; erro na cadeia de filtros sai com texto fixo (`SecurityErrorHandler.java:33-53`); sem override de `server.error.*`, valem os padrões do Boot (`include-message/stacktrace = never`). Login com mesma mensagem para e-mail inexistente e senha errada, bcrypt falso para igualar tempo, bloqueio antes de olhar o e-mail (`AuthService.java:26-57`); "Conta desativada" só para quem acertou a senha. Senha nova sempre 204 (`PasswordResetController.java:24-29`). Só 13 chamadas de log no `main`; o link de reset só sai com `EMAIL_LOG_TEXT=true`, padrão `false` (`LogEmailSenderTest.productionLogShowsTheSubjectButNotTheLink`). Trilha do admin sem nome de paciente, `recordId` nem conteúdo (`AuditEventDTO.java:10-19`), excluindo ação de paciente (`AuditEventRepository.java:33-38`, `AuditTrailTest.java:207-230`). Sem actuator. `server_tokens off` (`nginx.conf:9`), `Referrer-Policy same-origin` e CSP restrita. Id inexistente responde 403 de propósito nos serviços de acesso. Nenhum controller devolve entidade e `Users.java:171` tem `@JsonIgnore` no hash. `frontend/dist` e `.env.*` fora do git, sem sourcemap no build, `route-error.jsx` mostra texto fixo.

*O que vaza:*

1. Cadastro conta qual campo já tem conta: `POST /auth/register` devolve 409 com `field=email` ou `field=cpf` (`PatientService.java:81-88`), e `PatientSignUpTest:168-184` afirma exatamente isso. Exige um convite vivo (7 dias) passar primeiro em `:76-79`. É o #82.
2. Enumeração autenticada que o #82 **não** cobre: `PUT /profile/email` devolve 409 "Este e-mail já tem conta no sistema" para qualquer logado (`ProfileService.java:65-67`), sem limite, só exigindo a senha do próprio chamador. Um paciente consegue testar se qualquer e-mail pertence à clínica.
3. Oráculo de tempo em `POST /auth/password-reset/request`, sem card: `PasswordResetService.requestReset` (`:57-72`) volta na hora quando o e-mail não tem conta ativa (`:60-62`), mas para conta existente faz count, atualiza links pendentes, insere a linha e manda o e-mail de forma **síncrona** (`:83-100`, `SmtpEmailSender.send` sem `@Async`, timeouts de 5 s). O 204 é igual; o tempo não — centenas de ms a segundos, medível de fora. `PasswordResetTest.unknownEmailGetsTheSameAnswerAndNoEmail` (`:152`) só afirma o status.
4. Token cru no access log do nginx, sem card: o link de reset é `.../redefinir-senha?token=<cru>` (`PasswordResetService.java:96`) e o de convite `.../cadastro?convite=<cru>` (`invite-patient-modal.jsx:9`); o `frontend/nginx.conf` não tem `access_log` nem `log_format`, então vale o `main` da imagem, que grava `$request` com query string e `$http_referer`. A API esconde o link no log, mas o nginx grava cada token de reset (30 min) e de convite (7 dias) em texto puro, sem rotação (#111) — e com `Referrer-Policy same-origin`, o `POST .../confirm` chega com `Referer` contendo o token, que também vai para o log.
5. CSV anônimo leva o nome do prescritor (`ExportService.java:112`; `patientCell` só troca o paciente em `:293-295`) — #97. Sem card: no modo anônimo as colunas de texto livre (queixa, evolução, "Ocupação", "Observações") saem inteiras e podem conter nome; inerente ao formato, vale avisar na tela.
6. E-mail de lembrete diz por extenso qual escala clínica o paciente deve responder (`ReminderService.java:98-104,112-119`) — #109 JOBS-12. Painel do paciente entrega ao prescritor a caixa de avisos dele (`PatientDashboardDTO.java:19`) — #109 AUTZ-06. Aviso alheio 403 e inexistente 404 (`NotificationService.java:69-71`) — #109 AUTZ-07.
7. `handleBusinessRule` devolve `exception.getMessage()` ao vivo para `IllegalArgumentException` e `ValidationException` (`ErrorHandler.java:66-69`); o item 14 mostra um caminho concreto (mensagem do bcrypt em inglês), e qualquer `Assert` do Spring Data com nome de método também passaria.
8. `SmtpEmailSender.java:35` loga a `MailException` inteira; numa rejeição de destinatário a causa traz `550 ... <email>`. PII limitada a e-mail.
9. Log dos containers sem rotação (`docker-compose.yml` sem bloco `logging`) — #111 OPS-09.
10. `PrescriberService.java:107-110` devolve o link de senha nova do prescritor no corpo para o admin, mesmo tema do #61 em formato novo; o commit `594ad0b` só acrescentou trilha.
11. Fora deste item mas o maior vazamento real: sem HTTPS, senha e prontuário trafegam em claro — #57.

**Risco real para este sistema.** Para um sistema de óleo de cannabis com até 300 contas, confirmar que um CPF ou e-mail tem conta já é dado de saúde sobre pessoa identificada (dado sensível na LGPD). O #82 fecha a porta pública; `PUT /profile/email` e o tempo do reset deixam portas menores para quem já tem conta ou mede de fora. O resto (nome da escala no e-mail, avisos do paciente para o prescritor, token no log do nginx, mensagem de framework no 400) é baixo e quase tudo já tem card.

**Correção concreta.**

- #82 como está escrito: uma checagem só (`existsByEmail || existsByCpf`) virando `BusinessException("Não foi possível concluir o cadastro com esses dados. Se você já tem conta, entre pelo login")` sem campo, e registrar a falha no `LoginAttemptLimiter` por token e por endereço.
- `ProfileService.changeEmail`: manter o 409 (a tela precisa destacar o campo) mas limitar com o mesmo limitador (chave `email-change:` + userId, 5 em 15 min → `LoginBlockedException`). Melhor a longo prazo: link de confirmação para o e-mail novo e só trocar quando ele for aberto.
- Reset: responder 204 antes de mandar o e-mail, jogando o envio para fora da requisição (`@Async` com `@EnableAsync`, ou gravar o pedido e deixar o `DailyScheduler` drenar), fazendo o mesmo trabalho de banco nos dois caminhos.
- `ErrorHandler.handleBusinessRule`: só `BusinessException` com a mensagem ao vivo; `IllegalArgumentException` e `ValidationException` vão para `log.info` e devolvem o texto fixo de `handleBadRequest`.
- nginx: `log_format` próprio usando `$uri` em vez de `$request` e sem `$http_referer` (ou um `map` que mascara `token=` e `convite=`); e levar o token de reset no fragmento (`#token=`) em vez de `?token=` — o front já tira o token da barra de endereço (`reset-password.jsx:24-29`).
- `SmtpEmailSender`: logar `exception.getClass()` e uma mensagem curta, pilha só em debug.
- #97: em `appointmentsCsv` usar `anonymous ? "prescritor " + id : nome`, com assert no `ExportTest`. Na tela de exportação anônima, avisar que texto livre não é anonimizado.
- #109: os três itens como descritos. #111: bloco `logging` (`json-file`, `max-size 10m`, `max-file 3`) nos três serviços.

**Card.** #82, #97, #109, #111, #57, #61 (nota sobre o link no corpo). `changeEmail`, tempo do reset, `handleBusinessRule` e o log do nginx estão no #123 e no #120.

---

## 16 — Restringir uploads

**Situação.** não se aplica, gravidade nenhuma.

**Evidência.** Não existe rota de arquivo: `grep` no repositório inteiro (fora `node_modules`) por `MultipartFile`, `@RequestPart`, `multipart`, `FileOutputStream`, `Files.write`, `transferTo` e `FileReader` volta vazio; no front não há `input type="file"` nem `FormData` de envio (o único `FormData` é o `setFormData` do estado do React, o único `Base64` é o gerador de token em `SecureTokens.java:24`). O git também não tem passado nem plano: `git log -S` por esses termos é vazio em todos os branches, e em `docs/requisitos-v2.md` a palavra "anexo" só nomeia os anexos A e B do documento. A única resposta binária é o CSV (`ExportController.java:83-89`, `Content-Disposition: attachment`), que é saída: o nome do arquivo é palavra fixa + id + data (`ExportService.java:273-276`), sem texto de usuário, e o `location /api/` herda o `nosniff` do `security-headers.conf:4`. A API não grava arquivo em disco, roda sem privilégio (`backend/doisag/Dockerfile:22-23`), e os estáticos vêm prontos na imagem do nginx (`frontend/Dockerfile:15`). `spring.servlet.multipart.*` não está configurado, então o resolver vem **ligado** por padrão no Boot 4.1.1 (`MultipartAutoConfiguration` no `spring-boot-servlet-4.1.1.jar`): um POST multipart que chegue ao `DispatcherServlet` (rotas públicas ou com sessão) é parseado pelo Tomcat e as partes vão para o diretório temporário do container antes de o handler responder 415 (`ErrorHandler.java:118-122`), limitado pelo 1 MB implícito do nginx e apagado no fim da requisição. O teste `corpoFuraDeFormatoVira415` (`ErrorHandlingTest.java:67-74`) usa `TEXT_PLAIN`, não multipart. A API não é alcançável sem o nginx hoje (compose sem `ports` em `banco` e `api`).

**Risco real para este sistema.** Nenhum para upload. O único ponto é o teto de corpo estar implícito: quem subir o `client_max_body_size` por outro motivo, ou expuser a API sem o nginx (hipótese, não o estado atual), libera texto livre sem limite por campo da anamnese e por resposta de escala — assunto do item 14.

**Correção concreta.** Deixar a intenção escrita, junto com o #111:

- `frontend/nginx.conf`, dentro de `location /api/`: o mesmo `client_max_body_size` do item 14, com comentário no estilo do projeto ("o maior corpo da api eh a anamnese e cabe folgado aqui, arquivo n sobe por aqui").
- `application.yml`: `spring.servlet.multipart.enabled: false` ("a api n recebe arquivo entao nem monta o leitor de multipart"). A chave existe no `spring-configuration-metadata.json` do Boot 4.1.1. Com isso o corpo nem é lido e cai direto no 415; vale um teste irmão do `corpoFuraDeFormatoVira415` com `MediaType.MULTIPART_FORM_DATA`.
- Se um dia entrar anexo (exame em PDF), abrir card próprio: conferir tipo pelo conteúdo e não pela extensão, teto de tamanho, gravar fora da raiz web com nome aleatório, servir com `Content-Disposition: attachment` e `nosniff`, e passar pelo `@patientAccess` como todo dado clínico.

**Card.** #111.

---

## 17 — Trim respostas de API

**Situação.** parcial, gravidade média.

**Evidência.** *O que está certo:* as 83 assinaturas públicas de `controller/*.java` devolvem DTO, `ResponseEntity<DTO>`, `Void`, `byte[]` (CSV) ou `Map<String,String>` (`/health`); nenhum DTO tem campo tipado com classe do `model` (só enums e `AddressDTO`; os 22 imports de `model` nos DTOs são só nos construtores de conversão). Tudo é achatado em `patientId`/`patientName`/`prescriberName` (`AppointmentResponseDTO.java:27-30`, `AgendaAppointmentDTO.java:17-19`, `ScaleResponseDTO.java:22-24`, `AnamnesisResponseDTO.java:35`, `PrescriptionResponseDTO.java:34`). `Users.getPassword()` tem `@JsonIgnore` (`Users.java:171-175`), assim como os getters do `UserDetails`, `getRole`, `passwordChangedAt` e `sessionsEndedAt` (`:140-207`), e `Patient.getArchivedBy/isArchived` (`Patient.java:55-67`) — defesa em profundidade, porque a proteção real é a camada de DTO, e é ela que o `PasswordExposureTest` (`:79-121`) prova em `/patients`, `/patients/{id}`, `/profile` e `/scales/responses/{id}`. DTOs mínimos onde importa: `SessionUserDTO` = id/nome/papel; `AdminPrescriberDTO` sem CPF, telefone, endereço nem nascimento (`:7-15`); `AuditEventDTO` sem `recordId` nem nome de paciente; `InviteInfoDTO` (rota pública) só nome e profissão do prescritor; `TimeSlotDTO` só início/fim; erros só campo/mensagem/path.

*O que sobra na resposta:*

1. `PatientResponseDTO` manda `cpf`, `email`, `birthDate`, `phone` e endereço completo (`:11-23`) em `GET /patients` (lista inteira da carteira), `GET /patients/{id}` e `PUT /patients/{id}/archive|reactivate` (`PatientsController.java:37-63`). O front nunca lê `cpf`, `email`, `phone` nem `address` de um objeto paciente: só `name`, `birthDate`, `archivedAt` e `prescriberName` (`lista-paciente.jsx:17,20`, `historico-clinico-prescritor.jsx:104,239`, `consultation-record.jsx:235`, `impressao.jsx:117-123`, `agendamento-consulta-paciente.jsx:130,184,282`); as únicas ocorrências de `.cpf`/`.phone`/`.address` são estado de formulário em `profile.jsx`, `sign-up.jsx` e `admin-prescribers.jsx`. E a lista completa é baixada em **três** telas — `lista-paciente.jsx:48`, `progresso.jsx:102` e `agendamento-consulta-prescritor.jsx:73` — e em duas delas serve só para montar um dropdown de nome (`appointment-form-modal.jsx:61` usa `patient.id` e `patient.name`; `progresso.jsx:282-297` idem). A cada abertura da agenda ou do gráfico de progresso, o CPF, telefone e endereço de toda a carteira vão para o navegador sem uso.
2. `PasswordResetLinkDTO` devolve o token vivo de senha nova na resposta do admin (`:5-10`, `PrescriberService.java:107-110`). De propósito para o piloto sem SMTP e entra na trilha, mas o segredo passa pelo navegador e histórico do admin — mesmo tema do #61.
3. `PatientDashboardDTO.latestNotifications` vai para o prescritor — #109 AUTZ-06.
4. `ErrorHandler.java:66-68` devolve `exception.getMessage()` também para `IllegalArgumentException`, classe de framework (item 15).
5. Nota para o #37: os `@JsonIgnore` são de `com.fasterxml.jackson.annotation` enquanto o Boot 4 serializa com Jackson 3 (`tools.jackson` em `SecurityErrorHandler.java:13`); funciona porque o pacote de anotações foi mantido. Como nenhuma rota serializa entidade, remover o `jackson-annotations` não faria o hash voltar sozinho — mas vale manter. O que segura o Jackson 2 no classpath é o `jjwt-jackson` (`pom.xml:99`).

**Risco real para este sistema.** O prescritor tem direito aos dados dos próprios pacientes, então o ponto 1 não é furo de autorização: é amplificação. Uma sessão comprometida, um devtools aberto na clínica ou um HAR salvo entregam a lista completa de CPFs e endereços de pacientes de uma clínica de cannabis de uma vez só — e hoje isso ainda trafega em HTTP (#57). Minimização é princípio da LGPD (art. 6, III), e o custo de consertar é zero porque nenhuma tela usa os campos.

**Correção concreta.**

- `PatientResponseDTO`: tirar `cpf`, `email`, `phone` e `address`, ficando `id`, `name`, `birthDate`, `prescriberId`, `prescriberName`, `archived`, `archivedAt` (a própria pessoa vê o resto no `ProfileDTO`). Se a clínica pedir telefone ou CPF no prontuário, criar `PatientDetailDTO` usado só no `GET /patients/{id}` ou uma rota `/patients/{id}/contact`, nunca na lista.
- Travar: o `PasswordExposureTest` já cria o paciente com CPF `00000000191` e e-mail `teste-vazamento@email.com` (`:61-62`), então um teste `listarPacientesNaoPodeTrazerCpfNemContato` com `doesNotContain` dos dois entra sem fixture nova.
- `PasswordResetLinkDTO`: quando `MAIL_HOST` estiver preenchido (o `EmailConfig` já sabe), devolver só `prescriberName` e `validMinutes` e deixar o link ir pelo e-mail; manter o link na resposta apenas sem SMTP, como o comentário do DTO já explica.
- #109 AUTZ-06 e #37 conforme os cards (no #37 manter `jackson-annotations`).

**Card.** #109, #61, #37, #57, #97 (também é trim de resposta). Enxugar o `PatientResponseDTO` é o #122.

---

## 18 — Security headers

**Situação.** parcial, gravidade baixa.

**Evidência.** *nginx* (`frontend/security-headers.conf`, incluído no `server` em `nginx.conf:12` e repetido nos locations que têm `add_header` próprio, `:26` e `:32`; `/api/` e `/` herdam do `server`): `X-Content-Type-Options nosniff` (`:4`), `X-Frame-Options DENY` (`:5`), `Referrer-Policy same-origin` (`:6`), CSP `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'` (`:10`) sem `unsafe-inline`, `Permissions-Policy` com câmera, microfone, geolocalização, pagamento e USB desligados (`:13`), `Strict-Transport-Security max-age=31536000` (`:16`) sem `includeSubDomains`, todos com `always`. `server_tokens off` (`nginx.conf:9`). Cache: `index.html` `no-cache` (`:31`), `/assets/` imutável por 1 ano (`:25`); fonte, imagem e `boot.css` sem regra (#111 OPS-12). A CSP sem inline se sustenta no build: o `frontend/dist/index.html` gerado em 01/10 só tem tags externas (`theme-boot.js`, o módulo em `/assets/`, dois `modulepreload` e o CSS), `grep` em `dist/assets/*.js` não acha `createElement("style")`, `insertRule`, `setAttribute("style"`, `.cssText`, `eval(` nem `new Function(`; os `style={{}}` do React e do Recharts vão pelo CSSOM, que `style-src` sem `unsafe-inline` não bloqueia; as fontes são `url(/fonts/*.woff)` e não há `data:` no bundle (as permissões `data:` ficam de reserva); não há `<iframe>`, `window.open`, `createObjectURL` nem `@import`; o CSV e o WhatsApp são `<a>` de navegação, fora de `connect-src`. Mas nunca foi aberto num navegador atrás do nginx: o CI só faz `docker compose build` (#102). *Spring* (`/api`): `SecurityConfigurations.java:36-71` não mexe em `.headers()`, então valem os padrões do Spring Security 7.1.1 em toda resposta da API: `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`, `Pragma`, `Expires: 0`, `nosniff`, `X-Frame-Options DENY`, `X-XSS-Protection 0`; o CSV clínico também sai com `no-store` (`ExportController.java:83-89` só põe tipo e disposição). O HSTS do Spring só sai quando `request.isSecure()`, e hoje o nginx manda `X-Forwarded-Proto $scheme` = http (`nginx.conf:20`). Nenhum teste confere cabeçalho de resposta (`grep` em `src/test` só acha `Set-Cookie`). Faltam `Cross-Origin-Opener-Policy` e `Cross-Origin-Resource-Policy`.

**Risco real para este sistema.** Baixo, o conjunto está certo. O perigo concreto é a CSP estrita quebrar algo só em produção porque ninguém abriu o build final atrás do nginx; e o HSTS dar sensação de proteção enquanto não há TLS. Um detalhe para o #57: se o TLS for terminado **dentro** deste nginx, `X-Forwarded-Proto: https` faz o Spring emitir o próprio `Strict-Transport-Security: max-age=31536000 ; includeSubDomains` enquanto o nginx continua mandando o dele sem `includeSubDomains` — dois STS com valores diferentes, e só o primeiro é processado (RFC 6797 8.1). Se for Caddy ou Traefik na frente, `$scheme` fica http e só o do nginx sai. Os cabeçalhos duplicados em `/api` (nginx e Spring mandam `X-Frame-Options` e `nosniff` com o mesmo valor) são inofensivos.

**Correção concreta.** Em `security-headers.conf`: trocar a linha 16 por `add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;` (no mesmo commit do #57, o domínio é próprio) e acrescentar `add_header Cross-Origin-Opener-Policy "same-origin" always;` e `add_header Cross-Origin-Resource-Policy "same-origin" always;` com um comentário de uma linha cada. No job de compose do #102: `curl -sI http://localhost:5173/ | grep -i content-security-policy` e `curl -sI http://localhost:5173/api/health | grep -i no-store`, e abrir uma vez a tela de login e a de evolução (gráfico) olhando o console do navegador. Opcional, para travar o padrão do Spring: em `SessionTest.loginReturnsTheUserAndAProtectedCookie` (`:104`) acrescentar `.andExpect(header().string("Cache-Control", containsString("no-store")))`. Deixar os duplicados como estão.

**Card.** #57, #102, #111 (cache), #104. COOP/CORP e a assertion de cabeçalho não têm card; cabem no #111 e no #102 sem card novo.

---

## 19 — Forçar HTTPS

**Situação.** falta, gravidade alta.

**Evidência.** Não existe redirecionamento nem terminação TLS: `frontend/nginx.conf:3-5` só tem `listen 80; server_name _;`, sem `listen 443`, `ssl_certificate` ou `return 301 https://`; `docker-compose.yml:81-82` publica `"5173:80"` em todas as interfaces, única porta da pilha (`README:53`); `git grep -i -E "caddy|traefik|certbot|ssl_certificate|listen 443|acme|letsencrypt"` só bate em `docs/auditoria-da-api.md` descrevendo a falta, e `git log --all --grep` por https/tls/ssl/caddy só traz bumps do Dependabot e o `65e977b` (HSTS no nginx). O `ci.yml` só roda testes e `docker compose build`; não há pipeline de deploy que pudesse pôr o TLS fora do repositório. O HSTS do `security-headers.conf:16` é ignorado em http; o do Spring nunca sai porque `X-Forwarded-Proto` chega como http (`nginx.conf:20`). O `Secure` do cookie não está amarrado ao esquema: `SessionCookieService.java:27,63` lê `api.session.secure-cookie` (`application.yml:66` ← `SESSION_SECURE_COOKIE`); o compose dá padrão `true` (`:46`), mas o `.env.example:28` entrega `SESSION_SECURE_COOKIE=false` explícito, que vence, e `README:57,79` mandam trocar na mão (#103). `SessionTest.java:113-118` só confere `Secure` com o padrão `true` do yml; nenhum teste amarra o `Secure` ao esquema da requisição. O front não tem URL absoluta http: `api.js:4` `BASE_URL = "/api"`, redirect relativo (`:67`); o único `http://` é o alvo do proxy de dev em `vite.config.js:9`, fora do build; o WhatsApp é https. Links de e-mail (`PasswordResetService.java:96`, `ReminderService.java:117`) e o `OriginCheckFilter` (`:31-39`) usam `PUBLIC_URL`, que em produção precisa ser `https://...` — com isso, POST que chegue por http sem redirecionar toma 403, rede de segurança que não substitui o redirect. O `.gitignore` já exclui `*.pem`, `*.key`, `*.p12` e `*.jks`, então dá para guardar certificado próprio no servidor sem risco de commit. Inconsistência de documentação: `docs/requisitos-v2.md:973` marca o RNF05 ("toda conexão externa por HTTPS/TLS") como concluído na fase 1 em 12/09/2026, mas o critério de aceite em `:503` só pede "não há URL http:// fixa no código; origem da API configurável" — satisfeito sem existir TLS nenhum. A auditoria (`OPS-01`) contradiz o requisitos.

**Risco real para este sistema.** Alta. Senha no login, cookie de sessão (até 12 h), prontuário, anamnese e CSV atravessam a rede em claro; qualquer um no wi-fi da clínica copia o cookie e entra como a pessoa. A própria auditoria chama de bloqueador do piloto.

**Correção concreta.** Caminho menor: Caddy na frente do `web`, tirando o `ports: 5173:80` do serviço `web`.

`docker-compose.yml`:

```yaml
  # termina o https e repassa pro nginx do front
  proxy:
    image: caddy:2-alpine
    container_name: 2ag-proxy
    restart: unless-stopped
    depends_on:
      - web
    ports:
      - "80:80"
      - "443:443"
    environment:
      PUBLIC_HOST: ${PUBLIC_HOST}   # sem isso o {$PUBLIC_HOST} do Caddyfile fica vazio e o caddy n sobe
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - dados-do-caddy:/data
```

(e `dados-do-caddy:` no bloco `volumes`)

`Caddyfile` na raiz (o Caddy redireciona http para https sozinho e emite/renova o certificado; com certificado próprio, `tls /certs/cert.pem /certs/chave.pem` e um volume só leitura):

```
{$PUBLIC_HOST} {
    reverse_proxy web:80
}
```

`nginx.conf:20` → `proxy_set_header X-Forwarded-Proto $http_x_forwarded_proto;`.

No mesmo commit (#57 e #103 andam juntos, porque `Secure` antes do TLS quebra o login em silêncio): `.env.example` com `SESSION_SECURE_COOKIE=true` e o comentário dizendo que no computador local é `false`, `PUBLIC_URL=https://...`, `PUBLIC_HOST=` novo; `README:57,79` ajustados. Alternativa só com nginx (o #57 descreve): `server` extra com `listen 80; return 301 https://$host$request_uri;` + `listen 443 ssl` com `ssl_certificate` num volume e portas `80:80`/`443:443`.

Pronto quando: `curl -sI http://dominio/` dá 301 para https, `curl -sI https://dominio/api/health` traz `strict-transport-security`, o login no navegador grava o cookie com `Secure`, e o smoke test do #102 confirma os 429 do limitador pela porta pública (item 12). Corrigir o critério do RNF05 em `docs/requisitos-v2.md:503` ou reabrir o requisito, para ninguém ler o doc e achar que já está feito.

**Card.** #57, #103, #18, #102, #101.

---

## 20 — Scan de dependências

**Situação.** parcial, gravidade média.

**Evidência.** *Configurado:* `.github/dependabot.yml:6-36` cobre npm (`/frontend`, semanal), maven (`/backend/doisag`, semanal), github-actions (mensal), docker (`/frontend` e `/backend/doisag`, mensal) e docker-compose (mensal). Alertas de vulnerabilidade ligados (`gh api repos/2ag-uffs/2ag/vulnerability-alerts` → 204), 0 abertos; 47 alertas históricos, todos npm em `frontend/package-lock.json`, criados e corrigidos em 13/09. `package-lock.json` commitado e `npm ci` no CI (`ci.yml:75`) e no `Dockerfile` (`:6`). `npm audit --omit=dev` em `frontend`: 0 vulnerabilidades. Com dev: 1 high, `brace-expansion 5.0.9` (três advisories de DoS) via `eslint@10.10.0 > minimatch > brace-expansion`, só dev, e a PR #113 (eslint 10.11.0) está aberta. Backend: `pom.xml:8` BOM `spring-boot-starter-parent 4.1.1` gerencia tudo, único pin manual `jjwt 0.13.0`; `./mvnw -o dependency:list` resolve 99 artefatos compile/runtime: `tomcat-embed-core 11.0.24`, `spring-security 7.1.1`, `spring-core/web/webmvc 7.0.9`, `hibernate-core 7.4.5.Final`, `jackson-databind 2.21.5` (ainda no classpath pelo jjwt, #37) e 3.1.5, `logback 1.5.38`, `flyway-core 12.4.0`, `postgresql 42.7.13`.

*O que não funciona:* (1) `dependabot_security_updates: disabled` — advisory não vira PR sozinha. (2) O CI roda teste, lint, build e `docker compose build`, sem `npm audit`, `dependency-check`, CodeQL (`code-scanning/default-setup` = `not-configured`, grátis em repositório público) nem `dependency-review-action`; `grep` por `npm audit|dependency-check|codeql|dependency-review|trivy|snyk|osv-scanner|grype` no repositório: 0. (3) **O backend é invisível para os alertas:** `gh api repos/2ag-uffs/2ag/dependency-graph/sbom` tem 308 pacotes, 19 maven, só 3 com versão (os jjwt); `spring-boot-starter-security`, `postgresql` e os demais entram sem `@versão` porque o `pom.xml` não a declara (vem do BOM), e nenhuma transitiva aparece — tomcat, spring-security, hibernate, jackson e logback não existem no grafo. Prova empírica: dos 47 alertas da história, 100% são npm; nunca houve um alerta maven, nem quando o backend ficou em Spring Boot 3.5.0 (`c9fe887` subiu 3.5.0 → 3.5.16 na mão). As três advisories do `brace-expansion` são `reviewed`, publicadas em 29/09, o pacote está no SBOM, e até hoje não há alerta aberto — "0 alertas" não equivale a limpo. (4) Fila parada: 8 PRs do Dependabot abertas desde 15/09 (#50-#55, #112, #113), inclusive `react-router 8.3.1 → 8.4.0` (#55, produção), todas com CI verde; #50-#52 são saltos de major de imagem base (temurin 25, temurin 26, node 26). As 12 PRs anteriores (#1-#10, #49, #56) foram todas fechadas sem merge com bumps feitos na mão: o repositório nunca mergeou uma PR do Dependabot.

**Risco real para este sistema.** Hoje não há CVE conhecida nas versões resolvidas (Spring Boot 4.1.1 é recente), mas o processo não segura nada: uma advisory em `tomcat-embed`, `spring-security` ou `jackson` não vira nem e-mail — o backend que guarda o prontuário não tem canal de alerta nenhum, só o bump semanal do parent (que ninguém mergeia). No front, uma advisory futura em `react-router` vira um e-mail que não barra deploy. Para um sistema com prontuário isso é atraso silencioso na correção, não furo imediato.

**Correção concreta.** Em ordem:

1. `ci.yml`, job backend, depois do `./mvnw -B verify`:

```yaml
      # manda a arvore de dependencia pro github pra o dependabot enxergar tomcat, spring e cia
      - uses: advanced-security/maven-dependency-submission-action@v5
        with:
          directory: backend/doisag
```

com `permissions: contents: write` no job. A partir daí o Dependabot alerta sobre as 99 transitivas sem `NVD_API_KEY` nem `dependency-check`.

2. Job frontend, depois de `npm ci`: `- run: npm audit --omit=dev --audit-level=high` (dependência de produção com high/critical quebra o build; dev não bloqueia).
3. Settings > Code security: ligar Dependabot security updates (hoje `automated-security-fixes` = `false`), CodeQL default setup, e Secret scanning + Push protection (item 2).
4. Triar a fila: mergear #55, #54, #53, #112, #113; fechar #50-#52 e, nos blocos docker do `dependabot.yml`, `ignore: - dependency-name: "*" update-types: ["version-update:semver-major"]`.
5. Opcional: `org.owasp:dependency-check-maven` num profile `security` com `<failBuildOnCVSS>7</failBuildOnCVSS>` semanal no CI (precisa de `NVD_API_KEY` nos secrets).

**Card.** #115 e #116.

---

## O que fazer primeiro

1. **HTTPS, no mesmo commit do `Secure` do cookie** (itens 19, 9, 5 — #57, #103, #18): Caddy na frente do `web`, `SESSION_SECURE_COOKIE=true`, `PUBLIC_URL` com https, `X-Forwarded-Proto` repassado, `includeSubDomains` no HSTS. É o único item que sozinho carrega três gravidades altas, e tudo que vem depois só vale com ele.
2. **Limitador nos três oráculos de senha e `limit_req` no nginx** (item 11 — #119, #120): `PUT /profile/password`, `PUT /profile/email` e o reset do admin passando pelo `LoginAttemptLimiter`, `X-Forwarded-For` com `$remote_addr`, zona `auth` em `/api/auth/`. Fecha a tomada definitiva de conta a partir de sessão esquecida e dá o freio que falta a cadastro, senha nova e export.
3. **Admin não conhece a senha do prescritor e consegue travar conta de paciente** (item 7 — #61, #62): senha aleatória + link de definição no `create`, link de reset só sem SMTP, `PUT /admin/patients/{id}/active`. É o que sustenta a regra central "admin não vê prontuário".
4. **Enxugar o `PatientResponseDTO` e fechar a enumeração** (itens 17, 15 — #122, #123, #82): tirar CPF, e-mail, telefone e endereço da lista com o teste de tranca, cadastro com uma mensagem só, `changeEmail` limitado, 204 do reset antes do envio. Minimização com custo zero e três oráculos a menos.
5. **Dar visibilidade ao backend e ligar o que o GitHub oferece de graça** (itens 20, 2 — #115, #116): `maven-dependency-submission-action` no CI, `npm audit` de produção, Dependabot security updates, CodeQL, Secret scanning + Push protection, e triar a fila de PRs. Hoje o backend que guarda o prontuário não tem canal de alerta nenhum.

Logo em seguida, com o TLS no ar: backup cifrado com `age` e STARTTLS obrigatório (item 5), `__Host-` e a conferência de https × `secure-cookie` na subida (item 9), e os testes de tranca de isolamento (item 4).

---

## Cards criados

Criados em 01/10/2026 como #114 a #124, na ordem abaixo, depois do aval do dono do projeto.

**#114 · seed de teste n pode subir em producao e o dockerignore deixa passar application-local**
- DevDataSeed so olha api.seed.enabled, n tem trava nenhuma contra servidor de verdade
- se alguem deixar SEED_DADOS_TESTE=true no .env do servidor nasce um admin c/ a senha q ta no README
- trava: se PUBLIC_URL comeca c/ https o seed loga aviso e n faz nada
- backend/doisag/.dockerignore n exclui application-local*.yml e o Dockerfile copia src inteiro pro jar
- frontend/.dockerignore so exclui .env, trocar por .env* e *.local
- anotar no guia #18 q SEED_DADOS_TESTE fica fora do .env do servidor

**#115 · github: ligar secret scanning, push protection, dependabot security updates e codeql**
- repo eh publico e tudo isso eh de graca, hoje ta tudo desligado (gh api mostra disabled)
- push protection barra .env colado por engano antes de subir
- security updates faz advisory virar PR sozinha em vez de so e-mail
- codeql default setup pro java e pro js
- um clique em settings > code security ou gh api -X PATCH c/ security_and_analysis
- apagar database/physical-model/script-insert.sql q so tem hash de usuario do prototipo e ninguem usa

**#116 · ci: scan de dependencia do back e do front e triagem da fila do dependabot**
- o grafo do github so ve 19 dependencia direta do pom, 16 sem versao e zero transitiva
- tomcat, spring-security, hibernate, jackson e logback n existem pro dependabot, nunca teve alerta maven na historia do repo
- maven-dependency-submission-action no job do back dps do verify, c/ contents: write
- npm audit --omit=dev --audit-level=high no job do front dps do npm ci
- 8 PR do dependabot parada desde 15/09 c/ ci verde, mergear #53 #54 #55 #112 #113 e fechar as de major de imagem base
- nos blocos docker do dependabot.yml ignorar semver-major

**#117 · backup cifrado c/ age e starttls obrigatorio no smtp**
- backup-banco.sh gera pg_dump sem cifra e o doc manda a copia sair do servidor pra bucket ou pendrive
- age -r c/ chave publica no servidor e privada fora, quem leva o servidor n le os backup antigo
- restaurar-banco.sh faz age -d | pg_restore
- BACKUP_AGE_PUBKEY no .env.example e uma frase no backup-e-restauracao.md
- application.yml so tem starttls.enable, sem required o link de senha nova e o MAIL_PASSWORD podem ir em claro
- MAIL_SMTP_STARTTLS=true por padrao, false so pra servidor local de teste
- registrar q n vamos cifrar por campo e pq

**#118 · back: cookie c/ prefixo __Host- e conferencia de https x secure-cookie na subida**
- dps do #57, qnd secure-cookie for true o nome vira __Host-session
- navegador recusa __Host- sem Secure, c/ Domain ou fora de Path=/, entao config errada aparece na hora
- na subida, PUBLIC_URL https c/ secure-cookie false recusa subir (ou ao menos avisa), e http c/ true tbm pq o login falha em silencio
- mesmo estilo do OriginCheckFilter q ja recusa PUBLIC_URL invalido
- passwordChangedAt em milissegundo igual ao sessionsEndedAt (#109 AUTZ-08)
- opcional: so aceitar Authorization: Bearer c/ api.session.accept-bearer=true no perfil de teste

**#119 · back: trocar senha, trocar e-mail e reset do admin aceitam chute da senha atual sem limite**
- PUT /profile/password, PUT /profile/email e POST /admin/prescribers/{id}/password-reset conferem bcrypt e n passam pelo LoginAttemptLimiter
- quem pega uma sessao aberta no pc da recepcao testa a senha atual ate acertar e toma a conta de vez
- o de e-mail eh pior pq troca pra onde vai o link de senha nova
- reusar isBlocked e registerFailure c/ o e-mail do usuario e o getRemoteAddr, 429 igual ao login
- contar pedido de senha nova por endereco (10 em 15 min) e mandar o e-mail fora da transacao
- teste espelhando SessionTest: 5 senha atual errada -> 429 ate c/ a certa, no ProfileTest e no AdminPrescribersTest
- terceira chave email-total: c/ teto alto (100 em 15 min) pra frear botnet, e honeypot nos forms de cadastro e esqueci-senha

**#120 · nginx: limit_req, corpo maximo, log sem token e cabecalhos q faltam**
- limit_req_zone auth 10r/m em /api/auth/ e api 20r/s em /api/, limit_req_status 429
- proxy_set_header X-Forwarded-For $remote_addr em vez de $proxy_add_x_forwarded_for, cliente de ip privado hoje falsifica o endereco do limitador
- client_max_body_size 2m em /api/ e spring.servlet.multipart.enabled=false na api
- log_format proprio c/ $uri em vez de $request e sem referer, hoje o token de reset e de convite vai cru pro access_log
- location = /api/health { return 404; } pq o healthcheck chama por dentro
- Cross-Origin-Opener-Policy e Cross-Origin-Resource-Policy same-origin, includeSubDomains no hsts junto c/ o #57
- bloco logging c/ max-size nos tres servico do compose (#111 OPS-09)

**#121 · back: senha c/ acento estoura o bcrypt e campo livre sem @Size vira 409**
- PasswordRules conta 64 caracteres mas o bcrypt do spring recusa acima de 72 bytes, 37 letra c/ acento ja estoura
- cai no handleBusinessRule e devolve 400 c/ "password cannot be more than 72 bytes" em ingles, no login vira 400 em vez de 401
- PasswordRules.fitsInBcrypt antes do encode nos tres servico, mensagem em pt-BR, no login vira senha errada
- front soma TextEncoder().encode(senha).length <= 72 na regra
- @Size(max=255) em name/profession/registryType/registryNumber/email do PrescriberCreateDTO e nos campo do AddressDTO, hoje estoura varchar e vira 409 "ja existe"
- @Size(max) nas lista: components 20, escalationSteps 104, items 10, periods 50
- handleBusinessRule so repassa mensagem de BusinessException, IllegalArgumentException vai pro log

**#122 · back: lista de paciente manda cpf, telefone e endereco q nenhuma tela usa**
- PatientResponseDTO leva cpf, email, phone e address em GET /patients, /patients/{id}, archive e reactivate
- o front so le name, birthDate, archivedAt e prescriberName; a lista inteira eh baixada em 3 tela e em 2 so monta dropdown de nome
- deixar id, name, birthDate, prescriberId, prescriberName, archived, archivedAt
- se a clinica pedir contato no prontuario, PatientDetailDTO so no GET /patients/{id}
- PasswordExposureTest ja tem paciente c/ cpf 00000000191, acrescentar doesNotContain do cpf e do e-mail
- PasswordResetLinkDTO so devolve o link qnd n tem MAIL_HOST, c/ smtp vai pelo e-mail

**#123 · back: pedido de senha nova responde em tempo diferente e trocar e-mail conta quem tem conta**
- requestReset volta na hora pra e-mail sem conta e demora o envio smtp sincrono pra conta real, da pra medir de fora
- responder 204 antes de mandar o e-mail, envio fora da requisicao (@Async ou job)
- PUT /profile/email devolve 409 "ja tem conta" sem limite, paciente logado testa qualquer e-mail da clinica
- limitar c/ a chave email-change: no LoginAttemptLimiter, melhor ainda link de confirmacao no e-mail novo
- SmtpEmailSender loga a MailException inteira e a causa traz o endereco, logar so a classe e msg curta
- na tela de export anonimo avisar q texto livre n eh anonimizado

**#124 · back: testes de tranca pra isolamento, mass assignment e resposta sem pii**
- RouteRolesTest so exige hasRole, rota nova c/ {patientId} sem @patientAccess passa e vaza entre prescritor
- assertar q toda rota c/ {patientId}/{appointmentId}/{id} fora de /admin e /notifications cita "Access."
- PatientLinkTest ganha export csv, audit-events, progress/comments, archive/reactivate, no-show, review/annul, anamneses e meem por consulta
- mover o id do meem de prescriberOnlyReadRoutes pra sharedReadRoutes, paciente le o proprio
- teste mandando role/active/prescriberId/id junto c/ corpo valido em /auth/register e /profile e conferindo q nada mudou
- Cache-Control no-store no SessionTest e curl dos cabecalho no job de compose do #102
- fecha a parte automatizada do #29

---

## O que não se aplica e por quê

- **16 — Restringir uploads.** Não existe rota que receba arquivo, nem no código, nem no histórico do git, nem nos requisitos: a única resposta binária é o CSV, que é saída. O que sobra (teto de corpo implícito de 1 MB no nginx e o resolver de multipart ligado sem ninguém consumir) está registrado no item 14 e na proposta do nginx, e a regra para quando um anexo entrar um dia fica escrita no item.
- **3 e 4 no sentido original (chave pública anon e RLS do Postgres).** Não há cliente falando direto com o banco nem chave de serviço de terceiro no navegador; o banco não publica porta e a API usa o dono do schema. O equivalente aqui — banco inalcançável de fora e vínculo conferido por rota na API — foi avaliado e está ok.
- **Captcha (dentro do item 12).** Sem cadastro aberto, com convite de uso único e limitador no login, um captcha não compraria segurança proporcional ao atrito que traria para paciente idoso no celular. O freio certo é `limit_req` e honeypot.
- **Cifra por campo (dentro do item 5).** Não protege do ataque mais provável (API comprometida com a chave no mesmo ambiente), quebraria `existsByCpf`, o CSV nominal e o histórico, e traria rotação de chave para um código que quer ser simples. TLS, disco cifrado e backup cifrado são o conjunto apto para este tamanho; a decisão fica registrada e volta só se o banco for para host compartilhado ou nuvem de terceiro.
- **Reescrever o histórico do git (dentro do item 2).** O único segredo que entrou foi um placeholder de desenvolvimento do protótipo, removido em quatro minutos, que o código atual nunca lê; o GitHub continua servindo o commit por SHA até o suporte purgar, então o ganho seria nulo. O conserto é não reaproveitar e ligar o push protection.
