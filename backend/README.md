# backend do 2ag

api em java que guarda os dados clínicos e aplica as regras de acesso. o que o sistema precisa fazer está em [`docs/requisitos-v2.md`](../docs/requisitos-v2.md).

## tecnologias

- java 17 e spring boot 4.1
- spring data jpa com hibernate, e **flyway** para o esquema do banco
- spring security com sessão em cookie `httpOnly` assinada com jwt (jjwt 0.13)
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
| `SESSION_MAX_HOURS` | horas que uma sessão dura no máximo desde o login, mesmo sendo renovada | `12` |
| `LOGIN_MAX_FAILURES` | senhas erradas do mesmo e-mail a partir do mesmo endereço antes do bloqueio | `5` |
| `LOGIN_MAX_FAILURES_PER_ADDRESS` | senhas erradas de um endereço, somando todos os e-mails, antes do bloqueio | `20` |
| `LOGIN_MAX_FAILURES_PER_EMAIL` | senhas erradas de um e-mail, somando todos os endereços, antes do bloqueio. teto alto, só para frear ataque distribuído | `100` |
| `LOGIN_BLOCK_MINUTES` | minutos que o bloqueio do login dura | `15` |
| `PASSWORD_RESET_MAX_REQUESTS_PER_ADDRESS` | pedidos de link de senha nova do mesmo endereço antes do bloqueio, que dura o mesmo tempo do login | `10` |
| `SESSION_SECURE_COOKIE` | cookie da sessão só trafega em https | `true` |
| `ADMIN_EMAIL` e `ADMIN_PASSWORD` | conta administrativa criada na primeira subida | nenhuma conta |
| `SEED_DADOS_TESTE` | cria as contas de teste | `false` |
| `JPA_SHOW_SQL` | mostra o sql no console | `false` |
| `SERVER_PORT` | porta da api | `8080` |
| `PUBLIC_URL` | endereço onde as pessoas abrem o sistema, usado nos links enviados por e-mail e como único endereço aceito nas requisições com cookie que mudam dado | **obrigatória** |
| `MAIL_HOST` | servidor smtp. sem ele o e-mail não sai e o log mostra só o destinatário e o assunto | vazio |
| `EMAIL_LOG_TEXT` | `true` mostra no log o texto inteiro do e-mail, com o link de senha nova. só para desenvolvimento | `false` |
| `MAIL_PORT` | porta do servidor smtp | `587` |
| `MAIL_USERNAME` e `MAIL_PASSWORD` | conta que autentica no servidor smtp | vazio |
| `MAIL_FROM` | remetente dos e-mails. com `MAIL_HOST` preenchido e os dois vazios a api não sobe | o `MAIL_USERNAME` |
| `MAIL_SMTP_AUTH` e `MAIL_SMTP_STARTTLS` | autenticação e tls do smtp. um servidor local de teste costuma pedir `false` nos dois | `true` |

a api trabalha sempre no fuso `America/Sao_Paulo`, independente da máquina onde roda.

## sessão e perfis

| rota | o que faz |
| :--- | :--- |
| `POST /auth/login` | confere e-mail e senha, grava o cookie `session` e devolve `{id, name, role}` |
| `POST /auth/logout` | apaga o cookie e encerra as sessões abertas da pessoa em qualquer aparelho |
| `GET /auth/me` | quem está logado |
| `POST /auth/register` | cadastro do paciente pelo link de convite, já entrando logado |

- o cookie é `httpOnly` e `SameSite=Strict`: o javascript não lê o token e outro site não consegue usar a sessão
- além disso, requisição que muda dado (`POST`, `PUT`, `PATCH` e `DELETE`) com o cookie da sessão só passa se o cabeçalho `Origin`, ou o `Referer` quando ele não vem, for o endereço do `PUBLIC_URL`. vinda de outro endereço recebe `403`. isso cobre navegador antigo e subdomínio vizinho, que o `SameSite` não pega, e por isso o sistema precisa ser aberto pelo mesmo endereço do `PUBLIC_URL`: em desenvolvimento, `localhost` e não `127.0.0.1`
- a sessão é renovada enquanto a pessoa usa o sistema, então ninguém é derrubado no meio de um formulário, mas ela termina 12 horas depois do login
- sair da conta encerra as sessões abertas daquela pessoa em qualquer aparelho, mesmo que alguém tenha guardado o cookie
- depois de 5 senhas erradas do mesmo e-mail a partir do mesmo endereço, as tentativas desse e-mail vindas dali ficam bloqueadas por 15 minutos, mesmo com a senha certa. 20 erros de um endereço, somando e-mails diferentes, bloqueiam o endereço inteiro
- o bloqueio não fica na conta: quem erra a senha de outra pessoa não trava o login dela de outro lugar. a exceção é o ataque distribuído: 100 erros no mesmo e-mail, somando todos os endereços, bloqueiam aquele e-mail por 15 minutos em qualquer lugar. e-mail sem cadastro é bloqueado do mesmo jeito, então a resposta não revela quem tem conta, e o link de senha nova libera o e-mail na hora
- a mesma contagem vale para toda rota que confere senha: trocar a senha, trocar o e-mail e o link de senha nova gerado pelo administrador. sessão esquecida aberta não vira chute livre da senha atual
- trocar o e-mail para um que já tem conta responde `409`, e 5 dessas respostas bloqueiam a troca por 15 minutos, senão um paciente logado descobriria quem é da clínica
- o cadastro com e-mail ou CPF que já tem conta responde `400` com uma mensagem só, sem dizer qual dos dois, e o convite continua valendo para os dados certos. 5 tentativas no mesmo convite, ou 20 recusas do mesmo endereço, bloqueiam o cadastro por 15 minutos. a mensagem genérica sozinha não esconde tudo: uma tentativa com o CPF de alguém ainda diz se ele tem conta, e o que segura é o convite ser pessoal e a contagem
- a conta administrativa não troca de e-mail pelo perfil, porque o e-mail dela é o `ADMIN_EMAIL` do servidor: trocando por fora, a subida seguinte criaria outra conta com a senha inicial
- a senha vai de 8 a 64 caracteres e precisa caber em 72 bytes, que é o limite do bcrypt. letra com acento e emoji ocupam mais de um byte, e a senha que não cabe é recusada com mensagem no próprio campo
- a contagem fica na memória da api, que roda numa instância só, e zera quando ela reinicia. atrás do nginx o endereço de quem acessa vem do cabeçalho `X-Forwarded-For`, então um proxy a mais na frente precisa repassar esse cabeçalho, senão todo mundo aparece com o mesmo endereço
- conta desativada perde o acesso na requisição seguinte
- testes e ferramentas podem mandar o mesmo token no cabeçalho `Authorization: Bearer`, que não passa pela conferência de origem porque o navegador não manda esse cabeçalho sozinho

os perfis são `PATIENT`, `PRESCRIBER` e `ADMIN`. a autorização tem duas camadas:

- o `SecurityConfigurations` barra pelo perfil antes de chegar no controller: o administrador só entra em `/admin/**` e nos dados da própria conta, e as outras rotas são de paciente e prescritor
- cada rota declara no `@PreAuthorize` os perfis que podem usá-la e, quando o dado é de um paciente, confere o vínculo no `PatientAccessService`: o paciente só vê os próprios dados e o prescritor só vê os pacientes vinculados a ele

o `RouteRolesTest` passa por todas as rotas da api e falha se alguma não declarar perfil. o `PatientLinkTest` cobre as regras de vínculo, inclusive trocando o id na url.

## administração

| rota | o que faz |
| :--- | :--- |
| `GET /admin/prescribers` | lista os prescritores |
| `POST /admin/prescribers` | cria conta de prescritor, sem senha. o link de primeiro acesso só volta na resposta quando o e-mail não saiu |
| `PUT /admin/prescribers/{id}/active` | ativa ou desativa a conta, sem apagar nada |
| `POST /admin/prescribers/{id}/password-reset` | gera um link de senha nova para um prescritor. o link só volta na resposta quando o e-mail não saiu |
| `POST /admin/patients/lookup` | acha a conta de um paciente pelo e-mail inteiro, que vai no corpo. devolve só `{id, name, email, active}` |
| `GET /admin/patients/deactivated` | as contas de paciente desativadas, para dar para reativar depois |
| `PUT /admin/patients/{id}/active` | ativa ou desativa a conta de um paciente |

o administrador não acessa nenhum dado clínico.

o administrador não escolhe nem conhece a senha de ninguém. a conta de prescritor nasce com uma senha aleatória que ninguém vê, e quem cria a senha de verdade é o próprio prescritor, pelo link de primeiro acesso: uso único, 48 horas, o mesmo mecanismo do link de senha nova. com `MAIL_HOST` o link vai só para o e-mail da pessoa; sem ele, aparece na tela para o administrador entregar. se vencer, o **Senha nova** gera outro. a criação da conta fica na trilha como `CRIACAO` de `CONTA_DE_PRESCRITOR`.

desativar uma conta, de prescritor ou de paciente, tira o acesso na requisição seguinte e encerra as sessões abertas, então reativar depois não ressuscita a sessão antiga. as duas ações vão para a trilha (`DESATIVACAO` e `REATIVACAO`), e a de paciente aparece também na trilha do prontuário dele, para o prescritor ver. a administração não tem lista de pacientes: ela acha a conta pelo e-mail e só enxerga a lista das que desativou. desativar não é arquivar: arquivar é ato clínico do prescritor e não tira o acesso. a conta desativada para de receber e-mail de lembrete e escala nova do acompanhamento; os avisos dentro do sistema continuam sendo criados. a do paciente volta a receber as escalas quando for reativada, e desativar o prescritor encerra o acompanhamento dos pacientes dele.

o link de senha nova é a saída para o prescritor que esqueceu a senha antes de a clínica ter `MAIL_HOST` configurado: o administrador entrega o endereço para a pessoa pelo canal que ele confia. como a rota toma a conta de outra pessoa, ela pede a senha do próprio administrador no corpo (`adminPassword`), só funciona em conta ativa, reaproveita o link de uso único de 30 minutos e grava um evento `REDEFINICAO_DE_SENHA` na trilha de auditoria. quando o e-mail sai de verdade, o link vai só para o prescritor e a resposta vem com `resetLink` nulo, para o administrador não ficar com a conta de outra pessoa na mão. se o servidor de e-mail falhar, o link volta na resposta do mesmo jeito que sem `MAIL_HOST`, senão o prescritor ficaria trancado até alguém consertar o SMTP.

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

cada conta recebe no máximo 3 links por hora e um link novo cancela os anteriores. o pedido também conta por endereço, exista ou não a conta: depois de 10 pedidos em 15 minutos o endereço recebe `429`. o e-mail sai depois da resposta, em segundo plano, para a espera pelo servidor de e-mail não denunciar que a conta existe. sobra uma diferença de poucos milissegundos de banco entre os dois caminhos, que o limite por endereço torna difícil de medir. o e-mail sai pelo servidor configurado em `MAIL_HOST`. sem ele, o e-mail não sai: o log mostra só o destinatário e o assunto, e o texto com o link só aparece com `EMAIL_LOG_TEXT=true`, que a api local com banco em memória já liga.

## convite de paciente

| rota | o que faz |
| :--- | :--- |
| `POST /invites` | o prescritor gera um link de convite de uso único, válido por 7 dias |
| `GET /invites` | os convites do prescritor que ainda podem ser usados, sem o código |
| `PUT /invites/{id}/cancel` | cancela um convite que foi para a pessoa errada. convite já usado não cancela, porque a conta já existe |
| `POST /invites/lookup` | rota pública que a tela de cadastro usa para conferir o convite. o código vai no corpo (`token`), e não no endereço, para não ficar em log de servidor |

o banco guarda só o hash do código que vai no link, então a lista não mostra o link de novo. cancelar grava `cancelled_at` e não é `DELETE`: o convite continua no banco como registro do que foi enviado.

## termo de consentimento

| rota | o que faz |
| :--- | :--- |
| `GET /consent-term` | rota pública com a versão e o texto do termo que o cadastro pede |

o texto fica em `src/main/resources/consent/termo-de-consentimento.txt` e ainda é um rascunho que a clínica precisa aprovar. quando o texto mudar, troque também `ConsentTermService.CURRENT_VERSION`: cada aceite fica guardado com a versão e a data.

## histórico clínico

| rota | o que faz |
| :--- | :--- |
| `GET /patients/{patientId}/anamneses` | resumo das anamneses do paciente, da mais recente para a mais antiga |
| `GET /patients/{patientId}/appointments` | consultas do paciente |
| `GET /patients/{patientId}/prescriptions` | prescrições do paciente, com a data da consulta que gerou cada uma |

nenhuma rota lista registros do sistema inteiro: toda lista sai filtrada pelo paciente ou pelo prescritor logado.

## agenda

| rota | o que faz |
| :--- | :--- |
| `GET /availability` | horários de atendimento da semana e duração padrão das consultas do prescritor logado |
| `PUT /availability` | troca os horários de atendimento e a duração padrão, de 15 a 240 minutos |
| `GET /appointments?from=&to=` | agenda do prescritor logado entre as duas datas. sem as datas, vem a agenda inteira |
| `GET /appointments/requests` | pedidos esperando a resposta do prescritor logado |
| `POST /appointments` | o prescritor marca consulta, já confirmada, para um paciente dele. sem `durationMinutes`, vale a duração padrão |
| `GET /appointments/{id}` | uma consulta com os campos clínicos, para o prescritor do paciente |
| `PUT /appointments/{id}` | o prescritor remarca o pedido ou a consulta, que fica confirmada no horário novo |
| `PUT /appointments/{id}/confirm` | o prescritor confirma o pedido |
| `PUT /appointments/{id}/decline` | o prescritor recusa o pedido. o motivo é opcional e vai no aviso para o paciente |
| `PUT /appointments/{id}/cancel` | o paciente ou o prescritor cancela o pedido ou a consulta |
| `PUT /appointments/{id}/no-show` | o prescritor marca que o paciente não compareceu, depois que o horário passou |
| `GET /appointments/free-slots?from=&to=` | horários livres na agenda do prescritor do paciente logado, em até 31 dias |
| `POST /appointments/requests` | o paciente pede um horário livre, com modalidade e motivo opcional |
| `GET /appointments/mine` | próximos pedidos e consultas do paciente logado, com a situação de cada um |

- `inicio` e `fim` são datas no formato `aaaa-mm-dd`, as duas inclusive
- os horários livres saem da divisão dos períodos de atendimento pela duração padrão, sem os que já passaram e sem os que se sobrepõem a um pedido ou consulta
- o pedido do paciente segura o horário até a resposta. a recusa e o cancelamento liberam o horário
- o paciente tem no máximo 3 pedidos esperando resposta ao mesmo tempo, porque cada um segura um horário que some da agenda dos outros pacientes
- pedido que passou da data sem resposta é recusado pelo job diário, com aviso para o paciente, em vez de ficar aberto para sempre
- o paciente cancela o pedido a qualquer hora e a consulta marcada até 24 horas antes. depois disso, só o prescritor cancela
- a rota do paciente não aceita campo clínico. o motivo que ele escreve fica em `patientNote`
- cada mudança gera uma notificação para a outra parte
- a agenda de um prescritor recebe uma marcação de cada vez: marcar, pedir, confirmar e remarcar travam a linha do prescritor no banco até o fim da transação. dois pedidos do mesmo horário ao mesmo tempo não viram duas consultas: o segundo espera o primeiro gravar, lê a agenda já com a consulta nova e recebe horário ocupado (RN08)
- confirmar, recusar, remarcar, cancelar e marcar falta travam a própria consulta antes de ler ela, então o que o outro lado acabou de gravar (um cancelamento no meio da confirmação) é lido de novo em vez de ser sobrescrito
- só consulta confirmada e que já aconteceu recebe registro clínico (`PUT /appointments/{id}/clinical-record`), prescrição (`POST /appointments/{id}/prescriptions`) e Mini-Exame. os três usam a mesma folga de 5 minutos para o relógio
- a prescrição nova só vira a vigente quando a consulta dela não é mais antiga que a da vigente atual. receita lançada numa consulta anterior é gravada já como `SUBSTITUIDA`, só para o histórico, e a resposta vem com `current: false`
- a falta só vale para consulta que estava `AGENDADA` e cujo horário já terminou. ela libera o horário e avisa o paciente. registrar o atendimento depois desfaz a falta e conclui a consulta, para o erro de clique não trancar o prontuário

## escalas

as respostas de todas as escalas caem numa tabela só. o formulário de cada uma — itens, âncoras, faixas e direção — fica descrito no código, em `scale/ScaleCatalog`, e o cálculo de cada instrumento em `scale/ScaleScorer`.

| rota | o que faz |
| :--- | :--- |
| `GET /scales/definitions/{slug}` | o formulário de uma escala |
| `GET /scales/assignable` | as escalas que o prescritor pode enviar ao paciente |
| `POST /scales/{slug}/responses` | o paciente responde. no diário, responder de novo o mesmo dia corrige aquele dia |
| `GET /scales/{slug}/responses` | as respostas do paciente logado naquela escala, que a grade da semana usa |
| `GET /scales/responses/{id}` | uma resposta com o escore, a faixa e o valor de cada item |
| `PUT /scales/responses/{id}` | o paciente corrige a própria resposta enquanto o prescritor não analisou, inclusive as datas do período. o MEEM não se corrige: o prescritor anula e aplica de novo |
| `PUT /scales/responses/{id}/review` | o prescritor marca que já conferiu, e o paciente para de editar |
| `PUT /scales/responses/{id}/annul` | anula a resposta com motivo |
| `POST /scales/mental-state-exam/appointments/{appointmentId}` | o prescritor aplica o MEEM dentro da consulta, depois que ela aconteceu. o exame só é gravado com as 11 seções e a escolaridade |
| `POST /patients/{patientId}/scales` | envia uma escala avulsa ao paciente |
| `GET /patients/{patientId}/scales` | as tarefas de escala do paciente |
| `GET /patients/{patientId}/scales/overview` | o que espera resposta e o que já foi respondido |
| `GET /patients/{patientId}/scales/responses` | as escalas respondidas, para o histórico |

- cada tarefa vale por um período. o job diário fecha a que passou do prazo: com ao menos uma resposta ela conta como respondida, e sem nenhuma fica como não respondida, que no gráfico é lacuna e nunca zero (RN10)
- a ficha de acompanhamento e o diário do sono são um registro por dia, apresentados como a grade da semana do papel
- a correção de uma escala de período ou pontual vai pelo `PUT` com as datas junto: mudar a data move a mesma resposta, em vez de criar outra no dia novo como o envio faria. o dia de início identifica a resposta, então mover para cima de outra é recusado. no diário a data é o dia da grade e não muda na correção
- o paciente responde uma escala de cada vez: a resposta trava a linha do paciente até o fim da transação, então dois envios do mesmo dia ao mesmo tempo (duplo toque, retry da rede) não viram duas linhas do mesmo dia. o segundo espera e corrige o dia. pelo mesmo motivo a consulta fica travada enquanto o MEEM ou uma receita é lançada nela, e o prescritor fica travado enquanto envia uma escala avulsa ou cria um acompanhamento
- item em branco não é gravado, e escala validada sem todos os itens não tem escore. essa resposta pela metade é guardada com o resultado "Incompleta: sem escore", mas não vale pela tarefa: ela continua pendente, o lembrete continua cobrando e no prazo vira não respondida. completar depois, pela correção, fecha a tarefa e avisa o prescritor
- a resposta avisa o prescritor quando a escala fica completa. nas escalas de preenchimento diário o aviso é um só, quando o período fecha, dizendo quantos dias foram preenchidos
- o acompanhamento de 90 dias vai do primeiro ao nonagésimo dia, e o que começa hoje manda a primeira rodada na hora, sem esperar o job de amanhã. começo no passado ou no futuro fica com o job
- a tarefa do diário diz que é diária (`daily`), e só ela mostra a barra de dias preenchidos na tela
- a tarefa guarda se veio do acompanhamento automático. só essa marca o ciclo dos 90 dias: a escala enviada à mão não adianta a próxima rodada, mas a mesma escala nunca fica pendente duas vezes. se a rodada chega enquanto uma enviada à mão ainda está aberta, ela vira a rodada e ganha o prazo da periodicidade
- o escore de escala validada sai do algoritmo oficial do instrumento e vem sempre com a faixa (RN13 e RN14)
- o MEEM é de heteroaplicação: só o prescritor aplica, dentro de consulta confirmada, e ele nunca vira tarefa do paciente (RN09)
- o MEEM é a exceção à regra do item em branco: como não tem correção, exame com seção ou escolaridade em branco é recusado com a lista do que falta, em vez de ser gravado sem escore ou sem faixa (RN14)

## exportação

| rota | o que faz |
| :--- | :--- |
| `GET /patients/{patientId}/export/appointments.csv` | as consultas do paciente, com a conduta de cada atendimento |
| `GET /patients/{patientId}/export/prescriptions.csv` | as prescrições, com composição, posologia e vigência |
| `GET /patients/{patientId}/export/scales.csv` | as escalas respondidas, uma linha por item, com o escore e a faixa |
| `GET /patients/{patientId}/export/anamneses.csv` | a anamnese, uma linha por pergunta respondida |
| `GET /patients/{patientId}/export/progress.csv?attribute=&period=` | a série de um atributo no período |

- todas aceitam `anonymous=true`, que é a exportação para pesquisa: sai sem nome, CPF, e-mail, telefone e endereço, e o paciente e o prescritor aparecem só por um número. só o prescritor pode pedir esse modo. texto livre, como queixa e observação, sai como foi escrito
- o arquivo vai como anexo, separado por ponto e vírgula e com marca de UTF-8, que é como a planilha abre com acento certo
- o paciente exporta os próprios dados e o prescritor os dos pacientes vinculados, pela mesma regra de vínculo das outras rotas (RF30)
- exportar é leitura de prontuário e entra na trilha de auditoria
- não há geração de PDF no servidor: o front tem uma página de impressão que o navegador salva em PDF

## avisos e lembretes

| rota | o que faz |
| :--- | :--- |
| `GET /notifications?page=` | os avisos da conta logada, 20 por página, com o número de não lidos |
| `POST /notifications/{id}/read` | marca um aviso como lido |
| `POST /notifications/read-all` | marca todos como lidos |
| `DELETE /notifications/{id}` | apaga um aviso da própria conta |

o aviso é sempre da conta logada: o aviso de outra pessoa responde `404`, como se não existisse, para ninguém descobrir quais ids existem.

os lembretes automáticos saem no job diário, junto com o acompanhamento de 90 dias:

- **consulta:** quem tem consulta marcada para o dia seguinte recebe o lembrete de manhã
- **formulário:** a escala que vence em até dois dias e ainda não teve resposta nenhuma gera um lembrete. o aviso dentro do sistema diz qual escala é; o e-mail diz só que há um formulário esperando até tal dia, porque o nome da escala é dado clínico
- cada um sai uma vez só, porque a consulta e a tarefa guardam em `reminder_sent_at` a data em que o aviso foi criado
- os dois também vão por e-mail quando a conta está ativa e mantém os avisos por e-mail ligados no perfil. os outros avisos ficam só no sistema
- o e-mail só sai depois que o banco confirmou o aviso. se o envio falhar, o aviso do sistema continua valendo, o e-mail não é tentado de novo e o log diz de qual conta era, pelo número e sem o endereço
- o horário do job vem de `api.acompanhamento.cron`, que por padrão é 8 da manhã
- cada etapa do job roda sozinha: se uma falhar, o log diz qual foi e as outras seguem
- a api que estava fora do ar na hora do job roda as tarefas do dia quando volta, desde que já tenha passado do horário. o último dia concluído fica na tabela `daily_cycle`, e um dia com etapa que falhou não conta como concluído
- conta desativada não recebe escala nova do acompanhamento: a do paciente pausa o envio até ser reativada, e desativar o prescritor encerra o acompanhamento dos pacientes dele

## painel inicial

| rota | o que faz |
| :--- | :--- |
| `GET /dashboard/patient/{id}` | próximas consultas, escalas esperando resposta com o prazo, a prescrição vigente e os avisos não lidos. os avisos só vêm para o próprio paciente: o prescritor vê o painel sem a caixa pessoal dele |
| `GET /dashboard/prescriber/{id}` | pacientes ativos, consultas de hoje, pedidos de consulta esperando resposta e escalas vencidas sem resposta |

cada lista traz no máximo cinco itens, porque o painel é um resumo e cada cartão leva para a tela que tem a lista inteira. nada aparece aqui sem origem no resto do sistema.

## evolução

| rota | o que faz |
| :--- | :--- |
| `GET /progress/attributes` | o que dá para acompanhar num gráfico, com a escala, os limites do eixo e as faixas do instrumento |
| `GET /patients/{patientId}/progress?attribute=&period=` | a série do atributo no período, uma data e um valor por preenchimento |
| `GET /patients/{patientId}/progress/appointments?period=` | as consultas do período, para marcar no gráfico em que dia houve atendimento |
| `GET /patients/{patientId}/progress/comments?period=` | o que o paciente escreveu nas escalas do período, por data |

- `periodo` é `DIAS_15`, `DIAS_30`, `DIAS_60`, `DIAS_90` ou `TUDO`. o `TUDO` é o "todo o tempo" da tela do prescritor
- dia sem resposta não vira ponto no zero: ele simplesmente não entra na série (RN10)
- resposta anulada sai do gráfico e dos comentários, mas continua no histórico
- acrescentar uma escala não pede mudança aqui: os atributos saem do catálogo das escalas (RNF08)

## guarda do prontuário

o prontuário tem guarda mínima de 20 anos (Lei 13.787/2018), então nenhuma rota apaga dado clínico. encerrar o acompanhamento de 90 dias é `PUT /patients/{patientId}/treatment-protocol/end` e só marca o protocolo como inativo. a única rota `DELETE` da api é a de notificação, que pertence à própria conta.

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
| `403` | logado, mas sem permissão para aquele dado, conta desativada tentando entrar ou requisição com o cookie da sessão vinda de outro endereço |
| `404` | registro ou rota que não existe. a mensagem é genérica: o detalhe com o id vai para o log, não para a tela |
| `405` | método http que a rota não aceita |
| `415` | corpo enviado num formato que a rota não aceita. a api só recebe json |
| `409` | e-mail, CPF ou registro que já pertence a outra conta, com o campo em `errors`. o cadastro do paciente não usa: responde `400` sem dizer o campo |
| `429` | senha errada demais no login, na troca de senha ou de e-mail, pedidos demais de link de senha nova ou cadastro recusado vezes demais |
| `500` | erro inesperado, registrado no log |

`GET /health` responde se a api e o banco estão de pé. é o que o docker usa.

## banco

o esquema vem das migrações do flyway em `src/main/resources/db/migration/`. o hibernate só confere se as entidades batem com o banco, nunca altera nada.

- `V1__esquema_base.sql` consolida as migrações antigas numa base limpa, com restrições de nulidade e índices
- toda mudança de esquema é uma migração nova. nunca edite uma migração que já rodou em algum banco
- a regra do horário único (RN08) e a do registro único por dia no diário ficam no java, com trava pessimista na linha do prescritor e na do paciente, e não em índice do banco: o índice precisaria ser parcial (ignorando anuladas e o MEEM), e o h2 dos testes não aceita. a trava roda igual no h2 e no postgres
- essas travas são sql direto (`lockById`): com `@Lock` em jpql o hibernate trava prescritor e paciente (que herdam de `users`) numa consulta separada no h2, e pula essa trava quando a entidade já estava carregada na transação. a consulta e o convite, sem herança, usam `@Lock` normal (`findByIdForUpdate`). os métodos de trava são `MANDATORY`: fora de uma transação a trava soltaria no fim do select sem ninguém perceber, então eles falham na hora
- a ordem das travas é sempre consulta, depois prescritor, depois paciente (que no postgres também é tomado em modo compartilhado por todo insert que aponta pra ele). quem for travar algo novo segue essa ordem, senão duas transações ficam esperando uma pela outra. pelo mesmo motivo o job diário manda todos os envios de um acompanhamento antes de mexer na tarefa avulsa que vira rodada
- isso conta com o `READ COMMITTED` padrão do postgres: depois que a trava libera, a leitura seguinte enxerga o que a outra transação gravou. em `REPEATABLE READ` a corrida voltaria sem ninguém perceber

## testes

```bash
./mvnw clean install
```

os testes rodam no perfil `test`, com h2 em memória, então não precisam de postgres nem de variável de ambiente. todo teste que sobe o contexto do spring precisa de `@ActiveProfiles("test")`.

testes de fluxo que envolvem job agendado ou serialização de resposta devem rodar sem `@Transactional`: a transação do teste esconde carregamento sob demanda que falharia em uso real.

os testes de corrida (`AgendaRaceTest`, `ScaleResponseRaceTest` e `ClinicalRecordRaceTest`) seguram a trava numa transação de fora, pelo `HeldLock`, disparam o fluxo numa thread e conferem que ele espera. a url do h2 de teste leva `LOCK_TIMEOUT=10000` por causa deles: com o padrão de 2 segundos o fluxo que espera cairia por tempo esgotado.

## padrão de código

vale para todo código novo ou reescrito:

- dto de entrada e saída em toda rota, nunca a entidade
- regra de negócio no serviço, com `@Transactional`
- nomes em inglês, em camelCase e descritivos
- comentários curtos, em minúsculas, sem acento e sem pontuação

## situação dos módulos

a fundação (configuração, erros, sessão, administração e migração base), o módulo de acesso e identidade (login, convite, cadastro, termo de consentimento, perfil e recuperação de senha), o de autorização (perfil e vínculo em toda rota, fim da exclusão de dado clínico e trilha de auditoria), o de atendimento (consulta, prescrição, anamnese, histórico e arquivamento de paciente), o de agenda (horários de atendimento, pedido do paciente e agenda do prescritor) o de escalas (motor único, tarefas com prazo e acompanhamento automático de 90 dias) o de evolução (gráfico com a faixa do instrumento, a dose e o relato do paciente) o painel inicial de cada perfil, os avisos com lembretes automáticos e a exportação já seguem o padrão novo. a remodelação cobriu todos os módulos da ordem de trabalho do §8.6 do documento de requisitos. o `open-in-view` fica ligado porque vários controllers montam a resposta depois do serviço, e o `MainScreensWithDemoDataTest` abre as telas principais sem a transação dos outros testes para pegar esse tipo de erro.
