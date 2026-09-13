### **backend do sistema doisag**

esse repositorio tem o código do backend do sistema doisag, uma api pra gerenciar e acompanhar pacientes que usam fitocanabinoides

o documento de requisitos vigente é o [`docs/requisitos-v2.md`](../docs/requisitos-v2.md). este README descreve **o que a api faz hoje**, não o que ela deveria fazer — o que está fora do esperado está na seção [limitações conhecidas](#limitações-conhecidas)

-----

### **tecnologias**

  * **java 17**
  * **spring boot 3.5.0**
  * **spring data jpa** com hibernate pra cuidar do banco
  * **spring security** com jwt (biblioteca jjwt 0.11.5)
  * **maven** pra gerenciar as dependências
  * **postgresql** como banco de dados

-----

### **pré-requisitos**

pra rodar o projeto, você vai precisar ter instalado:

  * **jdk 17** (não jre, e não a 8 — o projeto compila com `release 17`)
  * postgresql rodando
  * maven **não** precisa instalar: o repo tem o wrapper (`mvnw`), que baixa a versão certa sozinho

confere se seu java tá certo antes de começar:

```bash
java -version
```

se aparecer `1.8.x` você tem só o java 8 no path e o build vai falhar

-----

### **como rodar o projeto**

1.  **clone o repositório:**

    ```bash
    git clone <url-do-seu-repositorio>
    cd 2ag/backend/doisag
    ```

2.  **arrume o banco de dados:**

    crie o banco `doisag` e o usuário `admindoisag` no seu postgres. o passo a passo completo tá no [`database/README.md`](../database/README.md)

3.  **configuração:**

    veja a seção [configuração](#configuração) logo abaixo

4.  **suba a aplicação:**

    ```bash
    ./mvnw spring-boot:run
    ```

    se tudo der certo a api vai tá rodando em `http://localhost:8080`

5.  **pra gerar o .jar:**

    ```bash
    ./mvnw clean package -DskipTests
    ```

-----

### **migracoes do banco**

quem cria e altera tabela eh o **flyway**, pelos arquivos em `src/main/resources/db/migration/`. o hibernate fica em `validate`: ele so confere se o banco bate com as entidades e reclama se n bater.

pra mudar o esquema:

1. mexe na entidade
2. cria um arquivo novo, `V2__descricao_curta.sql`, com o `alter table` correspondente
3. roda os testes. se esquecer do passo 2, quebra com `Schema-validation`

**nunca edite uma migracao que ja rodou em algum banco.** o flyway guarda o checksum de cada arquivo e recusa a subida se ele mudar. correcao vira migracao nova.

-----

### **testes**

```bash
./mvnw clean install
```

a suite roda no perfil `test`, com **h2 em memoria**. n precisa de postgres instalado, n precisa de variavel de ambiente, n precisa de docker. o build passa em maquina limpa, que eh o RNF13.

a configuracao fica em `src/test/resources/application-test.yml`: h2 com `MODE=PostgreSQL` (pros campos `TEXT` funcionarem igual), flyway ligado e `ddl-auto: validate`, chave jwt fixa e seed desligado.

os testes rodam **a mesma migracao que roda em producao**, e o `validate` confere se as entidades batem com ela. se alguem mexer numa entidade e esquecer da migracao, a suite quebra com `Schema-validation: missing column`.

**toda classe de teste precisa de `@ActiveProfiles("test")`.** sem isso ela tenta o postgres de verdade e falha. o IntelliJ roda o JUnit direto, sem passar pelo maven, entao a anotacao eh o que garante o perfil nos dois caminhos.

o que ja tem cobertura:

| teste | o que verifica |
| :--- | :--- |
| `DoisagApplicationTests` | o contexto spring inteiro sobe sem banco instalado |
| `ProtectedRoutesTest` | sem token, token expirado e assinatura inválida devolvem 401 |
| `AuthorizationRulesTest` | as regras de papel e de vínculo do RF29 e RF30 |
| `PasswordExposureTest` | nenhuma resposta da api carrega hash de senha |
| `HamiltonScaleServiceTest` | o escore soma os itens (unidade, sem spring nem banco) |

-----

### **configuração**

a configuração vem de **variável de ambiente**. o `application.yml` só declara os nomes e os padrões:

| variavel | pra que serve | padrao |
| :--- | :--- | :--- |
| `DATABASE_URL` | endereço jdbc do banco | `jdbc:postgresql://localhost:5432/doisag` |
| `DATABASE_USER` | usuário do banco | `admindoisag` |
| `DATABASE_PASSWORD` | senha do banco | **sem padrão, obrigatória** |
| `JWT_SECRET` | chave que assina os tokens jwt | **sem padrão, obrigatória** |
| `CORS_ALLOWED_ORIGIN` | origem do front que pode chamar a api | `http://localhost:5173` |
| `SEED_DADOS_TESTE` | cria os usuários de teste na subida | `false` |
| `JPA_DDL_AUTO` | estratégia de esquema do hibernate | `validate` |
| `FLYWAY_ENABLED` | roda as migrações na subida | `true` |
| `SERVER_PORT` | porta da api | `8080` |

`DATABASE_PASSWORD` e `JWT_SECRET` **não têm padrão de propósito**: é melhor a aplicação não subir do que subir com credencial que está publicada no repositório. nenhum segredo fica em arquivo versionado.

pra rodar localmente, exporte as duas antes de subir:

```bash
export DATABASE_PASSWORD='sua_senha_do_banco'
export JWT_SECRET="$(openssl rand -base64 48)"
export SEED_DADOS_TESTE=true
./mvnw spring-boot:run
```

no IntelliJ, o caminho é `Run` → `Edit Configurations` → `Environment variables` na configuração do `DoisagApplication`.

com docker, o `docker compose` já passa tudo isso a partir do `.env` — veja o [README da raiz](../README.md#como-executar)

-----

### **usuário de teste**

quando a aplicação sobe, um `CommandLineRunner` cria dois usuários pra você não ter que cadastrar na mão, mais algumas notificações de exemplo:

| perfil | email | senha |
| :--- | :--- | :--- |
| prescritor | `prescritor@email.com` | `123456` |
| paciente | `paciente@email.com` | `123456` |

> ⚠️ esse seed roda em **toda** inicialização, sem distinção de ambiente. num deploy real ele criaria uma conta de prescritor com senha `123456` em produção. limitar ao profile `dev` é parte do RNF15

-----

### **documentação da api**

  * **URL\_BASE**: `http://localhost:8080`
  * **CORS**: liberado pra `http://localhost:5173`. tem duas configurações de cors competindo: um bean global em `SecurityConfigurations` e `@CrossOrigin` espalhado nos controllers (o de `AuthenticationController` é `*`)

#### **autenticação**

o esquema é com token. você manda email e senha, a api devolve um jwt, e a partir daí toda chamada precisa mandar esse token junto

  * **COMO MANDAR O TOKEN**: no header da requisição:
    `Authorization: Bearer <seu-token-jwt>`
  * **VALIDADE**: 2 horas
  * **CLAIMS DO TOKEN**: `sub` (email), `id`, `name`, `role`, `authorities`
  * **PERFIS**:
      * `ROLE_PRESCRIBER`: perfil do **prescritor**
      * `ROLE_PATIENT`: perfil do **paciente**

> antes o prescritor tinha `ROLE_ADMIN` e o paciente `ROLE_USER`. chamar o prescritor de "admin" foi o que levou o controle de acesso a liberar tudo pra ele — o papel passou a dizer o quanto a pessoa pode, em vez de quem ela é. `ROLE_ADMIN` fica reservado pro perfil administrativo da v2.0, que provisiona contas e não vê prontuário

#### **endpoints de autenticação**

##### **1. registrar novo paciente**

  * **ENDPOINT**: `POST /auth/register`
  * **AUTORIZAÇÃO**: pública
  * **CORPO DA REQUISIÇÃO (`RegisterDTO`)**:

| campo | tipo | descrição | regras de validação |
| :--- | :--- | :--- | :--- |
| `name` | `string` | nome completo do paciente | obrigatório |
| `email` | `string` | email que ele vai usar pra logar | obrigatório, formato válido e único |
| `senha` | `string` | senha de acesso | no mínimo 8 caracteres, uma minúscula, uma maiúscula, um número e um caractere especial (`@$!%*?&`) |
| `cpf` | `string` | cpf do paciente | obrigatório, validado por dígito verificador |
| `birthDate` | `string` | data de nascimento (`YYYY-MM-DD`) | obrigatória e no passado |
| `phone` | `string` | telefone com ddd | opcional, mas se vier tem que ser só números |
| `address` | `objeto` | objeto com os dados de endereço | obrigatório |
| `professionalCode` | `string` | código do prescritor que vai acompanhar ele | obrigatório, formato `[A-Z]{3}[0-9]{2}` (3 letras maiúsculas + 2 números) |

  * **RESPOSTA (`201 Created`)**: `{ "message": "Cadastro realizado com sucesso, seja bem-vindo(a)!" }`

##### **2. efetuar login**

  * **ENDPOINT**: `POST /auth/login`
  * **AUTORIZAÇÃO**: pública
  * **CORPO DA REQUISIÇÃO (`LoginDTO`)**: `{ "email": "...", "senha": "..." }`
  * **RESPOSTA (`200 OK`)**: `{ "token": "<jwt>" }`

#### **endpoints principais (CRUD)**

todas essas rotas exigem autenticação. cada uma tem `GET`, `GET /{id}`, `POST`, `PUT /{id}` e `DELETE /{id}`

| rota | entidade |
| :--- | :--- |
| `/paciente` | paciente |
| `/prescritor` | prescritor |
| `/consulta` | consulta clínica |
| `/anamnese` | anamnese |
| `/acompanhamento` | ficha de acompanhamento semanal |
| `/escala-hamilton` | escala de ansiedade de hamilton |
| `/escala-pittsburgh` | índice de qualidade do sono de pittsburgh |
| `/mini-exame` | mini-exame do estado mental (meem) |
| `/registro-dor` | registro diário de dor |
| `/registro-sono` | registro diário de sono |
| `/registro-tea` | registro de sintomas (tea) |

rotas extras de paciente:

  * **`GET /paciente/prescritor/{prescriberId}`**: lista os pacientes de um prescritor
  * **`POST /paciente/cadastrar-para-prescritor`**: o prescritor logado cadastra um paciente já vinculado a ele. o vínculo vem do token, não do corpo da requisição. corpo: `PatientRegistrationDTO` (`name`, `email`, `senha`, `cpf`, `phone`, `birthDate`, `address`), todos validados

> **`POST /paciente` foi removido.** ele criava paciente com a senha em texto puro e sem validação nenhuma, gerando conta que nunca conseguia logar. os caminhos certos são `/auth/register` (o próprio paciente se cadastra) e `/paciente/cadastrar-para-prescritor` (o prescritor cadastra)

> `/paciente` e `/prescritor` **não aceitam mais a entidade crua** no corpo. `PUT /paciente/{id}` recebe `PatientUpdateDTO`, `POST /prescritor` recebe `PrescriberCreateDTO` e `PUT /prescritor/{id}` recebe `PrescriberUpdateDTO`. senha, `id` e vínculos ficam fora desses DTOs de propósito

> `POST /prescritor` exige `ROLE_ADMIN`. como ninguém tem esse papel ainda, quem cria prescritor na prática é o seed. era uma rota pública que criava conta com privilégio (RF02.2)

#### **prescrição**

  * **`POST /consulta/{appointmentId}/prescricao`**: cria prescrição dentro de uma consulta
      * corpo (`PrescriptionCreateDTO`): `productDescription`, `posology`, `brand`, `concentration`, `spectrum`, `observation`
  * **`GET /prescricao`** e **`GET /prescricao/{id}`**: consulta
  * **`PUT /prescricao/{id}`**: atualiza (sobrescreve, sem guardar versão anterior)
  * **`DELETE /prescricao/{id}`**: remove
  * **`GET /appointments/{appointmentId}/prescriptions`**: prescrições de uma consulta

#### **notificações**

  * **`GET /notifications`**: notificações do usuário logado
  * **`POST /notifications/{id}/read`**: marca uma como lida
  * **`POST /notifications/read-all`**: marca todas como lidas
  * **`DELETE /notifications/{id}`**: remove

hoje as notificações são criadas em três situações: novo paciente vinculado, escala designada ao paciente, e eventos de agendamento

-----

### **endpoints de lógica de negócio**

#### **1. dashboards (`/dashboard`)**

  * **`GET /dashboard/prescritor/{id}`**: nº de pacientes ativos, consultas do dia e formulários pendentes
  * **`GET /dashboard/paciente/{id}`**: escalas pendentes e consultas futuras

> ⚠️ o campo `upcomingAppointments` do dashboard do paciente retorna **sempre lista vazia** — a busca não foi implementada (`DashboardService`)

#### **2. ciclo de tarefas de escalas**

esse fluxo permite que um prescritor envie uma escala para o paciente e que o sistema dê baixa nela automaticamente

  * **`POST /pacientes/{patientId}/escalas`**: designa uma nova escala para um paciente

      * **CORPO DA REQUISIÇÃO (`AssignScaleDTO`):**
        ```json
        {
          "scaleType": "ESCALA_HAMILTON"
        }
        ```
      * **valores possíveis**: `ESCALA_HAMILTON`, `ESCALA_PITTSBURGH`, `MINI_EXAME_ESTADO_MENTAL`, `REGISTRO_DOR`, `REGISTRO_SONO`, `REGISTRO_TEA`, `ANAMNESE`, `ACOMPANHAMENTO_SEMANAL`
      * **RESPOSTA (`201 Created`):** retorna o objeto da tarefa criada

  * **`GET /pacientes/{patientId}/escalas`**: lista as escalas designadas

  * **`GET /pacientes/{patientId}/escalas/central`**: dados prontos da central de escalas, separados em pendentes e histórico

  * **para concluir uma tarefa**: não há endpoint específico. quando o paciente submete o formulário correspondente (ex: `POST /escala-hamilton`), o backend atualiza o status da tarefa de `PENDENTE` para `CONCLUIDO`

#### **3. relatórios de progresso**

  * **`GET /pacientes/{patientId}/progresso`**: série histórica de um atributo
  * **PARÂMETROS (obrigatórios):**
      * `atributo`: `DOR`, `SONO`, `HUMOR`, `TREMOR`, `ANSIEDADE`, `DISPOSICAO_ENERGIA`, `FUNCAO_INTESTINAL`, `APETITE`, `CONCENTRACAO`, `INTERACAO_SOCIAL`, `RIGIDEZ_ESPASTICIDADE`, `REDUCAO_SUBSTANCIA`, `NAUSEA_VOMITO`, `DESEMPENHO_ESPORTIVO`, `DERMATOLOGICO`
      * `periodo`: `DIAS_15`, `DIAS_30`, `DIAS_60`, `DIAS_90`
  * **EXEMPLO:** `GET /pacientes/1/progresso?atributo=DOR&periodo=DIAS_30`
  * **RESPOSTA:**
    ```json
    [
      { "date": "2025-06-15", "value": 8 },
      { "date": "2025-06-22", "value": 7 },
      { "date": "2025-06-29", "value": 5 }
    ]
    ```

> a rota funciona pra **qualquer escala**. o atributo já diz de qual escala ele vem, então não precisa passar a escala junto. use `GET /progresso/atributos` pra descobrir o que existe: ele devolve nome, rótulo, escala e a faixa de valores de cada atributo, que é o que a tela usa pra montar os seletores e o eixo do gráfico

#### **4. relatório de sono**

  * **`GET /pacientes/{patientId}/relatorio-sono`**: médias semanais do diário de sono

-----

### **modelos json**

##### **`Address`**

```json
{
  "street": "Rua Exemplo",
  "number": "123",
  "city": "Chapecó",
  "state": "SC",
  "country": "Brasil"
}
```

##### **`PatientResponseDTO` (o que a api devolve)**

```json
{
  "id": 1,
  "name": "Nome do Paciente",
  "cpf": "12345678900",
  "email": "paciente@email.com",
  "birthDate": "1990-01-15",
  "phone": "49999887766",
  "address": { "...": "..." },
  "prescriberId": 1,
  "prescriberName": "Bruna Varela"
}
```

> não existe mais campo `password` em resposta nenhuma da api, e os campos do `UserDetails` (`enabled`, `authorities`, `username`, `accountNonLocked`, `credentialsNonExpired`, `accountNonExpired`) também sumiram. o prescritor agora vem como `prescriberId` e `prescriberName`, em vez de não vir

##### **`Prescription` (prescrição)**

```json
{
    "id": 1,
    "productDescription": "Óleo de Cannabis Full Spectrum 3000mg",
    "posology": "5 gotas, 2x ao dia",
    "brand": "Marca Exemplo",
    "concentration": "100mg/mL",
    "spectrum": "Full Spectrum",
    "observation": "Aumentar a dose após 15 dias, se necessário."
}
```

-----

### **tratamento de erros**

duas classes padronizam as respostas de erro, e as duas devolvem o mesmo formato de json:

  * `ErrorHandler` (`@RestControllerAdvice`) cuida do que acontece **dentro** do controller
  * `SecurityErrorHandler` cuida do que acontece **antes** dele, na cadeia de filtros, onde o `@RestControllerAdvice` não alcança

| status | quando acontece |
| :--- | :--- |
| `400` | validação de dto falhou, regra de negócio violada, corpo ilegível (enum ou tipo inválido) |
| `401` | sem token, token expirado, assinatura inválida, ou credencial errada no login |
| `403` | autenticado mas sem permissão pra aquele recurso |
| `404` | recurso não encontrado |
| `409` | conflito de integridade no banco (registro duplicado) |
| `500` | qualquer erro não previsto |

a diferença entre 401 e 403 importa pro front: **401 quer dizer "faça login de novo"** (a sessão acabou), **403 quer dizer "você está logado mas isso não é seu"**. tratar os dois igual manda o usuário pra tela de login em situação que não é de login

formato do corpo: `timestamp`, `status`, `error`, `message`, `path`. erros de validação de dto trazem também a lista de campos com problema

-----

### **limitações conhecidas**

levantadas na auditoria de 12/09/2026. cada item aponta o requisito da v2.0 que resolve

**segurança** — o sistema não deve ser usado com dado de paciente real até isso ser corrigido

  * ~~toda rota protegida apenas por `anyRequest().authenticated()`~~ **resolvido**: 63 `@PreAuthorize` declaram a regra em cada método, e `@EnableMethodSecurity` está ligado (RF29)
  * ~~`CustomPatientAccessManager` libera acesso total pra qualquer prescritor~~ **resolvido**: substituído pelo `PatientAccessService`, que resolve o vínculo num único lugar. paciente vê só o próprio prontuário, prescritor vê só a carteira dele (RF30)
  * `GET /paciente` deixou de devolver todos os pacientes do sistema e passou a devolver só a carteira do prescritor logado
  * ~~falta cobrir o dono por registro nas 7 rotas de escala~~ **resolvido**: o `AssessmentAccessService` resolve o dono de qualquer uma das 7 escalas (todas herdam de `BaseAssessment`, que sabe de qual paciente é) e delega pro `PatientAccessService`
  * ~~o `POST` das escalas aceita o campo `patient` no corpo~~ **resolvido**: o dono passou a ser sempre o paciente logado, e o `id` é zerado pra `POST` não sobrescrever registro existente
  * o **MEEM** continua com regra só por papel. ele se liga a `Appointment` e não a `Patient`, então a checagem de dono passa pela consulta — falta fazer
  * ~~`POST /prescritor` é público e cria conta com privilégio~~ **resolvido**: exige `ROLE_ADMIN`, papel que ninguém tem ainda. o cadastro de prescritor saiu da tela de sign-up, que agora é só de paciente
  * ~~os controllers retornam entidade jpa crua e o jackson serializa o `password`~~ **resolvido**: `/paciente` e `/prescritor` devolvem DTO, e o `Users` tem `@JsonIgnore` no `password` e nos acessores do `UserDetails` como rede de proteção. os 7 endpoints de escala ainda devolvem a entidade, mas o paciente aninhado já não carrega senha — falta trocar por DTO pra parar de expor o paciente inteiro
  * a chave do jwt e a senha do banco estão em arquivo versionado (RNF15)
  * ~~`PUT /paciente/{id}` grava a senha sem passar pelo `passwordEncoder`~~ **resolvido**: o update não toca mais em senha. troca de senha será fluxo próprio (RN12)
  * ~~token expirado ou inválido retorna **500** em vez de 401~~ **resolvido**: o `SecurityFilter` trata a exceção do jjwt e o `SecurityErrorHandler` responde 401 em json. requisição sem token também passou de 403 pra 401 (RF01, RF29)
  * os `POST` e `PUT` das 7 rotas de escala ainda recebem a entidade jpa direto no `@RequestBody`, então o cliente pode mandar `id` e relacionamentos (mass assignment). `/paciente` e `/prescritor` já foram convertidos pra DTO
  * ~~`PatientRegistrationDTO` não tem validação~~ **resolvido**: agora tem as mesmas regras do `/auth/register` (senha forte, cpf com dígito verificador, e-mail válido) e o controller usa `@Valid`

**escalas clínicas** — os escores não correspondem aos instrumentos

  * **pittsburgh**: o escore é a soma crua de 13 campos, dando faixa de 0 a 39. o psqi real tem 7 componentes derivados e vai de 0 a 21, com corte em 5. o número atual não é comparável a nenhum ponto de corte publicado (RF23)
  * **meem**: só 8 seções implementadas, máximo de 27 pontos. faltam leitura, escrita e cópia dos pentágonos pra fechar os 30 (RF26)
  * **hamilton**: 13 itens em vez de 14, máximo de 52 em vez de 56 (RF21)
  * ~~os campos das escalas usam `int` primitivo, item não respondido vira `0`~~ **resolvido**: os 89 campos viraram `Integer`, escala com item faltando fica **sem escore** em vez de somar só o que veio, e o gráfico de progresso pula o dia em vez de plotar zero (RN10)

**funcionalidades incompletas**

  * não existe nenhum `@Scheduled` no projeto: o ciclo automático de acompanhamento de 90 dias e os lembretes de dose não existem. toda escala é designada manualmente (RF32, RF34)
  * `ESCALA_PITTSBURGH`, `REGISTRO_DOR` e `REGISTRO_TEA` apontam pra rotas de frontend que não existem — designar essas três leva o paciente a uma tela em branco (RF08)
  * `MINI_EXAME_ESTADO_MENTAL` é designável ao paciente, mas quem preenche é o prescritor e `MentalStateExamService` não dá baixa na tarefa. a tarefa nunca conclui (RN09)
  * `CompletedScaleInfoDTO` devolve a string fixa `"Concluído"` no lugar do resultado da escala (RF08)
  * não existe exportação em pdf nem csv (RF33)
  * **parcialmente resolvido**: toda entidade clínica ganhou `createdAt` e `updatedAt`, preenchidos pelo Spring. ainda falta o **autor** da alteração e o registro imutável de quem acessou o quê, que é o resto do RF31
  * `PUT /prescricao/{id}` sobrescreve a prescrição sem guardar a versão anterior (RF05)

**dados e infraestrutura**

  * ~~`Appointment.modality` e `Appointment.status` são `String` livre~~ **resolvido**: viraram `AppointmentModality` (`PRESENCIAL`, `REMOTA`) e `AppointmentStatus` (`AGENDADA`, `EM_ANDAMENTO`, `CONCLUIDA`, `CANCELADA`), guardados como texto no banco (RF04)
  * `Prescription.spectrum` ainda é `String` livre, apesar de a RN03 definir três valores. fica pendente porque o fluxo de prescrição precisa ser refeito antes (ver abaixo)
  * ~~campos de texto clínico viram `varchar(255)` e truncam~~ **resolvido**: 30 campos de resposta aberta passaram a `TEXT` — os da consulta, os 18 descritivos da anamnese, descrição e posologia da prescrição, comentário do acompanhamento e a mensagem de notificação (RN11)
  * ~~`cpf` não tem restrição de unicidade~~ **resolvido**: `@Column(unique = true)` no `Users`, então o mesmo paciente não entra duas vezes com o histórico partido em duas fichas (RN04)
  * ~~duas fontes de verdade pro esquema~~ **resolvido**: o esquema vem de `src/main/resources/db/migration/`, e o hibernate ficou em `validate` — ele só confere, não altera mais nada sozinho (RNF14)
  * nenhum endpoint pagina ou ordena. não existe `Pageable` no projeto, apesar do `relatorio.pdf` §2.11 descrever ordenação por `?sort=name,asc` e busca por `?search=` (RNF06)
  * `messages.properties` não está em utf-8, e a codificação de plataforma do ambiente é `Cp1252`. qualquer texto acentuado nesse arquivo sai corrompido (RNF12)
  * o `MessageSource` resolve mensagem com `Locale.getDefault()`, a locale do servidor, não a do usuário. só existe `messages.properties`, sem `_en` nem `_es` (RNF12)
  * `DashboardService` acessa associações `LAZY` dentro de stream, sem `@Transactional` e sem join fetch. funciona por causa do `open-in-view` que o spring boot habilita por padrão, mas gera n+1
  * dependência circular entre `PatientService`/`ScaleAssignmentService` e `NotificationService`, contornada com `@Lazy` em setter. `ScaleAssignmentService` injeta o mesmo bean duas vezes (construtor e setter)
  * `ScaleType` já guarda `displayName` e `path`, mas `ScaleAssignmentService` reimplementa os dois em `switch` de 8 casos (RNF08)
  * ~~o progresso só lê a ficha de acompanhamento, com um `switch` de 15 campos dentro do serviço~~ **resolvido**: cada escala responde pelo próprio `trackedValue`, e o serviço não conhece campo de escala nenhuma. acrescentar escala é registrar o repositório e implementar o método (RNF08)
  * lombok está no `pom.xml` como dependência e annotation processor, mas não é usado em nenhuma classe
  * `AuthorizationManager.check(...)`, usado em `CustomPatientAccessManager`, está deprecado no spring security 6.5 em favor de `authorize(...)`

**testes**

  * a suíte cobre hoje apenas a subida do contexto, a negação de acesso sem token e o cálculo do escore do Hamilton. **falta teste das regras de autorização** (RF29, RF30) e do escore das outras escalas (RF23, RF26). esses serão escritos junto com as correções, pra nascerem verdes
  * o **frontend ainda não usa** a diferença entre 401 e 403: as 15 telas montam o `fetch` na mão e tratam qualquer erro igual. mandar o usuário pro login só no 401 depende do cliente http centralizado (RNF15)
