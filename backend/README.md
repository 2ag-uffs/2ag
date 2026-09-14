# backend do 2ag

api em java que guarda os dados clínicos e aplica as regras de acesso. o que o sistema precisa fazer está em [`docs/requisitos-v2.md`](../docs/requisitos-v2.md).

## tecnologias

- java 17 e spring boot 3.5
- spring data jpa com hibernate, e **flyway** para o esquema do banco
- spring security com sessão em cookie `httpOnly` assinada com jwt (jjwt 0.11.5)
- postgresql em produção e h2 em memória nos testes
- maven, pelo wrapper `mvnw` que já vem no repositório

## rodando

```bash
cd backend/doisag
export DATABASE_PASSWORD='a_senha_do_banco'
export JWT_SECRET="$(openssl rand -base64 48)"
export SESSION_SECURE_COOKIE=false
export SEED_DADOS_TESTE=true
./mvnw spring-boot:run
```

a api responde em `http://localhost:8080/api`. toda rota fica embaixo de `/api` para o front e a api dividirem a mesma origem, sem cors.

para mexer só nas telas, sem instalar postgres, a api sobe com o h2 em memória dos testes. os dados somem quando ela para:

```bash
./mvnw spring-boot:test-run "-Dspring-boot.run.arguments=--spring.profiles.active=test --api.seed.enabled=true --api.session.secure-cookie=false"
```

## configuração

tudo que muda entre ambientes vem de variável de ambiente:

| variável | para que serve | padrão |
| :--- | :--- | :--- |
| `DATABASE_URL` | endereço jdbc do banco | `jdbc:postgresql://localhost:5432/doisag` |
| `DATABASE_USER` | usuário do banco | `admindoisag` |
| `DATABASE_PASSWORD` | senha do banco | **obrigatória** |
| `JWT_SECRET` | chave que assina a sessão, com pelo menos 32 caracteres | **obrigatória** |
| `SESSION_DURATION_MINUTES` | minutos que a sessão dura sem uso | `120` |
| `SESSION_SECURE_COOKIE` | cookie da sessão só trafega em https | `true` |
| `ADMIN_EMAIL` e `ADMIN_PASSWORD` | conta administrativa criada na primeira subida | nenhuma conta |
| `SEED_DADOS_TESTE` | cria as contas de teste | `false` |
| `JPA_SHOW_SQL` | mostra o sql no console | `false` |
| `SERVER_PORT` | porta da api | `8080` |
| `PUBLIC_URL` | endereço onde as pessoas abrem o sistema, usado nos links enviados por e-mail | `http://localhost:5173` |
| `MAIL_HOST` | servidor smtp. sem ele os e-mails aparecem só no log da api | vazio |
| `MAIL_PORT` | porta do servidor smtp | `587` |
| `MAIL_USERNAME` e `MAIL_PASSWORD` | conta que autentica no servidor smtp | vazio |
| `MAIL_FROM` | remetente dos e-mails | o `MAIL_USERNAME` |
| `MAIL_SMTP_AUTH` e `MAIL_SMTP_STARTTLS` | autenticação e tls do smtp. um servidor local de teste costuma pedir `false` nos dois | `true` |

a api trabalha sempre no fuso `America/Sao_Paulo`, independente da máquina onde roda.

## sessão e perfis

| rota | o que faz |
| :--- | :--- |
| `POST /auth/login` | confere e-mail e senha, grava o cookie `session` e devolve `{id, name, role}` |
| `POST /auth/logout` | apaga o cookie |
| `GET /auth/me` | quem está logado |
| `POST /auth/register` | cadastro do paciente pelo link de convite, já entrando logado |

- o cookie é `httpOnly` e `SameSite=Strict`: o javascript não lê o token e outro site não consegue usar a sessão
- a sessão é renovada enquanto a pessoa usa o sistema, então ninguém é derrubado no meio de um formulário
- depois de 5 senhas erradas seguidas, o login fica bloqueado por 15 minutos
- conta desativada perde o acesso na requisição seguinte
- testes e ferramentas podem mandar o mesmo token no cabeçalho `Authorization: Bearer`

os perfis são `PATIENT`, `PRESCRIBER` e `ADMIN`. a autorização tem duas camadas:

- o `SecurityConfigurations` barra pelo perfil antes de chegar no controller: o administrador só entra em `/admin/**` e nos dados da própria conta, e as outras rotas são de paciente e prescritor
- cada rota declara no `@PreAuthorize` os perfis que podem usá-la e, quando o dado é de um paciente, confere o vínculo no `PatientAccessService`: o paciente só vê os próprios dados e o prescritor só vê os pacientes vinculados a ele

o `RouteRolesTest` passa por todas as rotas da api e falha se alguma não declarar perfil. o `PatientLinkTest` cobre as regras de vínculo, inclusive trocando o id na url.

## administração

| rota | o que faz |
| :--- | :--- |
| `GET /admin/prescribers` | lista os prescritores |
| `POST /admin/prescribers` | cria conta de prescritor |
| `PUT /admin/prescribers/{id}/active` | ativa ou desativa a conta, sem apagar nada |

o administrador não acessa nenhum dado clínico.

## perfil

| rota | o que faz |
| :--- | :--- |
| `GET /profile` | dados da própria conta |
| `PUT /profile` | nome, data de nascimento, telefone e endereço |
| `PUT /profile/email` | troca o e-mail de acesso pedindo a senha atual |
| `PUT /profile/email-preference` | liga ou desliga os avisos por e-mail |
| `PUT /profile/password` | troca a senha pedindo a atual. as outras sessões caem e este aparelho continua logado |

as rotas de perfil usam sempre a conta da sessão, então ninguém altera o perfil de outra pessoa.

## recuperação de senha

| rota | o que faz |
| :--- | :--- |
| `POST /auth/password-reset/request` | manda o link de senha nova para o e-mail, se ele for de uma conta ativa. a resposta é sempre a mesma |
| `POST /auth/password-reset/confirm` | grava a senha nova. o link vale uma vez, por 30 minutos, e as sessões abertas caem |

cada conta recebe no máximo 3 links por hora e um link novo cancela os anteriores. o e-mail sai pelo servidor configurado em `MAIL_HOST`. sem ele, a mensagem com o link aparece só no log da api.

## convite de paciente

| rota | o que faz |
| :--- | :--- |
| `POST /invites` | o prescritor gera um link de convite de uso único, válido por 7 dias |
| `GET /invites/{token}` | rota pública que a tela de cadastro usa para conferir o convite |

o banco guarda só o hash do código que vai no link.

## termo de consentimento

| rota | o que faz |
| :--- | :--- |
| `GET /consent-term` | rota pública com a versão e o texto do termo que o cadastro pede |

o texto fica em `src/main/resources/consent/termo-de-consentimento.txt` e ainda é um rascunho que a clínica precisa aprovar. quando o texto mudar, troque também `ConsentTermService.CURRENT_VERSION`: cada aceite fica guardado com a versão e a data.

## histórico clínico

| rota | o que faz |
| :--- | :--- |
| `GET /pacientes/{patientId}/anamneses` | resumo das anamneses do paciente, da mais recente para a mais antiga |
| `GET /pacientes/{patientId}/consultas` | consultas do paciente |
| `GET /pacientes/{patientId}/prescricoes` | prescrições do paciente, com a data da consulta que gerou cada uma |

nenhuma rota lista registros do sistema inteiro: toda lista sai filtrada pelo paciente ou pelo prescritor logado.

## agenda

| rota | o que faz |
| :--- | :--- |
| `GET /agenda/disponibilidade` | horários de atendimento da semana e duração padrão das consultas do prescritor logado |
| `PUT /agenda/disponibilidade` | troca os horários de atendimento e a duração padrão, de 15 a 240 minutos |
| `GET /consulta?inicio=&fim=` | agenda do prescritor logado entre as duas datas. sem as datas, vem a agenda inteira |
| `GET /consulta/pedidos` | pedidos esperando a resposta do prescritor logado |
| `POST /consulta` | o prescritor marca consulta, já confirmada, para um paciente dele. sem `durationMinutes`, vale a duração padrão |
| `GET /consulta/{id}` | uma consulta com os campos clínicos, para o prescritor do paciente |
| `PUT /consulta/{id}` | o prescritor remarca o pedido ou a consulta, que fica confirmada no horário novo |
| `PUT /consulta/{id}/confirmacao` | o prescritor confirma o pedido |
| `PUT /consulta/{id}/recusa` | o prescritor recusa o pedido. o motivo é opcional e vai no aviso para o paciente |
| `PUT /consulta/{id}/cancelar` | o paciente ou o prescritor cancela o pedido ou a consulta |
| `GET /consulta/horarios-livres?inicio=&fim=` | horários livres na agenda do prescritor do paciente logado, em até 31 dias |
| `POST /consulta/agendamento` | o paciente pede um horário livre, com modalidade e motivo opcional |
| `GET /consulta/minhas` | próximos pedidos e consultas do paciente logado, com a situação de cada um |

- `inicio` e `fim` são datas no formato `aaaa-mm-dd`, as duas inclusive
- os horários livres saem da divisão dos períodos de atendimento pela duração padrão, sem os que já passaram e sem os que se sobrepõem a um pedido ou consulta
- o pedido do paciente segura o horário até a resposta. a recusa e o cancelamento liberam o horário
- o paciente cancela o pedido a qualquer hora e a consulta marcada até 24 horas antes. depois disso, só o prescritor cancela
- a rota do paciente não aceita campo clínico. o motivo que ele escreve fica em `patientNote`
- cada mudança gera uma notificação para a outra parte
- só consulta confirmada recebe registro clínico (`PUT /consulta/{id}/registro-clinico`) e prescrição (`POST /consulta/{id}/prescricao`)

## escalas

as respostas de todas as escalas caem numa tabela só. o formulário de cada uma — itens, âncoras, faixas e direção — fica descrito no código, em `scale/ScaleCatalog`, e o cálculo de cada instrumento em `scale/ScaleScorer`.

| rota | o que faz |
| :--- | :--- |
| `GET /escalas/definicoes` | o formulário de todas as escalas, que a tela genérica usa para desenhar os campos |
| `GET /escalas/definicoes/{slug}` | o formulário de uma escala |
| `GET /escalas/designaveis` | as escalas que o prescritor pode enviar ao paciente |
| `POST /escalas/{slug}/respostas` | o paciente responde. no diário, responder de novo o mesmo dia corrige aquele dia |
| `GET /escalas/{slug}/respostas` | as respostas do paciente logado naquela escala, que a grade da semana usa |
| `GET /escalas/respostas/{id}` | uma resposta com o escore, a faixa e o valor de cada item |
| `PUT /escalas/respostas/{id}` | corrige a resposta |
| `PUT /escalas/respostas/{id}/analise` | o prescritor marca que já conferiu, e o paciente para de editar |
| `PUT /escalas/respostas/{id}/anulacao` | anula a resposta com motivo |
| `POST /escalas/mini-exame/consulta/{appointmentId}` | o prescritor aplica o MEEM dentro da consulta |
| `POST /pacientes/{patientId}/escalas` | envia uma escala avulsa ao paciente |
| `GET /pacientes/{patientId}/escalas` | as tarefas de escala do paciente |
| `GET /pacientes/{patientId}/escalas/central` | o que espera resposta e o que já foi respondido |
| `GET /pacientes/{patientId}/escalas/respostas` | as escalas respondidas, para o histórico |

- cada tarefa vale por um período. o job diário fecha a que passou do prazo: com ao menos uma resposta ela conta como respondida, e sem nenhuma fica como não respondida, que no gráfico é lacuna e nunca zero (RN10)
- a ficha de acompanhamento e o diário do sono são um registro por dia, apresentados como a grade da semana do papel
- item em branco não é gravado, e escala validada sem todos os itens não tem escore
- o escore de escala validada sai do algoritmo oficial do instrumento e vem sempre com a faixa (RN13 e RN14)
- o MEEM é de heteroaplicação: só o prescritor aplica, dentro de consulta confirmada, e ele nunca vira tarefa do paciente (RN09)

## guarda do prontuário

o prontuário tem guarda mínima de 20 anos (Lei 13.787/2018), então nenhuma rota apaga dado clínico. encerrar o acompanhamento de 90 dias é `PUT /pacientes/{patientId}/acompanhamento/encerrar` e só marca o protocolo como inativo. a única rota `DELETE` da api é a de notificação, que pertence à própria conta.

## trilha de auditoria

| rota | o que faz |
| :--- | :--- |
| `GET /patients/{patientId}/audit-events?from=&to=&page=` | trilha de um paciente, para o prescritor dele |
| `GET /admin/audit-events?from=&to=&page=` | ações de prescritores e do sistema, para o administrador, sem nome de paciente |

- `from` e `to` são datas no formato `aaaa-mm-dd`, as duas inclusive. cada página traz 30 eventos, do mais recente para o mais antigo
- os serviços gravam quem criou ou alterou consulta, prescrição, anamnese, escala, designação e acompanhamento, na mesma transação da mudança
- abrir o prontuário também entra na trilha quando quem abre é o prescritor, uma linha por paciente a cada 30 minutos
- o que o job diário faz aparece com autor `Sistema`
- a tabela `audit_event` só recebe inserção: o repositório não tem método de alterar nem de apagar, e nenhuma rota escreve nela

## erros

toda resposta de erro tem `timestamp`, `status`, `error`, `message` e `path`. erro de validação traz também `errors`, com a mensagem de cada campo.

| status | quando |
| :--- | :--- |
| `400` | dado inválido, parâmetro faltando ou regra de negócio violada |
| `401` | sem sessão, sessão vencida ou senha errada |
| `403` | logado, mas sem permissão para aquele dado, ou conta desativada tentando entrar |
| `404` | registro ou rota que não existe |
| `405` | método http que a rota não aceita |
| `409` | e-mail, CPF ou registro que já pertence a outra conta, com o campo em `errors` |
| `429` | login bloqueado por tentativas erradas |
| `500` | erro inesperado, registrado no log |

`GET /health` responde se a api e o banco estão de pé. é o que o docker usa.

## banco

o esquema vem das migrações do flyway em `src/main/resources/db/migration/`. o hibernate só confere se as entidades batem com o banco, nunca altera nada.

- `V1__esquema_base.sql` consolida as migrações antigas numa base limpa, com restrições de nulidade e índices
- toda mudança de esquema é uma migração nova. nunca edite uma migração que já rodou em algum banco

## testes

```bash
./mvnw clean install
```

os testes rodam no perfil `test`, com h2 em memória, então não precisam de postgres nem de variável de ambiente. todo teste que sobe o contexto do spring precisa de `@ActiveProfiles("test")`.

testes de fluxo que envolvem job agendado ou serialização de resposta devem rodar sem `@Transactional`: a transação do teste esconde carregamento sob demanda que falharia em uso real.

## padrão de código

vale para todo código novo ou reescrito:

- dto de entrada e saída em toda rota, nunca a entidade
- regra de negócio no serviço, com `@Transactional`
- nomes em inglês, em camelCase e descritivos
- comentários curtos, em minúsculas, sem acento e sem pontuação

## situação dos módulos

a fundação (configuração, erros, sessão, administração e migração base), o módulo de acesso e identidade (login, convite, cadastro, termo de consentimento, perfil e recuperação de senha), o de autorização (perfil e vínculo em toda rota, fim da exclusão de dado clínico e trilha de auditoria), o de atendimento (consulta, prescrição, anamnese, histórico e arquivamento de paciente), o de agenda (horários de atendimento, pedido do paciente e agenda do prescritor) e o de escalas (motor único, tarefas com prazo e acompanhamento automático de 90 dias) já seguem o padrão novo. as telas de evolução, o painel inicial e as notificações ainda são os de 2025 e estão sendo reescritos na ordem do §8.6 do documento de requisitos. até o último deles ser reescrito, o `open-in-view` continua ligado.
