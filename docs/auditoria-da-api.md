# Auditoria da API antes do piloto

Levantamento do que impede liberar o 2AG para teste com a clínica. Feito em 20/09/2026 sobre o commit `b9049bf`, lendo o código de ponta a ponta: 23 controllers, 25 serviços, 16 repositórios, 16 migrações, as 8 escalas do catálogo e as ~50 telas que consomem a API.

**Veredito:** o sistema não está pronto para receber paciente real. Dois itens são impeditivos por si só (não há HTTPS em lugar nenhum; o termo de consentimento é um rascunho com campos em branco) e outros oito quebram fluxos que a clínica vai usar no primeiro dia. Nenhum deles é reescrita: a maior parte são correções de poucas linhas em pontos já mapeados. Fora isso, o sistema está estruturalmente íntegro — nenhuma tela chama rota que não existe, nenhum campo lido pelo front falta no DTO, a autorização cobre todas as rotas e as 16 migrações aplicam num Postgres limpo.

| Severidade | Quantidade | O que significa |
| :--- | ---: | :--- |
| Bloqueador | 2 | Impede usar com paciente real, por segurança ou por lei |
| Alto | 10 | Quebra um fluxo que a clínica usa no piloto |
| Médio | 38 | Atrapalha, tem contorno, mas aparece no uso normal |
| Baixo | 37 | Polimento, dívida técnica e risco futuro |

Os códigos (`AUTZ`, `CLIN`, `CONTRATO`, `ERRO`, `JOBS`, `NOOP`, `BANCO`, `OPS`, `LACUNA`) indicam a frente em que o achado apareceu e servem para rastrear a correção.

---

## Ondas de correção sugeridas

**Onda 1 — antes de qualquer teste com paciente real.** Os dois bloqueadores e os altos: `OPS-01`, `OPS-02`, `OPS-04`, `OPS-05`, `OPS-06`, `LACUNA-02`, `AUTZ-01`, `CLIN-01`, `NOOP-01`, `LACUNA-01`, `JOBS-03`, `JOBS-04`. São o que impede subir no servidor, entrar no sistema, recuperar senha, confiar no dado clínico e cumprir a LGPD.

**Onda 2 — antes de abrir para mais de dois ou três pacientes.** Os médios de escala e agenda, que produzem dado errado silenciosamente: `NOOP-05`, `NOOP-09`, `CLIN-02`, `CLIN-03`, `CLIN-06`, `LACUNA-03`, `LACUNA-04`, `CONTRATO-04`, `LACUNA-05`, `BANCO-01`, `BANCO-02`, `ERRO-01`.

**Onda 3 — durante o piloto.** O ruído de avisos (`JOBS-02`/`CLIN-07`, `JOBS-06`), a exportação (`JOBS-07`, `JOBS-08`, `JOBS-09`), o desempenho (`BANCO-05`, `BANCO-06`, `OPS-11`, `OPS-12`) e o restante dos baixos.

---

## Bloqueadores

### OPS-01 — Não existe HTTPS em nenhum ponto da pilha

**O que acontece.** `frontend/nginx.conf:4` só tem `listen 80;`: não há `listen 443 ssl`, nenhum certificado, nenhum redirecionamento. O `docker-compose.yml:72-73` publica `"5173:80"`, e essa é a única porta do sistema. Uma busca por `certbot`, `letsencrypt`, `ssl_certificate` ou `listen 443` no repositório inteiro não retorna nada. O `Strict-Transport-Security` que o `security-headers.conf:16` envia é ignorado pelo navegador quando a página veio por HTTP.

**Por que bloqueia.** Senha de login, cookie de sessão, prontuário, anamnese, escalas e CSV exportado atravessam a rede da clínica em texto puro. Qualquer pessoa no mesmo wi-fi lê e copia o cookie de sessão. O RNF05 (`docs/requisitos-v2.md:499`) trata HTTPS como essencial, e não há como considerá-lo atendido.

**Como corrigir.** Duas opções:

- **Caminho curto:** pôr um proxy com TLS automático (Caddy ou Traefik) na frente do serviço `web`, deixando o nginx atual como está. Resolve certificado, renovação e redirecionamento de uma vez.
- **Caminho direto no nginx:** acrescentar um `server` com `listen 443 ssl` e `ssl_certificate` no próprio `frontend/nginx.conf` (o Dockerfile copia o arquivo inteiro para `conf.d/default.conf`), trocar as portas do compose para `"80:80"` e `"443:443"` e criar o bloco `volumes: ["./certs:/etc/nginx/certs:ro"]`, que hoje não existe no serviço `web`.

Em qualquer variante, o mesmo commit precisa levar `SESSION_SECURE_COOKIE=true` no `.env.example` (ver `OPS-10`) e `PUBLIC_URL` com `https://`. Antes do TLS existir, virar o cookie para `Secure` deixa o login impossível — os dois andam juntos.

### OPS-02 — O termo de consentimento que o paciente aceita é um rascunho

**O que acontece.** `backend/doisag/src/main/resources/consent/termo-de-consentimento.txt` começa, na linha 1, com "RASCUNHO — este texto ainda precisa ser revisado e aprovado pela clínica antes do uso com pacientes"; a linha 8 diz "[NOME DA CLÍNICA], responsável pelo seu atendimento" e a linha 33 "Dúvidas e pedidos sobre os seus dados: [CONTATO DO ENCARREGADO DE DADOS DA CLÍNICA]". O `ConsentTermService.java:52-58` lê o arquivo do classpath sem nenhuma substituição, a versão gravada no aceite é `2026-09-rascunho` (`ConsentTermService.java:21`) e o front imprime o texto cru (`sign-up.jsx:378`).

**Por que bloqueia.** Todo paciente do piloto lê a palavra RASCUNHO e dois campos em branco na tela de cadastro, e o aceite fica registrado contra essa versão. Consentimento para dado sensível de saúde (art. 11 da LGPD, RF36) colhido sobre um texto que não identifica o controlador nem o encarregado não se sustenta, e o aceite não pode ser refeito depois sem reconvocar o paciente.

**Como corrigir.** É tanto técnico quanto de processo:

1. A clínica precisa aprovar o texto final e informar a razão social e o contato do encarregado de dados (está aberto em `docs/extensao/perguntas-para-a-clinica.md`, item 5).
2. No código, trocar os marcadores por configuração: `api.clinic.name: ${CLINIC_NAME:}` e `api.clinic.dpo-contact: ${CLINIC_DPO_CONTACT:}`, substituindo no `readTermFile()`. Não falhar na subida com valores vazios — o perfil de teste e o fluxo local não definem essas variáveis; quando vierem em branco, manter o marcador e emitir `log.warn`.
3. Acrescentar `CLINIC_NAME` e `CLINIC_DPO_CONTACT` ao `.env.example` e ao bloco `environment` da api no compose.
4. Apagar a linha 1 e trocar `CURRENT_VERSION` só depois do aval. Atenção: aceites já gravados ficam presos à versão antiga, e o sistema vai pedir novo aceite — o que é o comportamento correto.

---

## Altos

### OPS-04 — `PUBLIC_URL` vem com `localhost` e derruba com 403 tudo que grava

**O que acontece.** `.env.example:40`, `application.yml:77` e `docker-compose.yml:47` trazem `http://localhost:5173` como padrão — diferente de `POSTGRES_PASSWORD` e `JWT_SECRET`, que vêm em branco e por isso pedem atenção. O `OriginCheckFilter` (introduzido na correção de CSRF) compara o cabeçalho `Origin` com a origem do `PUBLIC_URL` e recusa POST, PUT, PATCH e DELETE feitos com o cookie de sessão.

**Impacto.** A clínica sobe no servidor e abre em `http://ip-do-servidor:5173`. O login funciona (nesse momento ainda não há cookie), a pessoa entra, vê o painel — e a partir daí toda ação que grava morre com 403: criar prescritor, gerar convite, cadastrar paciente, marcar consulta, responder escala, salvar prescrição. Até o logout falha. Como a entrada funciona, o diagnóstico fica dificílimo.

**Como corrigir.** Tirar o valor padrão dos três arquivos, deixando `PUBLIC_URL` obrigatório como o segredo do JWT. Os testes não quebram (`application-test.yml:32` já define o valor explicitamente), mas o bloco "rodando sem docker" do `README.md:70-77` precisa passar a exportar `PUBLIC_URL` — senão o `./mvnw spring-boot:run` deixa de subir.

### OPS-05 — O backup documentado não alcança o banco na instalação documentada

**O que acontece.** O serviço `banco` não publica porta nenhuma (`docker-compose.yml:22`, decisão correta), mas `scripts/backup-banco.sh:16-17` usa `DATABASE_HOST:-localhost` e chama `pg_dump --host "$SERVIDOR"`. A documentação (`docs/backup-e-restauracao.md:21-33` e `README.md:146-149`) manda rodar exatamente assim, inclusive no cron das 3h.

**Impacto.** Seguindo a documentação, o cron falha toda noite com conexão recusada, e como ele redireciona para um log que ninguém lê, o piloto roda meses achando que tem backup diário. Na hora de restaurar, não há arquivo. A tabela de restaurações testadas (`backup-e-restauracao.md:68-70`) está vazia, e o RNF07 é pré-requisito do piloto.

**Como corrigir.** Rodar o script dentro da rede do compose: `docker compose run --rm -e DATABASE_HOST=banco -v "$PWD/scripts:/scripts:ro" -v /var/backups/2ag:/backups banco /scripts/backup-banco.sh /backups`. Reescrever as seções correspondentes da documentação, acrescentar `BACKUP_DIR` ao `.env.example` e trocar o `read -r -p` interativo de `restaurar-banco.sh:29` por uma variável de confirmação, porque ele não funciona sem TTY. Se a clínica preferir rodar do host, o `README.md:15` precisa listar `pg_dump`/`pg_restore` 17 como pré-requisito. O RNF07 só fecha quando a primeira restauração for testada de verdade e anotada.

### OPS-06 — Sem SMTP não existe recuperação de senha, e o contorno documentado não chega ao container

**O que acontece.** `.env.example:48` entrega `MAIL_HOST=` vazio, e nesse caso o `EmailConfig` cai no `LogEmailSender`, que sem `EMAIL_LOG_TEXT=true` escreve apenas "e-mail para X com assunto Y não enviado" — o link se perde. E `EMAIL_LOG_TEXT` **não está** no bloco `environment` da api no `docker-compose.yml:38-55`: pôr a variável no `.env` não surte efeito nenhum sob docker. O mesmo vale para `SESSION_DURATION_MINUTES`, `SESSION_MAX_HOURS`, `LOGIN_MAX_FAILURES`, `LOGIN_MAX_FAILURES_PER_ADDRESS` e `LOGIN_BLOCK_MINUTES`, todas documentadas como ajustáveis.

**Impacto.** No primeiro dia do piloto, o prescritor que esquecer a senha que o administrador escolheu por ele fica fora do sistema de forma permanente: o e-mail não sai, o link não aparece no log, o administrador não tem botão para redefinir e o contorno documentado não funciona. A única saída é um `UPDATE` na tabela de usuários com um hash BCrypt gerado na mão.

**Como corrigir.** Duas frentes:

1. Repassar as variáveis que faltam no compose (`EMAIL_LOG_TEXT`, as de sessão e as de bloqueio de login). Deixar `SERVER_PORT` e `JPA_SHOW_SQL` de fora de propósito: a porta 8080 está fixa no healthcheck e no `proxy_pass`.
2. Ter SMTP de verdade no piloto, ou criar `POST /admin/prescribers/{id}/password-reset` que devolva o link na resposta. Como essa rota permite tomar a conta de um prescritor, ela precisa exigir a senha do próprio admin no corpo, registrar o evento na trilha de auditoria e reaproveitar o token de uso único de 30 minutos que já existe. Para a senha do próprio administrador não há saída sem SMTP — o `AdminAccountCreator` pula quando a conta já existe.

### LACUNA-02 — O administrador escolhe e conhece a senha do prescritor, e nada obriga a troca

**O que acontece.** `PrescriberCreateDTO.java:22-23` exige `password` no corpo que o administrador envia, e `PrescriberService.java:60` grava esse valor. A tela confirma o desenho: "Conta de X criada. Passe o e-mail e a senha inicial para a pessoa entrar" (`admin-prescribers.jsx:129`). Não existe marca de senha provisória em `Users`, e o login não confere nada disso.

**Impacto.** A separação entre administração e prontuário — o requisito mais importante do documento, declarado no próprio `AdminPrescriberController.java:23` ("o administrador vê só dado cadastral e nunca o prontuário") — vale apenas enquanto o administrador escolhe não usar a senha que ele mesmo definiu. Entrando como o prescritor, ele abre consultas, prescrições, anamnese e escalas de todos os pacientes, e a trilha de auditoria registra **o prescritor** como autor: o registro legal fica errado justamente no caso que ele existe para cobrir.

**Como corrigir.** O ideal é tirar `password` do DTO e da tela e criar a conta sem senha utilizável, disparando o fluxo de recuperação para o e-mail do prescritor. Como o SMTP pode não estar pronto, a alternativa é um campo `passwordChangeRequired` em `Users` (migração nova), marcado na criação pelo administrador e verificado no `SecurityFilter`, liberando apenas `GET /auth/me` e `PUT /profile/password` até a troca. Registrar a troca na trilha.

### AUTZ-01 — Não existe forma de revogar o acesso de uma conta de paciente

**O que acontece.** O único ponto do código de produção que chama `setActive` é `PrescriberService.java:72-76`, alcançável apenas por `PUT /admin/prescribers/{id}/active`. Não há nenhum controller de paciente sob `/admin`. Arquivar não revoga: `PatientArchiveService.java:41-42` grava só `archivedAt` e `archivedBy`, e o `SecurityFilter.java:63` só olha `isActive()`, nunca `isArchived()`.

**Impacto.** Depois que a conta de um paciente existe, ela funciona para sempre. Se um participante desistir e retirar o consentimento (RF36/LGPD), se a senha dele vazar, ou se a clínica quiser desligar as contas de teste no fim do piloto, não há ação no sistema que faça isso. O arquivamento, que é o gesto que a clínica vai usar achando que "fecha" o paciente, não tira o acesso.

**Como corrigir.** Criar `PUT /admin/patients/{patientId}/active` com `@PreAuthorize("hasRole('ADMIN')")`, gravando `setActive(active)` e, quando `false`, também `setSessionsEndedAt(agora)` para derrubar a sessão aberta. O `SecurityFilter` e o `AuthService` já barram as duas condições e já devolvem "Conta desativada", então nada mais precisa mudar no login. Manter o `PatientArchiveService` como está: arquivar é ato clínico, desativar é ato de conta, e a guarda de prontuário de 20 anos continua intacta.

### CLIN-01 — Escala validada respondida pela metade fecha a tarefa como respondida

**O que acontece.** Escala incompleta ficar sem escore é decisão registrada e testada (RN10). O defeito é o que o sistema faz **depois** do escore nulo: `ScaleTaskService.java:126-128` fecha a tarefa como RESPONDIDA para todo modo diferente de diário, `ScaleResponseService.java:124` avisa o prescritor que a escala foi respondida, e `ScaleCatalog.resultTextOf` devolve o texto fixo "Respondida" no lugar do escore. No front, nenhum item é obrigatório.

**Impacto.** O paciente marca um único item do HAM-A, salva, e o sistema trata a semana como cumprida: a tarefa sai da pendência, o lembrete para de cobrar (o filtro exige zero respostas), o painel do prescritor limpa a linha e o aviso "Escala respondida" é enviado. No gráfico o ponto some, porque escore nulo é filtrado. Num piloto de 90 dias isso produz semanas silenciosamente vazias de HAM-A e PSQI — exatamente os dados que justificam o sistema.

**Como corrigir.** Continuar gravando a resposta parcial (a regra RN10 está certa) e mudar as consequências:

1. Em `ScaleTaskService.linkResponse`, só chamar `closeAsAnswered` quando a definição não tiver escore **ou** `response.getScore() != null`; escala pontuada incompleta mantém a tarefa PENDENTE.
2. Em `ScaleResponseService.java:124`, notificar o prescritor pelo mesmo critério.
3. Em `ScaleCatalog.resultTextOf`, devolver "Incompleta: sem escore" quando `hasScore()` e o escore for nulo, em vez de "Respondida".
4. No front, marcar como obrigatórios os itens de escala pontuada (o `maxScore` já vem no DTO), para o paciente ver o aviso antes de enviar.

### NOOP-01 — Resposta de escala de período nunca amarra na tarefa

**O que acontece.** A tarefa nasce com `periodStart = hoje` (`ScaleTaskService.java:100-101`), mas a resposta de escala de período nasce com `periodStart` no passado: o front abre a grade em `hoje-6` e envia essa data. Como `linkResponse` exige `coversDay(response.getPeriodStart())` e `coversDay` devolve falso para qualquer dia anterior ao início da tarefa, o vínculo não acontece. Atinge `REGISTRO_DOR` e `REGISTRO_TEA`.

**Impacto.** Tarefa designada no dia D; o paciente responde em D+2; o vínculo falha. A tarefa continua PENDENTE com zero respostas: o lembrete cobra mesmo assim, o painel do prescritor mostra a escala como atrasada e o fechamento automático marca NÃO_RESPONDIDA — com o paciente tendo respondido. Na central dele a pendência não some, então ele responde de novo e cria respostas de semanas sobrepostas. Só funciona se responder exatamente no último dia da janela.

**Como corrigir.** Amarrar pela data do envio, não pela janela informada: em `ScaleTaskService.linkResponse`, trocar a condição por `!openTask.get().coversDay(LocalDate.now())` e chamar `closeAsAnswered(task, LocalDate.now())`. Isso resolve as escalas de período e também o preenchimento retroativo do diário, sem abrir a porta para uma resposta antiga fechar tarefa nova.

### LACUNA-01 — Emitir prescrição a partir de consulta antiga rebaixa a que estava valendo

**O que acontece.** `PrescriptionService.java:62-63` chama `replaceCurrentPrescriptions(patientId)` sem comparar a data da consulta que está recebendo a nova receita: toda prescrição VIGENTE vira SUBSTITUÍDA e a nova nasce vigente, mesmo que a consulta dela seja de três meses atrás. O gatilho está na tela: o histórico oferece "Emitir prescrição" em **todas** as consultas passadas.

**Impacto.** O prescritor que volta a uma consulta antiga para lançar uma receita que faltava — lançamento atrasado, caso comum — derruba silenciosamente a prescrição em uso. A partir daí o painel do paciente e a tela "Minhas prescrições" mostram a dose antiga como a que vale agora. Não há desfazer: anular a errada não devolve a anterior (`NOOP-04`), e a única saída é reemitir numa consulta nova.

**Como corrigir.** No serviço, só substituir a vigente quando a consulta da nova prescrição for mais recente que a consulta da vigente atual; caso contrário, gravar a nova já como SUBSTITUÍDA (registro histórico) e manter a vigente. No front, oferecer "Emitir prescrição" apenas na consulta mais recente e, nas demais, usar um rótulo do tipo "Lançar receita no histórico", avisando que ela não passa a valer.

### JOBS-03 — Remarcar a consulta não limpa a marca de lembrete (inclui `NOOP-03`)

**O que acontece.** `AppointmentService` remarca a consulta trocando data, modalidade, duração e status, mas não zera `reminderSentAt`. O campo só é escrito pelo `ReminderService` — não há `setReminderSentAt(null)` em lugar nenhum do serviço de agenda.

**Impacto.** A consulta de amanhã recebe o lembrete às 8h; durante o dia o paciente pede para remarcar e o prescritor remarca para a semana seguinte. Como `reminderSentAt` já está preenchido, o filtro do lembrete descarta a consulta para sempre: o paciente nunca é avisado do horário novo, justamente a consulta em que ele mais corre risco de esquecer, porque a data mudou.

**Como corrigir.** Acrescentar `appointment.setReminderSentAt(null);` junto dos outros setters de `reschedule`, e um caso no `ReminderTest` cobrindo remarcar depois do lembrete. Cancelamento não precisa: consulta cancelada já sai do filtro por status.

### JOBS-04 — Falha de envio de e-mail some no log e o lembrete é consumido mesmo assim

**O que acontece.** `SmtpEmailSender.java:30-36` captura toda `MailException` e apenas registra um `log.error`; o método é `void` e ninguém a montante sabe se o e-mail saiu. O `ReminderService` grava `reminderSentAt` logo depois, consumindo o lembrete. O `PasswordResetService` também não olha o resultado do envio (a resposta genérica ao usuário é proposital e deve continuar).

**Impacto.** Configuração plausível no servidor do piloto e todo envio falha em silêncio: o paciente que pede senha nova nunca recebe o link, e o job conta como enviado o lembrete que não chegou. O único sinal é uma linha de log que ninguém está olhando.

**Como corrigir.** Fazer `EmailSender.send` devolver `boolean`; no `ReminderService`, registrar `log.warn` com o id do paciente quando falhar e **não** gravar `reminderSentAt` nesse caso. Acrescentar guarda na subida em `EmailConfig`: se o remetente resolvido ficar em branco, lançar `IllegalStateException`, no mesmo padrão já usado pelo `OriginCheckFilter`.

---

## Médios

### Escalas e acompanhamento automático

- **`NOOP-05` — anular resposta deixa a tarefa como respondida.** A anulação grava só o `Annulment`; nenhuma contagem filtra anulação (`countByTaskId`). A tarefa continua RESPONDIDA, some da pendência e o paciente nunca é chamado a responder de novo; pior, ele também não consegue refazer aquele período, porque o serviço reaproveita o registro anulado e recusa alteração. **Correção:** criar `countByTaskIdAndAnnulmentAnnulledAtIsNull` e trocar os usos em `ScaleTaskService`, `DashboardService` e `ReminderService`; ao anular, voltar a tarefa para PENDENTE limpando `answeredAt` e `reminderSentAt`, ou designar tarefa nova se o período já tiver terminado.
- **`CLIN-02` — escala avulsa desloca o ciclo de 90 dias.** O RF32 manda a avulsa não interferir, mas `lastTaskOf` pega qualquer tarefa, inclusive a avulsa de 7 dias. Um PSQI avulso faz o PSQI mensal do protocolo sair 8 dias depois em vez de 30. **Correção:** marcar a origem na `ScaleTask` (um booleano `fromProtocol` basta) e filtrar por ele no `lastTaskOf`; manter a deduplicação olhando qualquer tarefa aberta da escala.
- **`CLIN-03` — dias preenchidos fora da janela não contam.** A tarefa começa no dia em que o job roda; a tela monta a grade sempre a partir da segunda-feira. Os dias anteriores ao início da tarefa gravam resposta, aparecem no gráfico, mas não entram na contagem "X de 7 dias". **Correção:** no front, quando o modo for diário, montar a grade de `periodStart` até `min(periodEnd, hoje)` usando a tarefa aberta que o overview já devolve. Não alinhar a tarefa à segunda no backend — isso quebra o contrato dos 7 dias da avulsa e não tem definição para quinzenal e mensal.
- **`CLIN-06` — MEEM incompleto mostra " de 30" sem faixa.** Pular uma seção grava escore nulo e a tela imprime o cartão vazio, sem explicação. Como o MEEM não tem correção, só anulação, a prescritora precisa refazer o exame com o paciente na sala. **Correção:** no front, conferir todos os itens antes de enviar e listar as seções faltantes; no `applyMentalStateExam`, recusar exame incompleto com mensagem própria (é caminho do prescritor, separado do caminho do paciente).
- **`LACUNA-04` — item marcado por engano não pode ser desmarcado.** Todos os itens são `radio`, sem opção "não respondi" e sem limpar. Um toque errado no celular vira dado clínico permanente: nas escalas validadas muda o escore e a faixa; nas outras, vira ponto no gráfico onde deveria haver lacuna, o contrário do que a RN10 pede. **Correção:** botão discreto "limpar resposta" por item chamando `onChange(item.key, "")` — o payload já descarta string vazia e o backend regrava o mapa sem a chave.
- **`NOOP-07` — anamnese entra no acompanhamento automático.** `/scales/assignable` inclui a anamnese, e o protocolo aceita: o paciente passa a ser cobrado a refazer a ficha de triagem inteira a cada período. **Correção:** recusar `ANAMNESE` no serviço do protocolo e criar um marcador próprio no enum para a lista de escalas designáveis periodicamente, mantendo `/scales/assignable` servindo o envio avulso.
- **`NOOP-08` — MEEM aplicado duas vezes na mesma consulta cria dois registros.** O caminho do prescritor sempre cria linha nova, sem procurar exame já gravado; não há índice único. Ficam duas respostas válidas, com escores diferentes, no mesmo atendimento. **Correção:** verificar MEEM não anulado da consulta antes de gravar e recusar com mensagem; na tela, carregar o exame já aplicado e mostrar o resultado em vez do formulário em branco.
- **`CLIN-04` — MEEM não aparece no gráfico de evolução.** O enum que alimenta o seletor não tem entrada para o MEEM, embora o exame tenha escore de 0 a 30 e o RF27 exija o seletor funcionando para toda escala pontuada. **Correção:** acrescentar `ESCORE_MEEM(ScaleType.MINI_EXAME_ESTADO_MENTAL, null)` ao `TrackableAttribute`; o resto já funciona pelo catálogo. Decidir se o paciente também deve ver esse atributo — hoje a rota é liberada aos dois perfis.
- **`CLIN-08` — falta a ação "ver progresso" na central de escalas.** Critério de aceite do RF08 que não foi implementado; a tela de progresso já sabe receber filtro pronto por `location.state`. **Correção:** segundo botão na linha, passando o **nome** do atributo (não o `scaleType`), que é o campo que a tela de progresso compara.

### Agenda e consulta

- **`CONTRATO-04` — pedido não respondido some das telas quando a data passa.** Os dois endpoints que alimentam as telas cortam por `dateTime > agora`, e nenhuma rotina fecha pedido vencido: o registro fica SOLICITADA para sempre e o paciente nunca recebe resposta. **Correção:** `declineExpiredRequests` no job diário, marcando RECUSADA e disparando a mesma notificação da recusa manual. Isso também fecha o rastro do `LACUNA-05`.
- **`LACUNA-05` — sem limite de pedidos em aberto, e cada pedido segura um horário.** Pedido SOLICITADA retira o horário da lista de livres e nada expira. Um paciente clicando em vários horários esvazia a agenda para os outros. **Correção:** contar os pedidos em aberto do paciente antes de gravar e recusar acima de 2 ou 3, com mensagem explicando; somado à recusa automática do item anterior.
- **`NOOP-09` — consulta passada nunca muda de situação.** `EM_ANDAMENTO` existe no enum e no check do banco, mas nunca é gravado; falta do paciente não tem como ser registrada; e o marcador do gráfico usa `isConfirmed()`, que inclui AGENDADA — consultas que não aconteceram viram marcador de atendimento realizado. **Correção:** dois passos. Primeiro, tirar AGENDADA do marcador do gráfico (barato e de baixo risco). Depois, acrescentar `NAO_COMPARECEU` numa migração que refaça o check e expor `PUT /appointments/{id}/no-show` só para consulta AGENDADA com data passada.
- **`LACUNA-03` — prescrição e MEEM são aceitos em consulta que ainda não aconteceu.** Os dois caminhos só exigem `isConfirmed()`, que inclui AGENDADA, enquanto o registro clínico da mesma consulta recusa data futura. A receita emitida numa consulta futura já nasce vigente e substitui a atual. **Correção:** aplicar a mesma checagem de data já existente no registro clínico, movendo a tolerância de relógio para um lugar comum em vez de duplicar o número.
- **`CONTRATO-01` — registrar consulta agendada para mais tarde no mesmo dia não salva.** O formulário abre com a hora agendada, mas o próprio campo tem `max` no instante atual: o navegador barra o envio por `rangeOverflow`, sem mensagem do sistema. Vale para o cartão "Consultas de hoje" do painel e para "Editar registro" do histórico; a agenda já se protege sozinha. **Correção:** abrir o formulário com o menor valor entre a hora agendada e agora, e esconder o botão Registrar enquanto a consulta for futura.
- **`AUTZ-02` — desativar prescritor deixa os pacientes sem acesso e a agenda aberta.** Nenhuma rota troca o prescritor de um paciente, e a agenda não confere se o prescritor está ativo: o paciente continua vendo horários livres e criando pedidos que ninguém vai responder. Reativar desfaz o estado, então não é impeditivo. **Correção:** devolver lista vazia de horários e recusar pedido quando o prescritor estiver inativo; depois, se a clínica quiser cobrir a saída definitiva, `PUT /admin/patients/{id}/prescriber`.
- **`AUTZ-03` — paciente arquivado continua escrevendo no prontuário.** Nada consulta `isArchived()` fora do protocolo de 90 dias: o arquivado continua pedindo consulta (segurando horário) e criando anamnese nova. **Correção:** guarda no início de `request`, `getFreeSlotsForPatient` e `AnamnesisService.create`. Deixar a resposta de escala como está — é comportamento documentado de propósito.

### Prescrição

- **`NOOP-04` — anular a prescrição vigente deixa o paciente sem nenhuma.** A anterior não volta a valer e a anulada continua com status VIGENTE. **Correção:** antes de programar "volta a valer a anterior", confirmar com a prescritora (nem sempre é o que ela quer). O mínimo seguro: ao anular uma vigente, marcar como SUBSTITUÍDA e avisar na resposta e no painel que o paciente ficou sem prescrição em uso.
- **`NOOP-06` — anular a consulta não anula o MEEM aplicado nela.** O exame continua válido no histórico e sai como "Anulada: não" no CSV. **Correção:** bloquear a anulação da consulta enquanto houver MEEM não anulado, com mensagem própria — mesmo tratamento que a consulta já dá para prescrição.

### Avisos, e-mail e exportação

- **`JOBS-02` + `CLIN-07` — o diário gera um aviso por dia.** Cada resposta nova avisa o prescritor, e nas escalas diárias há um registro por dia. Com 15 ou 20 pacientes, a lista de avisos recebe mais de 100 itens por semana e o que exige ação fica enterrado. **Correção:** não notificar por resposta no modo diário; disparar um único aviso no fechamento do período, com a contagem de dias preenchidos.
- **`JOBS-06` — o prescritor não tem contador de avisos.** O painel dele não consulta notificações e o menu não tem badge. O que fica invisível de verdade é "Consulta cancelada pelo paciente" e "Novo paciente vinculado", que não aparecem em nenhum cartão. **Correção:** `unreadNotifications` no DTO do painel (o método de contagem já existe) e badge no item "Avisos" dos dois perfis.
- **`JOBS-01` — lembrete de consulta some se o job não rodar às 8h.** A janela é exatamente "amanhã" e o `@Scheduled` não reexecuta disparo perdido: um deploy ou uma queda nesse minuto apaga os lembretes daquele dia. **Correção:** gravar a data do último ciclo concluído e, num `ApplicationReadyEvent`, rodar o ciclo se o dia ainda não foi processado. Não abrir a janela até agora — isso contraria teste existente.
- **`JOBS-10` — o job envia e-mail dentro da transação.** A rede fica dentro da transação de escrita e o `reminderSentAt` é gravado depois; se a transação falhar, os e-mails já saíram. **Correção:** dentro da transação, apenas notificação e marcação; enviar depois do commit, via `afterCommit` ou devolvendo a lista para o agendador.
- **`JOBS-11` — desativar prescritor não encerra o acompanhamento.** O job continua designando escalas aos pacientes de um prescritor que não consegue mais entrar. **Correção:** ao desativar, encerrar o protocolo ativo dos pacientes dele, e pular protocolo de prescritor inativo na designação. (A parte de paciente arquivado desse achado **não** é defeito — ver a seção final.)
- **`JOBS-07` — o CSV de escalas exporta código cru.** A célula recebe `true`, `2` e `450` onde a tela mostra "Sim", o rótulo da opção e "7h30". O arquivo só é interpretável com um livro de códigos que não existe. **Correção:** função `answerText` no `ExportService` espelhando o formatador do front, mantendo o código cru numa coluna extra; ajustar a asserção do `ExportTest`.
- **`JOBS-08` — o modo anônimo leva o nome do prescritor.** Só o campo do paciente é trocado; a coluna "Prescritor" continua preenchida, e a série de evolução não tem modo anônimo nenhum na tela. **Correção:** anonimizar também o prescritor no CSV de consultas e acrescentar `anonymous` ao link da tela de progresso.
- **`JOBS-09` — a impressão carimba a data em UTC.** A tela usa `toISOString()` e o formatador interpreta como horário local: toda impressão sai 3 horas adiantada, e a partir das 21h sai com a data do dia seguinte. O projeto já tem o utilitário correto e a tela não o usa. **Correção:** usar `toLocaleString("pt-BR")` no cabeçalho e `toIsoDate` no filtro de período.
- **`ERRO-01` — download de CSV entrega o JSON de erro dentro do arquivo.** O download é navegação pura e não passa pelo cliente de API: sessão expirada salva um `appointments.csv` com o JSON do 401 dentro, sem mensagem nem redirecionamento. É o caminho mais provável de reclamação no piloto, porque a tela de histórico fica aberta por horas. **Correção:** expor `download(path)` no cliente de API (com o mesmo tratamento de 401) e trocar os dois `<a href download>` por ele.

### Contas, convite e acesso

- **`AUTZ-04` — o cadastro público conta qual campo já tem conta.** `POST /auth/register` é liberado e devolve 409 dizendo se o duplicado foi o e-mail ou o CPF; a tentativa que falha não gasta o convite. Quem tiver um link válido (7 dias) pode repetir o cadastro variando o CPF e descobrir quem é paciente da clínica — e, num sistema de acompanhamento de óleo de cannabis, saber que um CPF está cadastrado já é informação de saúde. A rota de recuperação de senha foi feita justamente para não contar isso. **Correção:** uma checagem só, com mensagem sem nome de campo, e limite por token de convite e por endereço reusando o limitador de tentativas do login. Conferir antes como o formulário do front destaca o campo, para o cadastro não ficar mudo.
- **`AUTZ-05` — convite não pode ser listado nem cancelado.** Existem apenas `POST /invites` e `GET /invites/{token}`. Se o link for para o número errado ou cair num grupo, quem abrir primeiro cria uma conta real, vinculada àquele prescritor, e o paciente certo fica sem convite, porque o link é de uso único. O prescritor só descobre pelo aviso "Novo paciente vinculado", depois do fato. **Correção:** `GET /invites` do prescritor e `DELETE /invites/{id}`, com coluna própria de cancelamento em vez de reaproveitar `usedAt`, que ficaria ambíguo na auditoria.
- **`CONTRATO-03` — administrador não tem tela de perfil.** A API libera `/profile` para o ADMIN, mas o front põe `/perfil` sob `requireRole("PATIENT", "PRESCRIBER")` e o menu do administrador só tem Prescritores e Auditoria. A conta entra no piloto com a senha do `ADMIN_PASSWORD` e não tem onde trocá-la; o único contorno é o "esqueci minha senha", que depende de SMTP. **Correção:** incluir ADMIN no grupo de rotas do `/perfil` e no menu. A tela aguenta o perfil — o DTO monta para qualquer usuário e a validação de data de nascimento e endereço só se aplica a paciente.

### Banco e desempenho

- **`BANCO-01` — sem unique no dia da escala, mas o código lê com `Optional`.** Duas respostas simultâneas do mesmo dia gravam duas linhas, e a partir daí toda leitura daquele dia vira 500 permanente, sem caminho de limpeza pela API. **Correção:** capturar `DataIntegrityViolationException` no serviço e refazer a busca; o índice único precisa ser **parcial** (ignorando anuladas e o MEEM), o que exige a suíte no Postgres — o job de CI novo já permite isso.
- **`BANCO-02` — a RN08 só existe em Java.** Ler-então-escrever sem trava: dois pedidos no mesmo horário, ou duplo clique em "marcar", gravam as duas consultas. **Correção:** serializar por prescritor dentro da transação (um `findById` com lock pessimista antes da checagem), que funciona igual em H2 e Postgres; índice único parcial como reforço depois.
- **`BANCO-05` — o histórico monta DTO fora de transação.** Três serviços devolvem entidade crua sem `@Transactional`, e o DTO toca relações lazy: ~60 selects extras num paciente com 10 prescrições, e 500 se alguém desligar o `open-in-view`. Nenhuma das três listas tem paginação. **Correção:** `@Transactional(readOnly = true)` nos três, paginação nas rotas e `@BatchSize` ou consultas separadas para as coleções — `@EntityGraph` com duas listas dá `MultipleBagFetchException`.
- **`BANCO-06` — o painel carrega todas as escalas vencidas e conta uma a uma antes de cortar em 5.** Ajuste de desempenho para antes de crescer, não trava a entrega.

### Operação

- **`OPS-03` — sem `ADMIN_EMAIL`/`ADMIN_PASSWORD` o sistema sobe saudável e sem nenhuma conta.** O criador do administrador retorna em silêncio, sem log; o README dá a entender que só senha do banco e segredo do JWT são obrigatórios. A pessoa vê três containers verdes e não consegue entrar com ninguém. **Correção:** `log.warn` quando não houver nenhum usuário no banco, dizendo o que preencher, e uma linha no README.
- **`OPS-07` — o nginx resolve o endereço da api uma vez só.** Com `proxy_pass http://api:8080` sem `resolver`, recriar o container da api (um `up -d --build` para subir correção) deixa tudo em 502 até reiniciar o front. **Correção:** `resolver 127.0.0.11 valid=10s;` com a variável no `proxy_pass`, e `depends_on: api: {condition: service_healthy, restart: true}` no compose.
- **`OPS-08` — nada nunca subiu a pilha do docker.** O CI só monta as imagens; o plano de QA testa com H2 e `npm run dev`. Tudo que só existe no caminho do docker está sem verificação — e é por isso que `OPS-03`, `OPS-04` e `OPS-07` sobreviveram até aqui. **Correção:** job de CI que suba o compose com um `.env` de teste, espere o healthcheck, faça login e **um POST autenticado** (é o único jeito de pegar o `OPS-04`).
- **`OPS-10` — `.env.example` entrega `SESSION_SECURE_COOKIE=false`.** O valor explícito vence o padrão seguro do compose. Deve ser corrigido junto com o TLS, nunca antes.
- **`OPS-11` — o nginx serve JS e CSS sem compressão.** A tela de login baixa ~364 KB crus que cairiam para ~110 KB com gzip, num sistema cuja prioridade declarada é o celular. **Correção:** bloco `gzip` no `server`, incluindo `text/javascript` e `application/javascript`. Ganho maior e ortogonal: o chunk da tela de progresso tem 380 KB e provavelmente é a biblioteca de gráficos inteira — separar em import dinâmico.

---

## Baixos

| Código | O que acontece | Correção |
| :--- | :--- | :--- |
| `AUTZ-06` | `/dashboard/patient/{id}` entrega ao prescritor a caixa de avisos pessoal do paciente | Preencher `latestNotifications` só quando quem chama é o próprio paciente |
| `AUTZ-07` | Aviso de outro responde 403 e inexistente responde 404, o que permite descobrir ids válidos | Responder 404 nos dois casos |
| `AUTZ-08` | Sessão emitida no mesmo segundo da troca de senha não cai | Gravar `passwordChangedAt` em milissegundos, como já é feito na saída da conta |
| `BANCO-03` / `ERRO-04` | Item de protocolo sem validação: `items:[{}]` derruba a API com 500 | `@NotNull` em `scaleType` e `periodicity` no `ProtocolItemDTO` |
| `BANCO-04` / `ERRO-02` | `durationDays` aceita 0 e negativo; sem check de período no banco | `@Min(7)`/`@Max(365)` e `check (end_date >= start_date)` na próxima migração |
| `BANCO-07` | Os checks de tipo de escala divergem do enum e um do outro | Alinhar os dois checks e documentar que a tarefa recusa MEEM de propósito (RN09) |
| `BANCO-08` | FKs de anulação, arquivamento e revisão ficaram sem índice | Índices numa migração nova; o de `scale_response(appointment_id)` é o único que muda tempo hoje |
| `BANCO-09` | `Appointment.prescriptions` com `cascade ALL` + `orphanRemoval` e setter público da lista | Trocar para `{PERSIST, MERGE}`, `orphanRemoval = false`, e apagar o setter e o construtor mortos |
| `CLIN-05` | O gráfico da dor perde as faixas leve/moderada/intensa | Declarar o atributo como escore, não como item: o escore já é a intensidade |
| `CLIN-09` | O paciente abre o formulário do MEEM e só descobre no salvar que não pode responder | Não montar o formulário quando `filledByPatient` for falso, e filtrar o MEEM nos **dois** endpoints de definição |
| `CLIN-10` | O acompanhamento dura 91 dias e a primeira escala só sai no dia seguinte | `endDate = início + duração - 1` e designar a primeira rodada na criação |
| `CLIN-11` | Nome social (RN15) não existe, embora o formulário do PSQI da clínica peça | Campo `socialName` em `Patient` com `displayName()` nas telas, no CSV e na impressão |
| `CONTRATO-02` | Corrigir escala de período mudando a data cria uma segunda resposta | Usar o PUT (que precisa aceitar `periodStart`/`periodEnd`) ou apagar a resposta antiga ao mover o período |
| `CONTRATO-05` | Três endpoints sem nenhum chamador (`GET /scales/definitions`, `GET /prescriptions/{id}`, `PUT /scales/responses/{id}`) | Decidir por rota: usar ou remover — o PUT grava dado clínico e não pode ficar publicado sem dono |
| `CONTRATO-06` | `observation` da prescrição existe no DTO e no banco, e nenhuma tela usa | Colocar o campo na tela e no CSV, ou remover dos três lugares |
| `CONTRATO-07` | Histórico e impressão contam pedido recusado e consulta cancelada como "Consultas" | Filtrar por situação além da data, e acrescentar o corte superior de data na impressão |
| `ERRO-03` | Texto maior que o `varchar(255)` vira 409 "Este registro já existe" | `@Size(max = 255)` em `dosage`, `brand` e `batch`, com `maxLength` espelhado no formulário |
| `ERRO-05` | Content-Type errado vira 500 em vez de 415 | Fazer o `ErrorHandler` estender `ResponseEntityExceptionHandler` em vez de tratar exceção por exceção |
| `ERRO-07` | `GET /appointments` com só um lado do intervalo devolve a agenda inteira | Recusar com mensagem quando vier só uma das duas datas |
| `ERRO-08` | 404 expõe id interno e texto técnico na tela | Mensagem genérica, preservando as que já foram escritas para o usuário final (convite e escala) |
| `ERRO-09` | Lista de horários aceita elemento nulo (500); lista vazia apaga a semana com 200 | `@NotNull` no elemento e aviso na tela sobre zerar a agenda |
| `ERRO-10` | Degrau de escalonamento entra sem semana e sem dosagem | `@NotNull` + faixa em `week`, `@NotBlank` + `@Size` em `dosage` |
| `ERRO-11` | Correção de escala responde 201 Created como se fosse nova | Devolver 200 quando a resposta do período já existia |
| `JOBS-05` / `NOOP-10` | Uma falha no começo do job cancela os lembretes do dia inteiro | `try/catch` por etapa no agendador, com log nomeando o passo |
| `JOBS-12` | O e-mail de lembrete diz por extenso qual escala clínica o paciente precisa responder | Manter o nome só na notificação interna; no e-mail, "entre no sistema para ver qual" |
| `LACUNA-06` | Escala de resposta única enviada avulsa aparece como diário de 7 dias | Levar o modo de preenchimento até o DTO da tarefa e usá-lo na tela |
| `NOOP-02` | Arquivar não fecha as escalas pendentes | Fechar as PENDENTE como não respondidas ao arquivar (ver ressalva na última seção) |
| `NOOP-11` | O motivo da recusa do pedido nunca é gravado | Confirmar com a clínica se vira dado de prontuário; se sim, coluna nova e campo no DTO |
| `NOOP-12` | Métodos públicos sem nenhum chamador | Apagar quatro deles; manter `findByAppointmentId...`, que é a consulta que `NOOP-06` e `NOOP-08` vão precisar |
| `OPS-09` | Logs dos containers crescem sem limite; o `web` está sem fuso | `logging` com `max-size`/`max-file` nos três serviços e `TZ` no `web` |
| `OPS-12` | Fontes, `boot.css` e imagens ficam fora da regra de cache | Bloco por extensão, e trocar `location /assets/` por `location ^~ /assets/` para o regex não roubar a regra |

---

## O que **não** é defeito

Itens levantados na auditoria e descartados depois de reler o código — registrados aqui para ninguém "corrigir" o que está certo:

- **Escala incompleta ficar sem escore** é decisão registrada (`docs/requisitos-v2.md:974`) e coberta por teste. O defeito está no que acontece depois (`CLIN-01`), não na regra.
- **Paciente arquivado continuar recebendo escala enviada na mão** é intencional e está documentado no próprio serviço de arquivamento: o acompanhamento automático acaba, o envio manual continua. Por isso `NOOP-02` deve fechar só as tarefas pendentes, sem filtrar lembrete por arquivamento.
- **Texto clínico sem limite de tamanho** foi reportado e refutado: a coluna é `text` e o comportamento está correto.
- **`AUTZ-02`** não deixa o prontuário inacessível "por qualquer conta": o próprio paciente continua lendo, e reativar o prescritor devolve o acesso na hora.

---

## Como esta auditoria foi feita

Oito frentes independentes leram o código em paralelo — contrato entre front e API, ações quebradas e no-ops, autorização, banco e migrações, regras clínicas contra o documento de requisitos, validação e erros, jobs/e-mail/exportação, e prontidão operacional. Cada achado passou por um verificador adversarial, com a instrução de derrubá-lo relendo o código: 1 foi refutado, 13 tiveram a severidade ou o enunciado corrigidos, e um crítico de completude percorreu os fluxos de ponta a ponta procurando o que as oito frentes não tocaram (foi de onde saíram os seis `LACUNA`). Os dois bloqueadores e cinco dos altos foram conferidos manualmente arquivo por arquivo antes de entrar neste documento.

**O que não foi coberto:** nada foi executado — nenhuma subida do compose, nenhum teste de carga, nenhuma navegação real além da conferência do filtro de origem. Acessibilidade, responsividade e comportamento de navegador antigo ficaram de fora (são os cartões de QA #28 a #36). O conteúdo clínico das escalas foi conferido contra o Anexo A do documento de requisitos, não contra os PDFs originais dos instrumentos.

## Relação com os cartões abertos

- **#17, #18 e #19 (deploy e backup):** `OPS-01`, `OPS-04`, `OPS-05`, `OPS-08`, `OPS-09`, `OPS-10`.
- **#25 (montar respostas dentro dos serviços):** `BANCO-05`.
- **#26 e #27 (clínica):** `OPS-02` (texto do termo), `NOOP-04` e `NOOP-11` (decisões que dependem da prescritora), `CLIN-11` (nome social).
- **#28 a #36 (QA):** os médios de escala e agenda são bons casos de teste para ela; vale acrescentar ao plano o preenchimento parcial de escala (`CLIN-01`) e a resposta de escala de período (`NOOP-01`).
- **Sem cartão ainda:** todos os altos de segurança e primeiro acesso (`AUTZ-01`, `LACUNA-02`, `OPS-06`).
