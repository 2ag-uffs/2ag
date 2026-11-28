# Documento de Requisitos do Sistema — 2AG

**Versão:** 2.1
**Data:** 13 de setembro de 2026 (revisão técnica, §8) · versão 2.0 em 12 de setembro de 2026
**Substitui:** requisitos v1.0 (06/07/2025)
**Autoras:** Caroline de Quadros Piazza (20230000690), Maiqueli Eduarda Dama Mingoti (20230004643)
**Cliente:** Instituto EDMA — Chapecó/SC
**Interlocutora:** Brunna Varela, biomédica prescritora

---

## 1. Propósito desta versão

A v1.0 foi escrita na fase de elicitação, antes de existir código. A v2.0 é escrita **depois** de uma auditoria completa da implementação e reflete três aprendizados:

1. **Amplitude não é produto.** A v1.0 especificou 28 requisitos funcionais e a implementação cobriu quase todos superficialmente. O resultado é um sistema com 25 telas que não pode ser usado com paciente real, porque falha nos três eixos que definem software clínico: autorização, integridade do dado e automação.

2. **A v1.0 não especificou o requisito mais importante.** Nenhum dos 28 RFs originais descreve *quem pode ver o dado de quem*. O RNF03 menciona "controle de acesso baseado em perfis" em uma linha. Isso produziu uma implementação onde qualquer usuário autenticado lê e apaga o prontuário de qualquer outro. Nesta versão, autorização é um módulo com requisitos próprios e critérios de aceite verificáveis.

3. **A v1.0 especificou telas, não o ciclo de cuidado.** A dor central da cliente, registrada em §1.4 e §1.5 do documento original, é a **sobrecarga operacional**: ela faz tudo manualmente e sozinha. O sistema construído exige que ela designe cada escala, para cada paciente, manualmente — trocou o WhatsApp por outra interface manual. O ciclo automático de 90 dias passa a ser requisito de primeira classe (RF32).

A numeração RF01–RF28 e RNF01–RNF11 da v1.0 foi **preservada** para rastreabilidade. Cada requisito carrega um status: `mantido`, `alterado`, `ampliado` ou `descartado`. Requisitos novos começam em RF29 e RNF12.

---

## 2. Visão do produto

Sistema web responsivo para gestão clínica de tratamento com fitocanabinoides, cobrindo o ciclo completo de cuidado: triagem por anamnese → consulta → acompanhamento longitudinal de 90 dias com coleta estruturada de dados e visualização de evolução.

**O que o sistema é:** um prontuário especializado em acompanhamento longitudinal, que automatiza a coleta periódica de dados sintomáticos e entrega evolução visualizável ao prescritor.

**O que o sistema não é:** não é ERP de clínica (sem faturamento, estoque ou financeiro), não comercializa produtos, não substitui o prontuário legal da clínica — convive com ele via exportação.

### 2.1 Perfis de usuário

| Perfil | Papel técnico | Descrição |
|---|---|---|
| Paciente | `ROLE_PATIENT` | Preenche anamnese, escalas e fichas de acompanhamento; consulta seu histórico, progresso e prescrições; agenda consultas |
| Prescritor | `ROLE_PRESCRIBER` | Conduz consultas, emite prescrições, designa escalas, acompanha evolução dos **seus** pacientes |
| Administrador | `ROLE_ADMIN` | Provisiona contas de prescritor e desativa ou reativa contas de prescritor e de paciente. Perfil operacional, sem acesso a dado clínico |

> **Mudança em relação à v1.0:** a v1.0 usava `ROLE_ADMIN` como sinônimo de prescritor. Isso criou uma confusão que, na implementação, virou falha de segurança: um perfil chamado "admin" naturalmente recebeu acesso irrestrito. Os papéis agora são três e `ROLE_ADMIN` **não vê prontuário**.

---

## 3. Decisões de escopo desta versão

### 3.1 Descartado

| Item (origem v1.0) | Justificativa |
|---|---|
| **Integração com WhatsApp** (RNF10; tabelas de necessidades §1.5) | Exige conta comercial verificada, templates aprovados pela Meta e custo por conversa. Uma integração parcial é pior que nenhuma: gera expectativa de canal oficial sem confiabilidade. **Substituído por** notificação in-app (RF14/RF15) + e-mail (RF34), que atendem a mesma necessidade — comunicação entre consultas |
| **Integração com o prontuário da clínica** (RNF09) | O próprio documento original a classifica como "futura". **Substituído por** exportação em formatos abertos (RF33) |
| **RF16 — Tela de relatórios do paciente** | Sobrepõe RF12 (histórico) e RF27 (progresso). **Consolidado** em RF33, como ação de exportar dentro dessas telas |
| **RF17 — Tela de relatórios do prescritor** | Mesma justificativa. Consolidado em RF33 |

### 3.2 Rebaixado de funcionalidade para requisito de arquitetura

**Interface multilíngue pt/en/es** (v1.0 §1.5 e tabela de necessidades).

Atende "pacientes internacionais, ainda que em menor escala" — é o item de maior custo por unidade de valor do documento original. Traduzir e manter três idiomas de uma interface clínica inteira consome esforço que hoje é melhor aplicado em segurança e validade de escala.

**Nova forma (RNF12):** a arquitetura é preparada para múltiplos idiomas — nenhuma string de interface fica embutida no código, todo texto vem de arquivo de dicionário, e o idioma sai da preferência do usuário (não da locale do servidor). **Apenas `pt-BR` é traduzido nesta entrega.** Acrescentar `en` ou `es` passa a ser trabalho de tradução, não de programação.

Esta decisão também corrige um defeito real: a implementação atual resolve mensagens com `Locale.getDefault()`, a locale do **servidor** (hoje `pt_BR`), de modo que o comportamento muda conforme a máquina onde roda.

### 3.3 Corrigido por invalidade clínica

Três escalas produzem hoje um escore que **não corresponde ao instrumento que dizem implementar**. Como o número é entregue à prescritora com aparência de índice validado e pode influenciar ajuste de dose, a correção é obrigatória (RF23, RF25, RF28 e Anexo A).

### 3.4 Alterado por segurança

**Cadastro de prescritor deixa de ser autoatendimento público.** Na implementação atual `POST /prescritor` é um endpoint aberto que cria conta com privilégio elevado, sem validação de registro profissional. Combinado com a ausência de isolamento por vínculo, isso permite a qualquer pessoa na internet criar uma conta e ler a base clínica inteira.

Contas de prescritor passam a ser criadas por **seed administrativo ou convite** (RF02.2). Não existe cadastro público de profissional de saúde em sistema clínico.

---

## 4. Requisitos Funcionais

Legenda de prioridade: **E** = Essencial (sem ela o sistema não pode ser usado) · **I** = Importante · **D** = Desejável

### 4.1 Módulo — Acesso e Identidade

---

**RF01 — Login** · `mantido` · **E** · Paciente, Prescritor, Administrador

Acesso por e-mail e senha previamente cadastrados. Em caso de sucesso, o usuário é redirecionado à tela inicial correspondente ao seu perfil.

*Critérios de aceite:*
- Credencial inválida retorna **401** com mensagem genérica, sem revelar se o e-mail existe
- Token de acesso expira em no máximo 2 horas
- Token expirado ou com assinatura inválida retorna **401**, nunca 500
- Senha armazenada apenas como hash BCrypt; nenhuma resposta da API contém o campo de senha, em nenhuma circunstância
- Sessão vencida com uma tela aberta (07/11/2026): o **401** dentro do sistema não recarrega a página. Um login abre por cima da tela, só para a mesma conta (e-mail fixo, só a senha é digitada), a tela fica montada, mas escondida enquanto esse login está aberto, e o envio que recebeu o 401 vai de novo com a sessão nova, então uma escala ou anamnese pela metade não perde o que foi preenchido. Nada do que foi digitado é guardado no navegador, que pode ser compartilhado. "Sair" nesse login descarta o envio e leva à tela de login; se a API devolvesse outra conta, nada é reenviado e a página recarrega no login
- Conta desativada com uma tela aberta (decisão de 13/11/2026): o **401** da sessão de uma conta desativada traz o mesmo motivo do login, *Conta desativada. Fale com a clínica*, e no lugar do login por cima aparece esse aviso só com "Sair", sem pedir a senha e sem prometer enviar o que estava na tela. O motivo só vai para a sessão da própria conta, dentro do prazo e com login depois da última troca de senha e da última saída da conta; sem sessão, com a sessão vencida, com a de antes da troca de senha ou com a que a saída da conta já tinha encerrado, a resposta continua o 401 genérico, porque esse cookie pode ter ficado com outra pessoa. Por isso as sessões antigas são encerradas na reativação da conta, e não na desativação. Se a sessão já tinha vencido, o login por cima recebe a recusa da conta desativada e mostra o mesmo aviso

---

**RF02.1 — Cadastro de paciente** · `mantido` · **E** · Paciente

Autoatendimento, pelo **link de convite** do prescritor que o acompanhará (RN06). O paciente informa nome completo, CPF, e-mail, data de nascimento, telefone, endereço e senha. *Corrigido (decisão de 14/11/2026, revisão das regras R17): o texto falava de um código do prescritor, que saiu em 13/09/2026.*

*Critérios de aceite:*
- E-mail e CPF são únicos no sistema (ver RN04)
- Senha com mínimo de 8 caracteres, uma maiúscula, um número e um caractere especial
- CPF validado pelos dois dígitos verificadores (módulo 11), conferidos pelo próprio sistema, sem biblioteca: o navegador avisa no campo antes de enviar e a API confere de novo. Sequência de um número só repetido (111.111.111-11) é recusada mesmo fechando a conta (23/10/2026)
- E-mail sem ponto depois do @ (`maria@gmail`) é recusado no campo, porque o link de senha nova e os lembretes nunca chegariam. A mesma regra vale na troca de e-mail do perfil e na conta de prescritor criada ou corrigida pela administração (16/10/2026)
- Convite inexistente, vencido, cancelado ou já usado rejeita o cadastro com **400** e mensagem clara
- O vínculo paciente→prescritor é criado no cadastro, pelo convite, e hoje é fixo: nem o paciente nem a administração o alteram (ver RN05)

---

**RF02.2 — Provisionamento de prescritor** · `alterado` · **E** · Administrador

Contas de prescritor são criadas por administrador, por seed de inicialização ou por fluxo de convite. **Não há endpoint público de cadastro de prescritor.**

Dados obrigatórios: nome completo, CPF, e-mail, profissão e registro profissional (tipo + número: CRM, COREN, CRBM, CRP). Data de nascimento, telefone e endereço são opcionais nesse cadastro: a administração registra o prescritor com o que tem e ele completa esses dados no próprio perfil (RF18) *(07/11/2026)*.

*Critérios de aceite:*
- A combinação tipo + número de registro é única. *O conselho é comparado e guardado em maiúsculas e o número sem espaço nas pontas, então "crbm" e "77777 " são o mesmo registro que "CRBM" e "77777" (16/10/2026)*
- O CPF passa pela mesma conferência dos dois dígitos verificadores do cadastro do paciente (RF02.1), feita pelo próprio sistema (23/10/2026). A tela da administração faz a mesma conferência antes de enviar, no cadastro e na correção, então o CPF digitado com letras é recusado como inválido, e não como em branco nem ignorado na correção (13/11/2026)
- A administração corrige o registro profissional, a profissão e o CPF de um prescritor digitados errado, com a mesma conferência de CPF e de unicidade do cadastro; a correção fica na trilha de auditoria como alteração de conta de prescritor (07/11/2026)
- O prescritor vincula pacientes por links de convite de uso único (ver RN06). O código de vínculo fixo por prescritor saiu em 13/09/2026, e hoje não existe transferência de paciente de um prescritor para outro: os pacientes do prescritor desativado continuam vinculados a ele (RN05, decisão de 14/11/2026, revisão das regras R17)
- Requisição anônima a qualquer rota de criação de prescritor retorna **401/403**
- O administrador **não escolhe nem conhece** a senha do prescritor: a conta nasce com um link de primeiro acesso de uso único (48 h), enviado por e-mail ou, sem SMTP, mostrado ao administrador para entregar. Quem cria a senha é o próprio prescritor
- O seed de desenvolvimento roda **somente** no profile `dev` e nunca em produção
- Desativar o prescritor tira o acesso dele, mas não fecha nem cancela nada: as escalas esperando resposta continuam pendentes, os pedidos e as consultas marcadas continuam na agenda e os prazos correm como sempre. Enquanto a conta estiver desativada, nenhum lembrete de formulário ou de consulta (aviso ou e-mail) sai para os pacientes dele, porque ninguém leria a resposta. Reativar a conta faz os lembretes voltarem, e a escala cujo prazo venceu enquanto a conta estava desativada fecha como não respondida pelo job das 8h, como sempre. O acompanhamento automático dos pacientes dele só fica pausado, como na conta de paciente desativada: nenhuma rodada nova sai enquanto a conta estiver desativada, e reativar retoma de onde parou, com o fim do acompanhamento na mesma data. Antes a desativação encerrava os acompanhamentos, e uma desativação de poucos dias obrigava a montar todos de novo (decisão de 14/11/2026, revisão das regras R18, que muda a de 07/11/2026). A resposta da desativação diz quantas escalas esperando resposta e quantos pedidos e consultas por vir ficaram, só os números, e a tela da administração mostra isso no aviso (07/11/2026)
- A conta de paciente desativada pela administração aparece para o prescritor com o selo "Conta desativada" na lista de pacientes e no cabeçalho do prontuário, e o selo some quando a conta é reativada. Nenhuma contagem muda com isso: só o arquivamento tira o paciente da lista (07/11/2026). O mesmo selo aparece na agenda e nos pedidos de consulta, porque na tela em que confirma o pedido a prescritora não sabia que o paciente não ia ver a confirmação nem receber o lembrete. Desativar continua sem fechar nada: o pedido segue esperando resposta e a consulta marcada continua valendo, porque a conta pode ser reativada logo depois (decisão de 14/11/2026, revisão das regras R25)

---

**RF03 — Tela inicial (dashboard)** · `mantido` · **E** · Paciente, Prescritor

Painel personalizado por perfil, exibindo consultas futuras, escalas pendentes, notificações e alertas clínicos.

*Critérios de aceite:*
- Dashboard do paciente exibe **consultas futuras reais** consultadas no banco. A implementação atual retorna lista vazia fixa — este critério existe para fechar esse defeito
- Dashboard do prescritor exibe: nº de pacientes ativos, consultas do dia, formulários pendentes
- Nenhum dado exibido pertence a paciente fora do escopo do usuário (RF30)

---

**RF18 — Configurações e perfil** · `ampliado` · **I** · Paciente, Prescritor

Alteração de dados pessoais, de contato, **idioma preferido** e **preferências de notificação**.

*Critérios de aceite:*
- Alteração de senha exige a senha atual e re-hash com BCrypt
- Alteração do e-mail de acesso exige a senha atual e só vale depois de confirmada por link enviado ao endereço novo; o endereço antigo é avisado
- Nunca é possível gravar senha em texto puro (ver RN12)
- O idioma escolhido determina o idioma das mensagens do sistema para aquele usuário (RNF12)
- Preferência de notificação controla, por canal e por tipo, o que o usuário recebe (RF14/RF15/RF34)
- A troca da data de nascimento pelo paciente entra na trilha de auditoria, com a data antiga e a nova, e a que muda a faixa de idade, de menor para adulto ou o contrário, avisa a prescritora (decisão de 14/11/2026, revisão das regras R9, §8.6 *Aceite pelo responsável legal*)
- *Acrescentado em 20/11/2026:* toda troca de senha, pelo perfil ou pelo link de senha nova (RF35), avisa a conta por e-mail, em qualquer perfil, para a pessoa perceber quando não foi ela. O e-mail diz só a conta, o dia e a hora da troca e o que fazer se não foi a pessoa, sem link nenhum e sem dado do tratamento. Ele sai mesmo com os avisos por e-mail desligados no perfil, porque essa preferência cobre só os lembretes de formulário e de consulta (RF34). A troca pelo perfil também cancela o link de senha nova que ainda valia, como o link novo e a confirmação do e-mail novo já cancelavam: quem retomou a conta não deixa para trás um link que troca a senha de novo

---

### 4.2 Módulo — Autorização e Rastreabilidade *(novo)*

Este módulo não existia na v1.0. É o mais importante do documento.

---

**RF29 — Controle de acesso por perfil** · `novo` · **E** · Todos

Toda rota da API declara explicitamente quais perfis podem acessá-la. Não existe rota protegida apenas por "estar autenticado", exceto as de dados do próprio usuário.

*Critérios de aceite:*
- Um token de paciente recebe **403** em toda rota de gestão clínica (criar prescrição, designar escala, listar pacientes, registrar consulta, aplicar MEEM)
- Um token de prescritor recebe **403** nas rotas administrativas de provisionamento
- Não existe endpoint que liste todos os pacientes, todas as anamneses ou todas as escalas do sistema sem filtro de escopo
- Requisição sem token a qualquer rota não pública retorna **401**

---

**RF30 — Isolamento por vínculo** · `novo` · **E** · Todos

Todo acesso a dado clínico valida o vínculo entre o usuário autenticado e o paciente dono do dado:

- **Paciente:** acessa exclusivamente os dados onde `paciente.id == usuarioLogado.id`
- **Prescritor:** acessa exclusivamente dados de pacientes onde `paciente.prescritor.id == usuarioLogado.id`
- **Administrador:** não acessa dado clínico

*Critérios de aceite:*
- Prescritor A recebe **403** ao acessar qualquer dado de paciente do prescritor B (leitura, escrita e exclusão)
- Paciente recebe **403** ao acessar dado de outro paciente, inclusive trocando o id na URL
- Paciente recebe o mesmo **403** ao abrir o próprio MEEM pelo número da resposta, e as listas dele vêm sem o exame (RN09, decisão de 13/11/2026, §8.3)
- A validação ocorre no servidor. Ocultar a opção na interface não satisfaz este requisito
- Existe teste automatizado cobrindo cada uma das três regras acima

---

**RF31 — Trilha de auditoria** · `novo` · **E** · Prescritor, Administrador

Toda escrita em prontuário, prescrição, anamnese, escala e agendamento registra: autor, data e hora, entidade afetada e natureza da operação.

*Critérios de aceite:*
- Registros de auditoria são imutáveis e não podem ser removidos pela API
- Toda entidade clínica possui data de criação e de última alteração
- Alteração de prescrição preserva a versão anterior (RF05)
- A trilha é consultável por paciente e por período
- Cada arquivo exportado é uma linha própria da trilha, *Exportação*, com o tipo do arquivo e a marca de pesquisa no modo sem identificação, sem a janela de 30 minutos da abertura de tela (decisão de 14/11/2026, revisão das regras R19)

*Origem:* RF04 da v1.0 exigia registros "vinculados ao profissional responsável e paciente, devidamente datados", e a tabela de necessidades exigia "histórico de ajustes, datas, justificativas". Nada disso foi implementado e nenhum RF cobria a auditoria como requisito próprio.

---

### 4.3 Módulo — Atendimento e Prontuário

---

**RF04 — Consulta clínica** · `mantido` · **E** · Prescritor

Registro de observações clínicas, diagnósticos, condutas e plano terapêutico, evolução do quadro (melhora, piora, manutenção) e recomendações. Permite emitir prescrição (RF05). Todo registro é vinculado a prescritor e paciente e datado.

*Critérios de aceite:*
- **Modalidade** é um conjunto fechado de valores: `PRESENCIAL`, `REMOTA`
- **Status** é um conjunto fechado: `SOLICITADA`, `AGENDADA`, `EM_ANDAMENTO`, `CONCLUIDA`, `CANCELADA`, `RECUSADA`, `NAO_COMPARECEU`
- Campos de texto clínico aceitam **no mínimo 10.000 caracteres** sem truncamento (ver RN11)
- Toda alteração gera registro de auditoria (RF31)
- O envio repetido não grava em dobro (decisão da dona do projeto, novembro/2026): quando a resposta do salvar se perde, com a consulta nova ou a prescrição (RF05) já gravada, e o prescritor tenta de novo com o mesmo formulário e o mesmo conteúdo em até 10 minutos, o sistema reconhece o envio repetido e devolve o registro que já gravou. Antes nascia uma segunda consulta ou uma segunda receita igual, a primeira receita ficava como substituída, o paciente recebia dois avisos e a limpeza pedia uma anulação com motivo que o paciente lê. Nada muda para o registro feito de propósito: o formulário aberto de novo grava outra consulta ou outra receita, mesmo igual à anterior, e o que foi corrigido antes de tentar de novo também é gravado como registro novo. Fica para confirmar com a clínica se a receita igual repetida na mesma consulta acontece de propósito; a regra já deixa esse caso passar
- Sair do registro da consulta, da prescrição (RF05) ou do Mini-Exame (RF26) com algo digitado e ainda não salvo pergunta antes, pelo botão Voltar, pelo menu ou pelo navegador, como a anamnese e as escalas já faziam. Antes o que foi digitado se perdia sem aviso (13/11/2026). Enquanto o envio está em andamento a saída não pergunta, mas o envio parado atrás do login por cima, quando a sessão vence no meio (RF01), ainda não gravou nada: nesse momento as três telas também perguntam antes de sair, ao recarregar e pelo voltar do navegador, como a anamnese e as escalas, e depois de entrar de novo a tela mostra que o registro foi salvo. Antes a tela saía sem perguntar, o envio guardado era gravado depois do login sem nenhuma tela mostrar e o prescritor podia emitir a receita outra vez (decisão da dona do projeto, novembro/2026)
- O formulário diz, em cada bloco de texto (*Queixa e exame*, *Avaliação* e *Conduta*), que o paciente lê aquele texto em Meu histórico clínico, e o responsável também quando o paciente é menor de idade, porque o menor usa a conta que o responsável opera (16/10/2026) e o paciente vê o próprio histórico completo (RF12). Assim a prescritora escreve sabendo quem lê. Um campo que o paciente não vê fica para a clínica decidir (decisão de 14/11/2026, revisão das regras R8)

---

**RF05 — Prescrição** · `ampliado` · **E** · Prescritor

Prescrição digital com descrição do produto, marca, concentração, posologia, espectro (isolado / broad / full) e instruções específicas. Permite revisão e modificação.

*Critérios de aceite:*
- Identificador único por prescrição
- **Alterar uma prescrição cria nova versão e preserva a anterior**, com data e autor da mudança
- O paciente visualiza a prescrição vigente e o histórico de versões
- Vinculada à consulta que a originou
- Só consulta que já aconteceu gera prescrição; em consulta futura a emissão é recusada
- A nova prescrição passa a ser a vigente quando a consulta dela não é mais antiga que a da vigente atual. Receita lançada numa consulta anterior entra só no histórico, como substituída, e a vigente continua valendo. Sem vigente, a nova passa a valer
- A composição segue a RN03: o isolado tem um canabinoide só, o broad spectrum não leva THC, o mesmo canabinoide não aparece em duas linhas (o "Outro" pode, porque cada linha é um canabinoide fora da lista) e a concentração em % vai até 100. A duração do tratamento vai até 730 dias e a próxima consulta não fica antes do dia da consulta. A recusa aparece no próprio campo, e o formulário avisa antes de enviar (30/10/2026)

*Ampliação:* a tabela de necessidades da v1.0 exigia "histórico de alterações" do produto prescrito e "controle de ajuste de dose: histórico de ajustes, datas, justificativas, inclusive casos de desmame". O RF05 original não mencionava versionamento e a implementação sobrescreve a prescrição.

---

**RF19 — Anamnese** · `mantido` · **E** · Paciente

Formulário de triagem com: profissão, motivo principal da consulta, diagnósticos prévios, tratamentos anteriores, medicações em uso, tipo de dieta, tabagismo, uso de álcool, peso, altura, uso de substâncias recreativas, exercícios físicos, qualidade do sono, ansiedade, dor, histórico familiar, reações adversas, condições genéticas, expectativas com o tratamento, formas de monitoramento e observações gerais.

*Critérios de aceite:*
- Identificador único e data de preenchimento
- Campos descritivos aceitam texto longo sem truncamento (RN11)
- Preenchimento dá baixa automática na tarefa correspondente (RF08)
- Peso e altura aparecem na ficha do histórico, na impressão e no `anamneses.csv` com vírgula decimal (`68,5`), como o peso da consulta: com ponto, a planilha em português lê a resposta como texto. A resposta continua guardada como foi enviada (13/11/2026)
- *Decisão de 14/11/2026, revisão das regras R4:* toda correção guarda a versão anterior da ficha. A prescritora vê "Corrigida em dd/mm/aaaa" e, embaixo da ficha, o que estava antes de cada correção, só nos campos que mudaram. A tela do paciente continua só com a ficha de agora, e a administração não lê nenhuma das duas

---

**RF12 — Histórico clínico (paciente)** · `mantido` · **I** · Paciente

Acesso ao próprio histórico completo: anamnese, diagnósticos, tratamentos prescritos, evoluções e prescrições anteriores. Exportável (RF33). *Desde 14/11/2026, a consulta e a prescrição anuladas aparecem para o paciente só com a data, a marca de anulada e o motivo, porque o registro anulado pode ser de outra pessoa e nunca foi dado dele (decisão de 14/11/2026, revisão das regras R7, §8.6)*

---

**RF13 — Histórico clínico (prescritor)** · `mantido` · **I** · Prescritor

Acesso ao histórico completo dos **seus** pacientes (RF30): tratamentos, diagnósticos, prescrições e escalas respondidas.

---

### 4.4 Módulo — Agenda

---

**RF10 — Agendamento (paciente)** · `mantido` · **I** · Paciente

O paciente agenda consulta visualizando a disponibilidade do seu prescritor. O sistema envia lembretes automáticos.

*Critérios de aceite:*
- Não é possível agendar horário já ocupado (RN08)
- Não é possível agendar no passado
- O paciente só vê a agenda do prescritor ao qual está vinculado
- Lembrete disparado automaticamente antes da consulta (RF34)
- O paciente pede horário com pelo menos 24 horas de antecedência, e a tela só oferece esses horários. O prescritor continua marcando direto sem antecedência mínima (decisão de 13/11/2026, §8.3)
- O paciente tem um pedido esperando resposta por vez: o pedido novo substitui o anterior, que é cancelado e libera o horário (RN08, decisão de 13/11/2026, §8.3)
- O sistema não é canal de urgência: junto de todo texto livre que o paciente escreve (motivo do pedido de consulta, observações das escalas e da ficha diária e cada parte da anamnese) fica o aviso fixo *Isto não é atendimento de urgência. Se algo piorou, fale com a clínica ou procure um serviço de saúde.* E o pedido que chega na véspera do horário sem resposta avisa o paciente dentro do sistema, uma vez só, para ele falar com a clínica (decisão de 14/11/2026, revisão das regras R1)
- A modalidade (presencial ou remota) vem sem nada marcado em toda tela que cria consulta: o pedido do paciente, a *Nova consulta* da agenda do prescritor e o registro de consulta. A pessoa escolhe, e enviar sem escolher marca o campo e leva o foco até ele. Antes vinha marcado *Presencial*, o contrário do que a clínica faz (a maioria das consultas é remota, RF04), e o paciente que não mexia na caixa pedia uma consulta presencial sem querer (decisão de 14/11/2026, revisão das regras R24)

---

**RF11 — Agenda (prescritor)** · `mantido` · **I** · Prescritor

Visualização e gestão da própria agenda, criação e edição de consultas, com notificação ao paciente em caso de alteração.

*Critérios de aceite:*
- Alteração ou cancelamento notifica o paciente (RF14). Na consulta que o paciente já recebeu por e-mail (o lembrete), o cancelamento e a remarcação para outro dia sem lembrete novo também vão por e-mail, respeitando a preferência do paciente (RF18, decisão de 14/11/2026, revisão das regras R11)
- Só é possível agendar para pacientes vinculados (RF30)
- Período de atendimento em que não cabe nem a consulta mais curta (primeira consulta ou retorno) é recusado ao salvar, com o erro na linha do período, porque nunca daria horário livre. Vale também quando a duração sobe acima de um período que já existia (decisão de 13/11/2026, §8.3)
- O prescritor não marca consulta nova para paciente arquivado, assim como o paciente arquivado não pede consulta: a API recusa com "Paciente arquivado não recebe consulta nova. Reative o paciente antes de marcar". As consultas marcadas antes do arquivamento continuam podendo ser remarcadas e canceladas (decisão de 13/11/2026, §8.3)
- Depois do fim da consulta (o início mais a duração, o mesmo fim que libera a falta), o prescritor não cancela mais: a API recusa com 400 ("Esta consulta já terminou e não pode mais ser cancelada. Marque a falta ou registre o atendimento") e a agenda tira o *Cancelar*. Para a consulta que passou sobram marcar a falta e registrar o atendimento. Antes o paciente recebia *Consulta cancelada*, e o e-mail quando o lembrete tinha ido por e-mail, de uma consulta que já tinha acontecido ou não. Antes do fim nada muda, a regra das 24 horas do paciente continua a mesma e o pedido segue o caminho dele, de confirmar ou recusar (decisão de 17/11/2026, apontado nos testes do grupo)
- A grade da semana da agenda esconde os pedidos recusados e as consultas canceladas, que não têm ação nenhuma: o paciente que pedia de novo deixava o pedido recusado ao lado do novo e a grade enchia. O botão *Mostrar recusados e cancelados*, com a quantidade da semana quando há algum, mostra os dois de volta no lugar, e *Esconder recusados e cancelados* tira de novo, enquanto a tela está aberta. Nada mais some: as consultas marcadas, em andamento, concluídas, com falta e anuladas, os pedidos aguardando resposta e o histórico continuam como antes (decisão de 17/11/2026, apontado nos testes do grupo)
- Na grade da semana, o dia sem consulta aparece como uma linha curta, com o nome do dia e "Nenhuma consulta." lado a lado, e não estica até a altura do dia cheio ao lado. No celular, a agenda abre rolada até o dia de hoje, uma vez, ao carregar a tela; os pedidos aguardando resposta continuam acima. Antes, os dias vazios ocupavam o mesmo espaço dos dias cheios e, no celular, o prescritor passava por vários cartões vazios antes de chegar em hoje (decisão da dona do projeto, novembro de 2026)

---

### 4.5 Módulo — Escalas e Acompanhamento

---

**RF08 — Central de escalas (paciente)** · `ampliado` · **E** · Paciente

Tela central listando as avaliações pendentes e o histórico. Pendentes em cards, com nome da escala, indicador de status e botão de preenchimento. Histórico em tabela, com nome, data de conclusão, **resultado** e ações (ver respostas, ver progresso).

*Critérios de aceite:*
- A coluna **resultado** exibe o escore real da escala com sua faixa interpretativa. A implementação atual exibe a string fixa `"Concluído"` — este critério existe para fechar esse defeito
- "Ver progresso" leva à tela de progresso já filtrada naquela escala (RF27)
- **Respondidas** mostra uma linha por escala, com o resultado mais recente e um único "Ver progresso". As outras respostas da escala ficam dentro da linha e abrem a pedido, cada uma com o seu "Ver respostas" (decisão da dona do projeto, novembro de 2026)
- **Toda escala designável possui tela de preenchimento acessível.** Hoje três tipos (`ESCALA_PITTSBURGH`, `REGISTRO_DOR`, `REGISTRO_TEA`) apontam para rotas inexistentes
- Escala aplicada pelo prescritor (MEEM) não aparece como tarefa do paciente (RN09), nem em **Respondidas**: desde 13/11/2026 o resultado do MEEM é conversado na consulta e fica fora de toda tela do paciente (§8.3)

---

**RF09 — Designação de escalas (prescritor)** · `mantido` · **I** · Prescritor

O prescritor seleciona escalas padronizadas e as disponibiliza aos seus pacientes.

*Critérios de aceite:*
- Só é possível designar a paciente vinculado (RF30)
- A designação notifica o paciente (RF14) com link para uma rota existente
- Não é possível designar escala de aplicação exclusiva do prescritor (RN09)

---

**RF32 — Ciclo automático de acompanhamento de 90 dias** · `novo` · **E** · Sistema

Ao iniciar o acompanhamento de um paciente, o sistema passa a designar **automaticamente** as escalas configuradas no protocolo, na periodicidade definida, pelos 90 dias de duração do acompanhamento.

*Critérios de aceite:*
- O prescritor define o protocolo do paciente: quais escalas e com que periodicidade (semanal, quinzenal, mensal)
- A designação ocorre sem intervenção humana e notifica o paciente
- Escala pendente não respondida gera lembrete ao paciente e sinalização no dashboard do prescritor
- O ciclo encerra automaticamente ao fim dos 90 dias, com possibilidade de renovação
- O prescritor pode encerrar o ciclo antes do fim: os envios param e as escalas do ciclo que ainda esperavam resposta são fechadas na hora, sem contar como atraso do paciente (decisão da dona do projeto, novembro de 2026)
- O prescritor recebe um aviso no sistema 7 dias antes do fim do ciclo e outro no dia do fim, com link para renovar, e a renovação vem preenchida com o último acompanhamento, para sair com um clique (decisão de 14/11/2026, revisão das regras R5)
- O prescritor pode designar escala avulsa a qualquer momento (RF09), sem interferir no ciclo

*Justificativa:* este é o requisito que atende a dor central registrada na v1.0 §1.4 e §1.5 — a sobrecarga operacional da prescritora, que acumula funções clínicas e administrativas sozinha. A tabela de necessidades pedia literalmente: *"Acompanhamento semanal automatizado — sistema deve enviar formulários semanalmente por 90 dias"*. A v1.0 não transformou isso em requisito funcional e a implementação não possui nenhum agendamento automático: toda escala é designada manualmente, uma por uma. Sem este requisito, o sistema substitui a planilha no WhatsApp por outra tarefa manual.

---

**RF06 / RF20 — Ficha de acompanhamento** · `mantido` · **E** · Paciente

Registro de gotas utilizadas pela manhã e à tarde e avaliação, em escala de 0 a 10, de: dor, sono, humor, tremor, ansiedade, disposição/energia, função intestinal, apetite, concentração, interação social, rigidez/espasticidade, redução no uso de outra substância, náusea e vômito, desempenho esportivo e condição dermatológica. Campo de comentário opcional.

*Critérios de aceite:*
- Identificador único e data de preenchimento
- **Item não respondido é registrado como ausente, não como zero** (RN10)
- Preenchimento dá baixa automática na tarefa (RF08)
- A ficha em papel usada pela clínica (`docs/scales/acompanhamento-semanal.pdf`) é uma **grade semanal**: um formulário cobre um período (`De __/__/__ A __/__/__`) com uma coluna por dia e o registro de gotas da manhã e da tarde em cada uma. O sistema deve permitir o preenchimento diário ao longo da semana, sem exigir que o paciente abra um formulário novo por dia, e sem perder o registro por data individual de que o gráfico de progresso depende
- O ideal é o preenchimento diário, mas nada impede o paciente de preencher de uma vez os dias que esqueceu dentro do período da tarefa aberta: a grade mostra do início do período até hoje, mesmo quando ele passa de uma semana. Dia que ainda não chegou não é aceito (resposta da clínica em 16/10/2026)

---

**RF07 — Acompanhamento (prescritor)** · `mantido` · **I** · Prescritor

Visualização da evolução clínica do paciente por gráficos, tabelas e comentários inseridos pelo paciente, organizados por data.

---

**RF21 — Escala de Ansiedade de Hamilton (HAM-A)** · `alterado` · **E** · Paciente

Avaliação com pontuação de 0 a 4 em cada item, onde valores maiores indicam sintomas mais severos. Cada item exibe descrição comportamental de apoio. O sistema calcula e armazena o escore total.

*Critérios de aceite:*
- **14 itens**, conforme o instrumento original: humor ansioso, tensão, medos, insônia, comprometimento intelectual, humor deprimido, somatizações motoras, somatizações sensoriais, sintomas cardiovasculares, sintomas respiratórios, sintomas gastrointestinais, sintomas geniturinários, sintomas autonômicos e **comportamento durante a entrevista**
- Escore total de 0 a 56, com faixa interpretativa (Anexo A.1)
- A v1.0 e a primeira implementação contemplavam apenas 13 itens, com máximo de 52 — fora da escala do instrumento. Os 14 itens estão no sistema desde 12/09/2026 (Anexo A.1)

---

**RF22 — Diário de sono** · `mantido` · **I** · Paciente

Registro da última noite: horário de dormir, horário de levantar, tempo total na cama, tempo até adormecer, número de despertares, duração total acordado, tempo total de sono e se foi um dia comum. Em escala de 0 a 5: cansaço, estresse, desatenção, sonolência diurna e irritabilidade. Adicionalmente: tempo em atividade física, tempo fora de casa, uso de medicação para dormir, presença de dor, percepção geral de saúde, consumo de álcool, cochilos, consumo de café e tabagismo noturno.

*Decisão de 13/11/2026:* dormir e levantar no mesmo horário é recusado, com o erro no campo do horário de levantar, porque o tempo na cama sairia como 24 horas, o que quase sempre é horário digitado errado. Passar da meia-noite continua valendo: só a diferença zero é recusada.

*Decisão de 14/11/2026, revisão das regras R13:* tempo na cama acima de 16 horas também é recusado, com o erro no campo do horário de levantar. Os minutos de cada campo vão até 1.440, um dia inteiro, e as contagens (despertares, doses de álcool, xícaras de café, cigarros) até 200. O valor acima é recusado no próprio campo, nunca trocado por outro.

---

**RF23 — Índice de Qualidade do Sono de Pittsburgh (PSQI-BR)** · `alterado` · **E** · Paciente

Avaliação do sono do último mês. O sistema calcula o índice global conforme o **algoritmo oficial de 7 componentes** (Anexo A.2).

*Critérios de aceite:*
- O índice global é a soma de 7 componentes derivados, cada um de 0 a 3, **total de 0 a 21**
- O componente de eficiência habitual do sono é **calculado** a partir de horas dormidas ÷ horas na cama, não coletado
- Ponto de corte interpretativo: escore > 5 indica qualidade de sono ruim
- Existe teste automatizado com caso de referência publicado
- A entidade recebe o campo faltante de **frequência do item "outros motivos"**, necessário para compor o componente de distúrbios do sono
- *Decisão de 14/11/2026, revisão das regras R13:* horas de sono maiores que o tempo na cama são recusadas no campo das horas de sono, como o diário do sono já recusa mais tempo acordado do que na cama. O mesmo horário para deitar e levantar e o tempo na cama acima de 16 horas são recusados no campo da hora de levantar, e os minutos para dormir vão até 1.440. O cálculo do índice não muda: só deixa de receber a resposta impossível
- *Desde 17/11/2026* a tela mostra uma vez, em cima dos itens de a até j, a pergunta 5 do formulário (*Durante o último mês, com que frequência você teve dificuldade para dormir porque você:*), como no papel. O texto e a ordem dos itens não mudam, e o índice não usa esse título
- *Desde 20/11/2026* o paciente lê cada pergunta inteira, como o papel pergunta, com o *Durante o último mês* de cada uma, no lugar do nome curto que o sistema dava ao item (*Qualidade do sono de uma maneira geral*, por exemplo). O nome curto continua só onde é preciso um nome curto: no gráfico, nos arquivos CSV, na lista do que falta preencher e na leitura da resposta. A instrução do início e a frase *Para cada uma das questões restantes, marque a melhor (uma) resposta*, antes da pergunta 5, voltaram a ser as do papel. As chaves, as opções, a ordem dos itens e o cálculo do índice não mudam, e nenhuma resposta passou a ser obrigatória. A comparação item por item, com os erros de digitação do papel que foram corrigidos, está em [`docs/qa/migracao-das-escalas.md`](qa/migracao-das-escalas.md)
- As perguntas 7, 8 e 9 aparecem com o português corrigido, diferentes do papel de propósito: a 7 termina em *para lhe ajudar a dormir?* (no papel a frase acaba em *para lhe ajudar*), a 8 diz *com que frequência* (o papel traz *que frequência*) e as três fecham com ponto de interrogação. As opções, a ordem e o cálculo do índice não mudam (decisão da dona do projeto, novembro de 2026)

*Justificativa:* a primeira implementação somava 13 campos crus, gerando faixa de 0 a 39, incomparável a qualquer ponto de corte publicado, e o próprio código documentava a limitação em comentário. Como o valor era apresentado à prescritora rotulado como índice de qualidade do sono e podia influenciar ajuste de dose, a soma crua é inaceitável. O cálculo pelos 7 componentes está no sistema desde 12/09/2026 (Anexo A.2).

---

**RF24 — Acompanhamento semanal de paciente com TEA** · `mantido` · **I** · Paciente

Registro do período avaliado e autoavaliação da qualidade de vida (0 a 10). Frequência de comportamentos na última semana em quatro níveis — nenhum dia, até 3 dias, entre 3 e 6 dias, todos os dias — para: agressividade/impulsividade, agitação psicomotora/ansiedade, sono, interação social, estereotipia e apetite. Campo de observação opcional.

*Critérios de aceite:*
- **Possui tela de preenchimento acessível.** Existe no backend, sem interface
- *Desde 17/11/2026* a tela mostra uma vez, em cima dos seis comportamentos, a pergunta da tabela do formulário (*Durante a última semana com que frequência esses comportamentos ocorreram:*), como no papel
- *Desde 20/11/2026* a pergunta da qualidade de vida (*Em uma escala de 0 a 10, sendo 10 a melhor pontuação, como você classifica sua qualidade de vida no último período?*), a descrição de cada comportamento e a orientação das observações aparecem inteiras, como no papel. O nome curto *Qualidade de vida no período* continua no gráfico e nos arquivos CSV ([`docs/qa/migracao-das-escalas.md`](qa/migracao-das-escalas.md))

---

**RF25 — Acompanhamento semanal de paciente com dor** · `mantido` · **I** · Paciente

Período avaliado (`De __/__/__ A __/__/__`) e intensidade da dor em escala visual de 0 a 10. Frequência de interferência da dor — nenhum dia, até 3 dias, entre 3 e 6 dias, todos os dias — em: atividades básicas (comer, levantar, tomar banho), atividades sociais (sair com amigos, passear, ficar com a família), produtividade no trabalho, sono e necessidade de medicação extra para dor. Identificador único e observação opcional.

*Critérios de aceite:*
- **Possui tela de preenchimento acessível.** Existe no backend, sem interface
- Faixas da escala visual **conforme o formulário aplicado** (`docs/scales/registro-dor.pdf`): **leve 0 a 2 · moderada 3 a 7 · intensa 8 a 10**. A v1.0 especificava 1–3 leve, 4–6 moderada e 7–10 intensa, que não são as do instrumento em uso
- A escala é apresentada visualmente, com as três faixas nomeadas e coloridas, não como um campo numérico solto. *Atendido em 16/10/2026: o formulário genérico desenha a nota com as faixas da definição — Dor leve (0 a 2) em azul, Dor moderada (3 a 7) em âmbar e Dor intensa (8 a 10) em vermelho, cores que seguem o formulário impresso (no papel a faixa moderada vai do verde ao âmbar; na tela foi simplificada num âmbar só) — com o nome e o intervalo de cada faixa escritos em cima das notas dela, então a cor não é o único sinal. Vale para qualquer item de nota que vá de ponta a ponta das faixas da escala; no HAM-A as faixas são do total de 0 a 56 e o item de 0 a 4 continua com a régua simples (`ScaleForm`)*
- *Desde 20/11/2026*, no celular, as onze notas da escala visual têm todas a mesma largura e a mesma altura (44 por 48 px). Antes cada faixa esticava até a borda da linha dela, e as notas de 0 a 2 e de 8 a 10 ficavam bem mais largas que as de 3 a 7. As linhas por faixa e o nome de cada faixa em cima das notas dela continuam como eram
- Três sobras de aparência da escala visual foram acertadas (decisão da dona do projeto, novembro de 2026): em telas de 768 px a faixa que desce para a linha de baixo não estica mais até a borda, e as onze notas ficam com a mesma largura; num celular pequeno, de 320 por 568 px, o contador e os botões da escala rolam com a página, para a faixa de 8 a 10 não começar escondida atrás dos botões; e em janelas com menos de 480 px de altura, onde nada fica preso desde 20/11/2026, não havia o que acertar
- O campo de observação exibe a orientação do formulário: relato de efeito colateral, ausência de tomada de alguma medicação, momento significativo na rotina
- *Desde 20/11/2026* a pergunta da tabela do formulário (*Durante a última semana com que frequência a dor te atrapalhou:*) aparece uma vez, em cima das cinco atividades, como no papel e como a do TEA. Até então ela vinha reescrita, como ajuda de cada atividade, e não virava título para a mesma frase não aparecer duas vezes em cada item. A pergunta da intensidade (*De acordo com a escala visual abaixo, como você classifica a sua dor no último período?*), as linhas *Na produtividade do trabalho* e *No seu sono* e a orientação das observações também passaram a ser as do papel, inteiras. O nome curto de cada item continua no gráfico e nos arquivos CSV ([`docs/qa/migracao-das-escalas.md`](qa/migracao-das-escalas.md))
- Cada pergunta lembra o período: a linha *Na última semana* aparece em cima de cada uma das sete perguntas, como moldura do sistema, e o texto da pergunta continua o do formulário. A frase *Pense na última semana para responder* continua no início e a pergunta da tabela continua uma vez em cima das cinco atividades. Antes o período aparecia só no início, e a pessoa podia esquecê-lo nas últimas perguntas (decisão da dona do projeto, novembro de 2026)

---

**RF26 — Mini-Exame do Estado Mental (MEEM)** · `alterado` · **E** · Prescritor

Aplicado pelo prescritor durante a consulta. Registra nome do paciente, data da avaliação e a pontuação de cada seção. O sistema calcula o total automaticamente.

*Critérios de aceite:*
- **Pontuação máxima de 30 pontos**, com todas as seções do instrumento (Anexo A.3): orientação temporal (5), orientação espacial (5), registro (3), atenção e cálculo (5), memória de evocação (3), nomeação (2), repetição (1), comando de três etapas (3), **leitura (1)**, **escrita (1)** e **cópia dos pentágonos (1)**
- Faixa interpretativa ajustada por escolaridade
- *Decisão de 14/11/2026, revisão das regras R14:* os pontos de corte são de adultos, então o exame de quem tinha menos de 18 anos na data da consulta (pela data de nascimento do cadastro) é gravado com o escore e sem a faixa, com "Instrumento não validado para a idade" no lugar dela, e fica sem a linha do corte no gráfico de evolução. Assim o resultado não sai como "abaixo do esperado" com o nome do instrumento validado fora do que foi validado (RN13). A idade é a do dia da consulta: o exame de quem fez 18 anos depois continua sem faixa, e o de quem já tinha 18 continua com ela. *Decisão de 17/11/2026:* a faixa é decidida quando o exame é gravado e fica como foi gravada. A data de nascimento corrigida depois no perfil não recalcula a faixa, nem para tirar nem para devolver a de adulto, porque a data é digitada pelo próprio paciente e o resultado é um registro clínico do prescritor. Quando a correção muda a faixa de idade e o paciente tem Mini-Exames não anulados cuja faixa seria outra pela data nova, o aviso "Faixa de idade mudou" diz ao prescritor quantos exames foram aplicados com a data antiga e que, para mudar a faixa, ele anula o exame e aplica de novo. Nada disso aparece para o paciente, que não vê o Mini-Exame. Fica para a clínica marcar uma seção como não aplicada, ou não dar faixa em consulta remota
- Exame com seção ou escolaridade em branco não é gravado: a tela aponta o que falta e a API recusa o envio. É a exceção à RN10: como o MEEM não tem correção, um exame incompleto ficaria sem escore ou sem faixa (RN14)
- A primeira implementação contemplava 8 seções, com máximo de **27 pontos** — faltavam leitura, escrita e cópia, todas descritas no RF26 da v1.0, e um teto de 27 desloca todas as faixas de interpretação. As 11 seções, de 0 a 30, estão no sistema desde 12/09/2026 (Anexo A.3)
- Vinculado à consulta que o originou, e só depois que ela aconteceu: em consulta futura o envio é recusado
- Não é designável como tarefa ao paciente (RN09)

---

### 4.6 Módulo — Evolução e Exportação

---

**RF27 — Tela de progresso (paciente)** · `mantido` · **E** · Paciente

Seletor de período no topo (15, 30, 60 ou 90 dias, ou todo o tempo), seleção da **escala** e filtro do **atributo**. Visualização principal: gráfico de evolução do atributo selecionado (eixo Y) ao longo do tempo (eixo X, pela data).

*Critérios de aceite:*
- **Existe interface.** Hoje o endpoint existe e nenhuma tela o consome
- O seletor de escala funciona para todas as escalas pontuadas, não apenas a ficha de acompanhamento, menos o MEEM, que só o prescritor vê (RF28, decisão de 13/11/2026, §8.3)
- Períodos sem dado aparecem como lacuna, nunca como zero (RN10). Na linha das gotas do dia, o dia com uma das doses em branco mostra só a dose preenchida, com um marcador próprio, vazado, que a legenda explica, e a dica do ponto diz qual dose ficou em branco: a dose em branco nunca conta como zero, e a linha de quem toma uma dose só por dia não some (decisão de 14/11/2026, revisão das regras R12, que substitui a de 13/11/2026, em que esse dia ficava sem ponto). O dia em que as gotas foram anotadas e o item escolhido ficou em branco também mantém a gota, sem cortar a linha das gotas: só a linha do item se interrompe nesse dia
- As gotas do dia têm eixo próprio, à direita, com os números na cor da linha delas, e não dividem mais o eixo da nota de 0 a 10, em que 100 gotas achatavam a curva do sintoma (decisão de 14/11/2026, revisão das regras R13)
- Lacuna quer dizer linha interrompida: o eixo X vai pela data, proporcional ao tempo, e a linha se interrompe quando, entre duas respostas, passa mais tempo do que a frequência da escala: mais de 1 dia na ficha de acompanhamento e no diário do sono e mais de 7 dias nos acompanhamentos semanais de dor e de TEA, que falam da semana. A Hamilton e o Pittsburgh, respondidos num dia só e enviados pelo acompanhamento a cada 7, 14 ou 30 dias, e o MEEM, aplicado na consulta, não têm frequência fixa, e a linha liga uma resposta à outra. A frequência vem do catálogo das escalas, e a consulta cai no dia dela, mesmo sem resposta nesse dia (decisão de 14/11/2026, revisão das regras R3, que substitui a de 13/11/2026, em que o eixo seguia só as datas de preenchimento). O eixo vai da primeira à última resposta, e as duas sempre têm a data embaixo. A consulta de antes da primeira resposta ou de depois da última fica só na lista *Consultas no período*: no gráfico ela esticaria o eixo até o dia dela e espremeria as respostas num canto (14/11/2026, reteste da revisão das regras R3)
- O paciente também tem "todo o tempo", como o prescritor, e compara como está hoje com o começo do tratamento. A API já devolvia o período inteiro dos dados dele, e a impressão ("Todo o tratamento") e o `progress.csv` também, então o limite de 15 a 90 dias da tela não protegia nada (decisão de 14/11/2026, revisão das regras R27)
- A resposta entra no gráfico quando o período dela tem ao menos um dia dentro do período escolhido. Nas escalas de um dia só nada muda. Os acompanhamentos semanais de dor e de TEA, que falam de uma semana, entram quando a semana toca o período, nas duas pontas: a semana que começou antes do corte e terminou depois dele aparece, com o ponto na data em que a semana começa, e o comentário dela vem junto. A impressão (RF33) e o `progress.csv` seguem a mesma regra (decisão da dona do projeto, novembro de 2026)
- Adicionar uma escala nova não exige alterar o serviço de progresso (RNF08)
- O gráfico diz o que o 0 e o máximo significam e qual lado é melhor, com as âncoras do formulário da clínica (RF20). Em ansiedade o 10 é "tranquilo" e em dor o 10 é "dor intensa", então sem isso a curva subindo pode ser lida ao contrário. *Atendido em 16/10/2026: o catálogo de atributos traz as âncoras e o `higherIsBetter` do item, e a tela mostra, abaixo do nome do atributo, por exemplo "0 = muito ansioso · 10 = tranquilo · Maior é melhor". No escore, as pontas são a primeira e a última faixa do instrumento. Contagem e minutos não têm âncora no papel e ficam sem lado melhor, em vez de um inventado*
- Escore de escala validada no gráfico vem com a faixa (RN14). *Atendido em 16/10/2026: cada ponto do escore traz a faixa gravada na resposta, que aparece ao lado do valor no ponto do gráfico e no último registro. O MEEM, cuja faixa depende da escolaridade, traz também o ponto de corte da escolaridade de cada exame: quando é o mesmo no período inteiro, vira uma linha de corte no gráfico; quando há dois ou mais pontos de corte diferentes, a tela avisa que a escolaridade mudou e a faixa fica em cada ponto. Exame antigo sem escolaridade registrada vem sem corte e sem faixa e é contado à parte, sem ser lido como mudança de escolaridade: a tela avisa "Exame sem escolaridade registrada: sem faixa", e o ponto e o último registro mostram esse texto ao lado do escore. O MEEM de quem tinha menos de 18 anos no dia da consulta também vem sem corte, mas com "Instrumento não validado para a idade" no lugar da faixa, e não é contado como exame sem escolaridade (decisão de 14/11/2026, revisão das regras R14). Desde 13/11/2026 essa leitura do MEEM vale só no progresso do prescritor (RF28)*
- O progresso do paciente não oferece o MEEM: o catálogo de atributos do paciente vem sem o escore do exame, e a API recusa com **403** a série dele pedida pelo paciente, inclusive no `progress.csv`. O resultado do exame é conversado na consulta (RN09, decisão de 13/11/2026, §8.3)
- Embaixo do gráfico, *Ver como tabela* mostra os mesmos pontos numa tabela: a data, o valor com a faixa e, quando o gráfico mostra as gotas, as gotas do dia. É a descrição detalhada do gráfico para quem usa leitor de tela, que no celular não tem as setas do teclado, e para quem quer o número exato de cada dia. Cada preenchimento é um ponto e uma linha: dois MEEM no mesmo dia, um em cada consulta, aparecem os dois. A tabela vem fechada, usa só os dados que o gráfico já carregou e escreve cada dia como a dica do ponto: o que ficou em branco aparece como *em branco*, nunca como zero (RN10), a resposta anulada não entra, como no gráfico, e o MEEM continua só no progresso do prescritor (20/11/2026)

---

**RF28 — Tela de progresso (prescritor)** · `mantido` · **E** · Prescritor

Mesma estrutura do RF27, para o paciente selecionado, com os mesmos períodos, inclusive "todo o tempo". Restrito a pacientes vinculados (RF30). As âncoras, o lado melhor e a faixa de cada escore do RF27 valem igual aqui. Só aqui aparece o MEEM, com a faixa e o ponto de corte da escolaridade de cada exame (decisão de 13/11/2026, §8.3), menos no exame de quem tinha menos de 18 anos no dia da consulta, que vem sem corte e com "Instrumento não validado para a idade" no lugar da faixa (decisão de 14/11/2026, revisão das regras R14).

---

**RF33 — Exportação de dados** · `novo (consolida RF16 e RF17)` · **I** · Paciente, Prescritor

Exportação de histórico clínico, escalas respondidas e séries de evolução em **PDF** (leitura e impressão) e **CSV** (análise).

*Critérios de aceite:*
- O paciente exporta apenas os próprios dados, para levar a outras consultas
- No arquivo, na impressão e nas telas do paciente, a consulta e a prescrição anuladas aparecem só com a data, a marca de anulada e o motivo, sem o conteúdo clínico, que pode ser de outra pessoa lançada no paciente errado. O prescritor continua vendo tudo (decisão de 14/11/2026, revisão das regras R7, §8.6)
- O arquivo e a impressão do paciente saem sem o MEEM. O prontuário completo, com o exame, a clínica entrega a pedido do paciente ou do responsável, com o prescritor exportando ou imprimindo (LGPD, art. 18; decisão de 13/11/2026, §8.3)
- O prescritor exporta dados dos seus pacientes
- Existe modo **sem identificação** para fins de pesquisa, sem nome, CPF, e-mail, telefone e endereço
- O PDF identifica paciente, prescritor, período e data de emissão
- A impressão traz a seção **Prescrição em uso** com a prescrição vigente, independentemente do período escolhido: a receita em uso é estado, não histórico, e a emitida antes do período continua valendo (07/11/2026). Do paciente arquivado a seção diz **Última prescrição, acompanhamento encerrado em dd/mm/aaaa** (decisão de 14/11/2026, revisão das regras R2, §8.6)
- A impressão traz também a seção **Anamnese mais recente**, com a última ficha não anulada, independentemente do período escolhido e com a data de preenchimento à vista (decisão de 14/11/2026, revisão das regras R10)
- Na impressão, a escala respondida entra no período quando ao menos um dia do período dela cai dentro dele: a semana de dor ou de TEA que começou até seis dias antes do corte e terminou dentro do período sai no papel, como no gráfico de progresso (RF27; decisão da dona do projeto, novembro de 2026)
- O CSV de consultas segue a mesma regra do histórico (atendidas, marcadas que já passaram e faltas), e o de prescrições traz a receita inteira, com prescritor, plano de dose, instruções, precauções e efeitos esperados (07/11/2026)
- O `progress.csv` diz em cada linha o que significam o mínimo e o máximo e se o maior é melhor, como o gráfico (RF27), e todos os CSV trazem o texto da tela em situação, modalidade, espectro e atributo, com o código na coluna ao lado, e número quebrado com vírgula decimal (decisão de 14/11/2026, revisão das regras R16)

*Origem:* consolida os RF16 e RF17 da v1.0 e atende as necessidades "exportação de dados para pesquisa" e "padronização para estudos científicos" da tabela de §1.5, além do RNF09.

---

### 4.7 Módulo — Notificações e Lembretes

---

**RF14 — Notificações (paciente)** · `mantido` · **I** · Paciente

Notificação sobre compromissos agendados, alertas de consulta e formulários pendentes, configurável conforme preferência (RF18).

*Critérios de aceite:*
- Todo link de notificação aponta para rota existente. Hoje há links para rotas inexistentes
- O paciente vê apenas as próprias notificações
- A prescrição nova que passa a valer e a anulação da prescrição em uso avisam o paciente dentro do sistema, com link para Minhas prescrições; a troca avisa uma vez só, da nova (07/11/2026)

---

**RF15 — Notificações (prescritor)** · `ampliado` · **I** · Prescritor

Notificação sobre compromissos agendados, novos pacientes vinculados, **formulários preenchidos** e alertas clínicos.

*Critérios de aceite:*
- A conclusão de uma escala por paciente notifica o prescritor. Hoje a baixa da tarefa é silenciosa
- Escala pendente vencida sinaliza no dashboard (RF32)

---

**RF34 — Lembretes automáticos** · `novo` · **I** · Paciente

Lembretes automáticos de **horário de dose**, de **consulta próxima** e de **formulário pendente**, nos canais habilitados em RF18.

*Critérios de aceite:*
- O paciente configura os horários de dose conforme a posologia prescrita
- Lembrete de consulta enviado com antecedência configurável. *Não entregue nesta versão (§8.3, 21/11/2026): a antecedência é fixa, e o lembrete sai na véspera*
- Lembrete de formulário pendente respeita a periodicidade do protocolo (RF32), sem duplicar
- *Decisão de 14/11/2026, revisão das regras R6:* na ficha diária e no diário do sono, dois dias seguidos em branco geram um lembrete, um só por sequência de dias em branco, no sistema e por e-mail quando a preferência do paciente deixa, como os outros lembretes
- *Acrescentado em 20/11/2026:* o link do e-mail de lembrete de formulário leva ao painel do paciente, um endereço sem o número dele; o aviso dentro do sistema continua levando à central de escalas ou à tela da escala. Um teste automático passa pelo assunto e pelo texto de todos os e-mails que o sistema monta e falha se achar o nome de uma escala ou uma palavra do tratamento (ansiedade, sono, dor, cannabis, canabidiol). É um alarme, não uma prova de que o e-mail não diz nada clínico

*Justificativa:* foi a necessidade mais citada pelos pacientes na elicitação (v1.0 §1.5 e Tabela 2): *"Pacientes relataram dificuldade para lembrar de tomar as doses"*. A v1.0 menciona lembretes em RF10 e RF14, mas nunca como requisito próprio, e nada foi implementado.

---

## 5. Requisitos Não Funcionais

---

**RNF01 — Usabilidade** · `mantido` · **E**
Interface simples e de navegação fácil, adequada a usuários com diferentes níveis de familiaridade com tecnologia.
*Decisão de 13/11/2026:* o envio recusado por um campo leva o foco ao primeiro campo com erro, que já traz a mensagem ligada a ele, então o leitor de tela lê o campo e o motivo juntos. Vale para todos os modais com formulário, inclusive a senha nova e a correção de e-mail do prescritor pela administração, e para o perfil, o cadastro, a tela de senha nova e a tela do termo.
*Desde 13/11/2026* o campo que recebe o foco do teclado não fica atrás da barra de ações presa no pé dos formulários nem do menu de baixo do celular: a página sobe só o bastante para mostrá-lo inteiro, também quando ele já estava dentro da janela.
*Desde 17/11/2026* o formulário das escalas mostra, preso no alto, quantos itens a pessoa já respondeu (*7 de 19 respondidas*), sem cobrir o campo com foco e sem anunciar ao leitor de tela cada letra digitada. Salvar com item obrigatório em branco leva o foco ao primeiro item que falta, do mesmo jeito que o erro de campo, na escala do paciente e no Mini-Exame. Depois de salvar, a escala do paciente mostra um painel de pronto com o que foi salvo e, só quando o sistema sabe, o que vem depois (no diário com período aberto, que amanhã tem mais um dia, ou que hoje é o último), sem inventar data.
*Desde 20/11/2026* a borda do que a pessoa precisa achar para preencher (campo de texto, seletor, cartão de opção, bolinha e caixa de marcar vazias, nota das réguas) e as linhas das faixas no gráfico do progresso têm contraste de pelo menos 3:1 com o fundo, nos temas claro e escuro, também sobre as três faixas coloridas da escala visual de dor e no campo travado. Antes era 1,3:1. O contorno dos cartões e a grade do gráfico continuam claros. O campo recusado não depende só da cor: a borda fica dobrada, além do halo e da mensagem.
*Desde 20/11/2026* cada tela tem título próprio na aba do navegador, com um nome fixo e o nome do sistema (*Agenda · 2AG*), inclusive as telas de entrada, de cadastro, de senha e de confirmação de e-mail e as telas de erro. O título fica nas abas e no histórico de um computador que pode ser de mais gente, então nunca leva nome de paciente, nome de escala, diagnóstico ou data: a tela de uma escala é só *Escala · 2AG*. Enquanto há na tela um campo marcado com erro por um envio recusado, o título começa com *Erro:*, e o prefixo sai no envio seguinte aceito, ao trocar de tela e ao fechar o modal do formulário.
*Decisão da dona do projeto em novembro de 2026:* o aviso geral de erro sem campo marcado, como a senha errada na entrada, a falta de conexão ou a tela que não carregou, também põe o *Erro:* no título da aba, e o prefixo sai quando o aviso sai da tela. A nota neutra e o aviso de sucesso não mudam o título.
*Decisão da dona do projeto, novembro/2026:* na pergunta de confirmação em que o sim faz perder algo (arquivar paciente, anular registro, desativar conta, marcar falta, cancelar consulta, encerrar acompanhamento e sair de um formulário sem salvar), o botão que confirma é terracota cheio com texto branco, e não o verde do botão principal, que passa a ideia de caminho seguro. O contraste do texto é de pelo menos 4,5:1 e o do botão com o fundo em volta de pelo menos 3:1, nos temas claro e escuro. O botão de voltar continua de contorno e vem antes, e as perguntas que não fazem perder nada (reativar conta e trocar pedido) continuam com o botão verde.
*Decisão da dona do projeto em novembro de 2026:* a tela que não carrega e a tela que a conta não pode abrir mostram um cartão único dentro da tela, com ícone, título curto, a mensagem e um botão: **Tentar de novo** na falha ao carregar e, na falta de acesso, o cadeado e **Ir para o início**. Antes a mesma falha tinha três aparências, e nas listas e nos painéis era uma frase solta, sem saída para quem não pensa em recarregar a página. A página inteira de aviso fica só para quando o sistema não abre. Sem internet, o **Tentar de novo** não troca o 2AG pela página de erro do navegador.

**RNF02 — Responsividade** · `mantido` · **E**
Telas responsivas em dispositivos móveis e desktop. *Prioridade de projeto no smartphone*: a elicitação registrou preferência unânime dos pacientes por celular.
*Desde 17/11/2026* a escala do paciente, no celular (abaixo de 768 px), mostra uma pergunta por tela, com **Anterior** e **Próxima**, e o salvar só na última. A **Próxima** nunca obriga a responder (RN10), e o salvar lista o que falta como antes. A partir de 768 px a escala continua toda numa página, e o Mini-Exame, aplicado pelo prescritor, também. No celular, o cartão das datas e o aviso "Responda todos os itens antes de salvar" ficam na última tela, junto do **Salvar respostas**, e a instrução da escala continua no alto; antes os dois vinham em cima e a primeira pergunta abria atrás da barra dos botões. Trocar a data na última tela mantém as respostas já dadas. No computador as datas e o aviso continuam antes da primeira pergunta (decisão da dona do projeto, novembro de 2026). Cada opção de escolha é um cartão inteiro para tocar, com pelo menos 44 px de altura, e a escolhida mostra um check e a borda dobrada, além da cor; *Sim* e *Não* ficam lado a lado. A régua de notas continua régua, com notas de pelo menos 44 px no celular e sem rolar a página de lado. *Desde 20/11/2026* os cartões de uma mesma pergunta têm todos a mesma altura, a do mais alto, para a opção de texto comprido não parecer mais importante nem ser mais fácil de acertar; a altura vale dentro de cada pergunta, nunca de uma para outra. O texto de apoio embaixo da pergunta tem o tamanho do texto da página, porque em várias escalas ele é texto do próprio instrumento. Só no Mini-Exame, que é lido pelo prescritor, o texto de apoio fica na letra menor, de 14 px (decisão da dona do projeto, novembro de 2026).
*Desde 20/11/2026* numa janela baixa, com menos de 480 px de altura, como o celular deitado ou o computador com zoom de 400%, nada fica preso na tela: o cabeçalho, o contador das escalas e os botões do pé dos formulários rolam com a página, e o menu de baixo do celular vai para o fim dela. Antes, numa janela de 320 por 256 px (1280 por 1024 com zoom de 400%), essas barras cobriam de 249 a 256 dos 256 px de altura numa escala, e no celular deitado (667 por 375 px), 258 dos 375. Numa janela mais alta nada mudou. Na mesma janela baixa, o aviso *Sua sessão venceu*, que abre por cima da tela, rola quando não cabe: antes, em 320 por 256 px, o título e os botões *Sair* e *Entrar* ficavam fora da janela e não havia como chegar neles.
*Desde 27/11/2026* o menu de baixo do paciente no celular tem *Início*, *Escalas*, *Progresso*, *Consultas* e *Avisos*: as escalas, que são a tarefa de todo dia, entraram entre o *Início* e o *Progresso* e levam à lista de avaliações que o *Início* já abria. Para caberem cinco itens, o *Perfil* saiu da barra e virou um botão com o nome escrito no cabeçalho do celular, ao lado do tema e do *Sair*, com pelo menos 44 px de toque. Os cinco itens e o cabeçalho cabem em 320, 360 e 375 px com a letra em 200%. O menu lateral do computador e as barras do prescritor e da administração não mudaram (decisão da dona do projeto, novembro de 2026).
*Decisão da dona do projeto, novembro/2026:* no celular, a janela que abre por cima da tela (a pergunta de confirmação, a anulação, o convite, a *Nova consulta*) é um cartão de cantos arredondados com uma folga dos lados, igual ao aviso *Sua sessão venceu*. Antes era uma faixa quadrada de lado a lado. O teclado continua preso dentro da janela, o Escape fecha, e o conteúdo que não cabe na altura rola dentro dela.
*Decisão da dona do projeto, novembro/2026:* no celular, os botões do topo de cada tela esticam e preenchem a linha em que estão, como os botões do pé dos formulários, em vez de quebrar com larguras desiguais. Os atalhos secundários, como *Indicadores*, continuam do tamanho do próprio texto.

**RNF03 — Controle de acesso** · `ampliado` · **E**
Controle de acesso por perfil **e por vínculo**, aplicado no servidor. Detalhado em RF29 e RF30, com critérios verificáveis.
*Critério de aceite:* nenhuma rota protegida apenas por autenticação; cobertura de teste automatizado para as regras de isolamento.

**RNF04 — LGPD** · `ampliado` · **E**
Conformidade no tratamento de dado pessoal sensível de saúde (art. 11).
*Critérios de aceite:*
- Acesso a dado clínico restrito por vínculo (RF30)
- Trilha de auditoria de acesso e alteração (RF31)
- Exportação sem identificação disponível para uso científico (RF33). Não é anonimização: a clínica sabe de quem é cada número, então o arquivo continua sendo dado pessoal sensível de saúde (LGPD, art. 5º, II, e art. 12)
- Nenhuma resposta da API expõe hash de senha ou credencial
- Nenhum dado pessoal em parâmetro de URL

**RNF05 — Criptografia** · `ampliado` · **E**
Toda conexão externa por HTTPS/TLS. Senha armazenada apenas como hash BCrypt. O backup do banco sai cifrado (age) antes de deixar o servidor, e o e-mail só sai com STARTTLS.
*Critério de aceite:* o sistema só abre por https: o nginx termina o TLS com o certificado da clínica, a porta 80 redireciona para https, o HSTS sai com `includeSubDomains` e o cookie de sessão sai com `Secure`; não há URL `http://` fixa no código e a origem da API é configurável por ambiente. Atendido em 13/10/2026 (#57).
*Decisão registrada (10/10/2026):* o dado clínico **não é cifrado por campo** dentro do banco. O banco não publica porta e só a api chega nele; cifrar campo a campo tiraria a busca e a ordenação do PostgreSQL, espalharia chave pela api e não protegeria de quem já tem a api na mão, que é quem lê o dado de qualquer jeito. O que sai do servidor é o backup, e esse vai cifrado. A decisão volta a ser discutida se o banco for para host compartilhado ou nuvem de terceiro.

**RNF06 — Tempo de resposta** · `ampliado` · **I**
Funcionalidades principais respondem em menos de 3 segundos em rede estável.
*Critério de aceite:* toda listagem que cresce com o uso é **paginada**. Nenhum endpoint retorna coleção não limitada.

**RNF07 — Backup** · `mantido` · **E**
Backup automático diário, com procedimento de restauração documentado e testado.
*Critério de aceite:* existe registro de um teste de restauração bem-sucedido.

**RNF08 — Extensibilidade** · `ampliado` · **I**
Incluir nova escala clínica, campo de formulário ou ajuste de fluxo não exige reescrever partes centrais.
*Critério de aceite:* acrescentar uma escala pontuada não exige alteração no serviço de progresso (RF27) nem na central de escalas (RF08). Os metadados da escala — nome de exibição, rota, atributos monitoráveis — vivem em um único lugar.

**RNF09 — Interoperabilidade** · `alterado` · **I**
Exportação em formatos abertos, PDF e CSV (RF33). *Integração direta com o prontuário da clínica fora de escopo (§3.1).*

**RNF10 — Integração com comunicação** · `descartado` · —
Ver §3.1. Substituído por notificação in-app e e-mail.

**RNF11 — Requisitos de desenvolvimento** · `mantido` · **E**
Java (backend), HTML/JavaScript (frontend), PostgreSQL (banco).

---

**RNF12 — Internacionalização preparada** · `novo` · **I**

A arquitetura suporta múltiplos idiomas; apenas `pt-BR` é traduzido nesta entrega (§3.2).

*Critérios de aceite:*
- Nenhuma string de interface embutida no código; todo texto vem de arquivo de dicionário
- O idioma é resolvido pela **preferência do usuário** (RF18), nunca pela locale do servidor
- Arquivos de mensagem em **UTF-8**, com teste que verifica a codificação. A implementação atual tem arquivo de mensagens fora de UTF-8, num ambiente cuja codificação de plataforma é Cp1252
- Acrescentar idioma não requer alteração de código

---

**RNF13 — Testabilidade** · `novo` · **E**

O projeto possui suíte de testes automatizados executável sem dependência de ambiente externo.

*Critérios de aceite:*
- `mvn clean install` passa em máquina limpa, **sem banco de dados instalado**. Hoje o comando documentado no README falha sempre, porque o único teste sobe o contexto Spring completo e exige PostgreSQL com credencial válida
- Cobertura obrigatória: regras de autorização (RF29, RF30) e cálculo de escore das escalas (RF21, RF23, RF26)
- Perfil de teste isolado, com banco em memória ou container efêmero

---

**RNF14 — Versionamento de esquema** · `novo` · **E**

O esquema do banco é versionado em migrações rastreadas, fonte única de verdade.

*Critérios de aceite:*
- Nenhuma geração automática de esquema em produção; o esquema é **validado** contra as migrações, não atualizado por inferência
- Uma fonte única de verdade. Hoje coexistem os scripts SQL de `database/physical-model/` e a geração automática pelas entidades, já divergentes entre si
- Toda alteração de esquema é uma migração nova, nunca a edição de uma anterior

---

**RNF15 — Configuração por ambiente** · `novo` · **E**

Nenhum segredo ou endereço de ambiente no código versionado.

*Critérios de aceite:*
- Senha de banco e chave de assinatura de token vêm de variável de ambiente. Hoje ambas estão em arquivo versionado
- A origem da API no frontend é configurável. Hoje há 29 ocorrências fixas de `localhost:8080` em 15 arquivos
- Dado de demonstração e usuário de teste existem **somente** no profile de desenvolvimento. Hoje uma conta de prescritor com senha `123456` é criada a cada inicialização, sem distinção de ambiente

---

## 6. Regras de Negócio

| ID | Regra |
|---|---|
| **RN01** | O acompanhamento clínico tem duração padrão de **90 dias**, renovável, contados do início do tratamento |
| **RN02** | A conduta terapêutica segue escalonamento gradual de dose (*start low, go slow*): toda prescrição inicia na menor dose eficaz e é ajustada conforme resposta clínica. *Desde 14/11/2026 o plano de dose vai da semana 1 em diante sem pular semana, e a semana 1 é a primeira semana de uso (decisão de 14/11/2026, revisão das regras R23, §8.6)* |
| **RN03** | Formulações possíveis: **isolado** (CBD, THC, CBG), **broad spectrum** (sem THC) e **full spectrum**. A concentração de cada canabinoide é definida **caso a caso**, porque a resposta varia por paciente e por condição tratada — não existe formulação padrão. *Desde 13/09/2026 o espectro é um campo próprio e o óleo é descrito por uma lista de canabinoides, cada um com a sua concentração e unidade, mg/mL ou % (`PrescriptionComponent`); desde 02/10/2026 a tela abre em mg/mL, que é como a clínica cadastra hoje. Segue em aberto se o mg/mL é de cada canabinoide ou do produto: está nas perguntas para a clínica. Desde 30/10/2026 a prescrição recusa isolado com mais de um canabinoide e broad spectrum com THC (RF05)* |
| **RN04** | E-mail e CPF são únicos em todo o sistema. *Atendido em 12/09/2026: o CPF é único na entidade (`Users`) e na migração base (`V1__esquema_base.sql`), e o cadastro confere e-mail e CPF antes de gravar, recusando com uma mensagem só, sem dizer qual dos dois já tem conta (#82)* |
| **RN05** | Cada paciente está vinculado a exatamente um prescritor. O vínculo é estabelecido no cadastro, pelo convite do prescritor (RN06), e hoje é fixo: não existe tela nem rota que o altere, nem para a administração, então não há transferência de pacientes entre prescritores e um prescritor novo não vê os pacientes de outro (RF30). *Reescrita (decisão de 14/11/2026, revisão das regras R17): o texto anterior dizia que o administrador podia alterar o vínculo, o que nunca existiu. A transferência de um paciente por vez, feita pela administração com registro na trilha e sem dar a ela acesso a dado clínico, fica para antes de entrar um segundo prescritor* |
| **RN06** | O código de vínculo do prescritor é único e **não é adivinhável**. *Alterado em 13/09/2026 (§8.3): o código de três letras e dois dígitos, que oferecia 90 combinações por prefixo, saiu do banco (`V3__remove_codigo_do_prescritor.sql`), e o vínculo passou a ser feito por convite de uso único, com 32 bytes sorteados e validade de 7 dias (`PatientInviteService`, `SecureTokens`). Desde 02/10/2026 o prescritor também lista e cancela os convites em aberto* |
| **RN07** | O prescritor não comercializa produtos. O sistema registra orientação quanto a marca, lote e concentração, sem função de venda |
| **RN08** | Dois agendamentos do mesmo prescritor não podem ocupar o mesmo horário |
| **RN09** | Escalas de **autoaplicação** (ficha de acompanhamento, HAM-A, PSQI, diário de sono, registro de dor, registro TEA, anamnese) são designáveis ao paciente. Escalas de **heteroaplicação** (MEEM) são aplicadas pelo prescritor em consulta e não geram tarefa ao paciente, que também não vê o resultado: ele é conversado na consulta. *Atendido em 12/09/2026: o MEEM é marcado como aplicado pelo prescritor (`ScaleType`), o envio avulso e o acompanhamento de 90 dias recusam a escala (`ScaleTaskService`, `TreatmentProtocolService`) e o check `ck_scale_task_type` da `V13__escalas.sql` não aceita tarefa de MEEM.* *Alterado em 13/11/2026 (§8.3): o paciente não vê o MEEM em lugar nenhum, nem chamando a API direto, e só o prescritor do paciente vê o exame. A regra fica em `ScaleType.isHiddenFrom`, e o prontuário completo, com o exame, a clínica entrega a pedido do paciente ou do responsável (LGPD, art. 18)* |
| **RN10** | Item de escala não respondido é registrado como **ausente**, nunca como zero. *Atendido desde 12/09/2026: item em branco não entra nas respostas gravadas (`ScaleResponseService`), escala pontuada sem todos os itens fica sem escore (`ScaleScorer`) e o gráfico de evolução deixa lacuna onde falta o item (`ProgressReportService`). A exceção é o MEEM, que incompleto não é gravado (RF26)* |
| **RN11** | Campo de texto clínico livre (observação, plano terapêutico, evolução, diagnóstico, campos descritivos da anamnese) não tem limite prático de tamanho. *Atendido em 12/09/2026: esses campos são `TEXT` na migração base e nas entidades, e os testes gravam 10.000 caracteres na evolução da consulta e numa resposta da anamnese sem truncar (`ConsultationRecordTest`, `AnamnesisTest`)* |
| **RN12** | Senha nunca é gravada em texto puro nem transita em resposta da API. *Atendido desde 12/09/2026: a atualização de perfil não recebe senha (`ProfileUpdateDTO`), toda senha gravada passa pelo `passwordEncoder` (cadastro, troca no perfil, link de senha nova ou de primeiro acesso e conta do administrador), e o hash não sai em resposta nenhuma, com `@JsonIgnore` em `Users.getPassword` e o `PasswordExposureTest` conferindo* |
| **RN13** | Escore de escala validada é calculado exclusivamente pelo algoritmo oficial do instrumento (Anexo A). Adaptação ou simplificação de algoritmo não é permitida: escore fora do instrumento não pode ser exibido com o nome do instrumento |
| **RN14** | O escore é sempre apresentado com sua **faixa interpretativa**, nunca como número isolado. *No gráfico de evolução desde 16/10/2026: cada ponto de escore traz a faixa gravada, e o MEEM traz o ponto de corte da escolaridade de cada exame (`ProgressReportService`), menos o de quem tinha menos de 18 anos no dia da consulta, que vem sem corte e com "Instrumento não validado para a idade" no lugar da faixa (decisão de 14/11/2026, revisão das regras R14). A série exportada em `progress.csv` ganhou a coluna "Faixa", preenchida nos escores do HAM-A, do PSQI, do MEEM e da intensidade da dor, inclusive no arquivo sem identificação* |
| **RN15** | O paciente é identificado pelo **nome social** em toda a interface, nos formulários e nos relatórios. O nome de registro civil é armazenado apenas onde houver exigência legal. *Atendido em 16/10/2026: nome social opcional no cadastro e no perfil do paciente (`Patient.socialName`, `V27__nome_social_do_paciente.sql`), mostrado no lugar do nome civil nas telas, nos avisos e nos e-mails. O nome civil aparece junto só no prontuário, na impressão, na exportação com identificação e na tela de contas da administração (#109)* |

---

## 7. Rastreabilidade v1.0 → v2.0

| v1.0 | Status | v2.0 |
|---|---|---|
| RF01 Login | mantido | RF01, com critérios de segurança |
| RF02.1 Cadastro paciente | mantido | RF02.1 |
| RF02.2 Cadastro prescritor | **alterado** | RF02.2 — por seed/convite, sem endpoint público |
| RF03 Dashboard | mantido | RF03 — consultas futuras reais |
| RF04 Consulta clínica | mantido | RF04 — modalidade e status fechados, texto longo |
| RF05 Prescrição | **ampliado** | RF05 — versionamento obrigatório |
| RF06 Acompanhamento paciente | mantido | RF06/RF20 |
| RF07 Acompanhamento prescritor | mantido | RF07 |
| RF08 Central de escalas | **ampliado** | RF08 — resultado real, toda escala com tela |
| RF09 Designação de escalas | mantido | RF09 |
| RF10 Agendamento paciente | mantido | RF10 |
| RF11 Agenda prescritor | mantido | RF11 |
| RF12 Histórico paciente | mantido | RF12 |
| RF13 Histórico prescritor | mantido | RF13 |
| RF14 Notificações paciente | mantido | RF14 |
| RF15 Notificações prescritor | **ampliado** | RF15 — notifica formulário preenchido |
| RF16 Relatórios paciente | **descartado** | consolidado em RF33 |
| RF17 Relatórios prescritor | **descartado** | consolidado em RF33 |
| RF18 Configurações | **ampliado** | RF18 — idioma e preferências |
| RF19 Anamnese | mantido | RF19 |
| RF20 Ficha de acompanhamento | mantido | RF20 |
| RF21 HAM-A | **alterado** | RF21 — 14 itens, 0 a 56 |
| RF22 Diário de sono | mantido | RF22 |
| RF23 PSQI | **alterado** | RF23 — 7 componentes, 0 a 21 |
| RF24 TEA | mantido | RF24 — exige interface |
| RF25 Dor | mantido | RF25 — exige interface |
| RF26 MEEM | **alterado** | RF26 — 30 pontos |
| RF27 Progresso paciente | mantido | RF27 — exige interface |
| RF28 Progresso prescritor | mantido | RF28 — exige interface |
| — | **novo** | RF29 Controle de acesso por perfil |
| — | **novo** | RF30 Isolamento por vínculo |
| — | **novo** | RF31 Trilha de auditoria |
| — | **novo** | RF32 Ciclo automático de 90 dias |
| — | **novo** | RF33 Exportação de dados |
| — | **novo** | RF34 Lembretes automáticos |
| — | **novo** | RF39 Indicadores do piloto |
| RNF10 WhatsApp | **descartado** | §3.1 |
| Multilíngue pt/en/es | **rebaixado** | RNF12 — arquitetura preparada, só pt-BR |
| — | **novo** | RNF13 Testabilidade |
| — | **novo** | RNF14 Versionamento de esquema |
| — | **novo** | RNF15 Configuração por ambiente |

---

## 8. Revisão técnica de 13/09/2026

### 8.1 Motivo

Uma auditoria que executou o sistema, e não apenas leu o código, mostrou que parte dos registros de conclusão do Anexo B não se sustenta em uso real. Os testes passavam porque rodavam dentro de transação, o que escondia as falhas abaixo:

- o job diário do acompanhamento de 90 dias (RF32) falha com `LazyInitializationException` e não designa nenhuma escala;
- a leitura de escala por id responde 500, porque a entidade JPA é serializada com o paciente ainda não carregado;
- a edição de escala aceita o paciente no corpo e permite mover o registro para o prontuário de outra pessoa;
- qualquer usuário autenticado lê qualquer prescrição por id, e qualquer prescritor lista as prescrições do sistema inteiro;
- rota inexistente e parâmetro inválido respondem 500;
- a ficha de acompanhamento semanal apresenta 11 dos 15 itens com as âncoras invertidas em relação ao formulário da clínica;
- a tela de consulta clínica nunca abre, o que impede consulta, prescrição e MEEM pelo frontend.

Os registros do Anexo B ficam como histórico. O andamento passa a seguir a ordem de trabalho do §8.6.

### 8.2 Decisões de arquitetura

| Decisão | Descrição |
|---|---|
| Motor único de escalas | Uma tabela de respostas (paciente, tipo, período, respostas, escore e faixa), uma classe de cálculo por escala e a definição de cada escala (itens, âncoras e faixas) em um único lugar, consumida por um formulário genérico no frontend |
| Migração base | As migrações V1 a V8 são consolidadas em uma única migração base, com restrições de nulidade e índices. A consolidação só é possível porque o sistema nunca foi implantado |
| Mesma origem | Um único servidor web entrega o frontend e repassa `/api` para o backend, eliminando CORS e deixando um único ponto de HTTPS |
| Sessão | Token em cookie `httpOnly`, renovado durante o uso, com bloqueio temporário após tentativas de login incorretas |
| Padrão de backend | DTO em toda entrada e saída, serviços transacionais, tratamento de erro completo e fuso `America/Sao_Paulo` |
| Padrão de frontend | Layout por perfil, rotas protegidas em um único lugar, carregamento de cada tela sob demanda e CSS com escopo por componente |
| Integração contínua | Testes do backend (H2 e Postgres), lint, testes e build do frontend, build das imagens e a pilha do compose subindo de ponta a ponta com login e um POST autenticado, a cada envio ao repositório |

### 8.3 Requisitos alterados

| Requisito | Alteração |
|---|---|
| RF02.2 | Conta administrativa mínima criada por variável de ambiente na primeira inicialização, responsável por criar e desativar prescritores. *Acrescentado em 02/10/2026:* o prescritor cria a própria senha por link de primeiro acesso, e o administrador desativa e reativa conta de paciente achando-a pelo e-mail, sem lista geral e sem ver prontuário. *Acrescentado em 16/10/2026:* pedir para a conta o estado que ela já tem é recusado, porque a resposta traz o nome e o e-mail e o número do paciente na trilha virava uma pessoa sem a ação ficar registrada. *Acrescentado em 07/11/2026:* data de nascimento, telefone e endereço do prescritor são opcionais no cadastro feito pela administração e ele completa esses dados no perfil; a administração corrige registro profissional, profissão e CPF digitados errado, com a alteração na trilha. *Acrescentado em novembro de 2026 (decisão da dona do projeto):* desativar ou reativar uma conta, de prescritor ou de paciente, cancela os links de senha nova e os pedidos de troca de e-mail que estavam pendentes nela, como a troca de senha já faz, para a conta reativada depois de uma invasão não trazer de volta o link que foi para o invasor. Só a conta do e-mail configurado no servidor administra: quando esse e-mail muda, a subida seguinte desativa a conta administrativa antiga, encerra as sessões dela e cancela os links pendentes, e o log do servidor avisa; antes ela continuava entrando com a senha antiga sem aparecer em tela nenhuma. *Acrescentado em novembro de 2026, por decisão da dona do projeto:* nas telas de prescritores e de pacientes da administração, a linha da conta que está sendo desativada ou reativada fica ocupada até o sistema responder e seus botões não aceitam outro clique; a recusa continua aparecendo na tela, a linha e a lista passam a mostrar a conta como ela está, e uma nova busca limpa o aviso anterior. Na mesma data e pela mesma decisão, a busca de paciente que não acha conta mostra a frase num aviso neutro, com ícone de lupa, e o campo só fica marcado como erro quando o e-mail está mal escrito; a busca que acha a conta continua registrada na trilha |
| RN06 | O vínculo passa a ser feito por link ou QR de convite gerado pelo prescritor, aleatório e com validade |
| RF04 | *Critério acrescentado:* pressão, peso e altura são opcionais. A clínica não mede sinais vitais, porque a maioria das consultas é remota (01/10/2026) |
| RF05 | Nova prescrição substitui a vigente quando a consulta dela não é mais antiga que a da vigente; lançada em consulta anterior, entra só no histórico (vigente ou substituída). O histórico resulta da própria sequência, sem tabela de versões. O paciente acessa suas prescrições em tela própria |
| RF06/RF20 e RF22 | Um registro por dia, apresentado como a grade semanal do formulário em papel. O diário de sono inclui a programação de horários definida pelo prescritor |
| RF20 | *Critério acrescentado:* as âncoras de cada item são idênticas às do formulário da clínica |
| RF24/RF25 | O período avaliado é registrado com início e fim, como no formulário |
| RF07 | Consolidado com o RF28 em uma página única do paciente |
| RF10/RF11 | O prescritor cadastra a disponibilidade, o paciente solicita o horário e o prescritor confirma |
| RF10 e RN08 | *Decisão de 13/11/2026:* o paciente tem um pedido de consulta esperando resposta por vez, o pedido novo substitui o anterior, e todo pedido do paciente é para um horário com pelo menos 24 horas de antecedência. A QA achou um paciente segurando três horários do mesmo dia com o limite antigo de 3 pedidos em aberto. O pedido continua segurando o horário até o prescritor confirmar ou recusar, para ninguém perder um horário que já pediu; com um pedido por paciente, uma pessoa só não segura vários horários da agenda; e as 24 horas, as mesmas do cancelamento pelo paciente, dão ao prescritor tempo de responder. A troca é feita de uma vez: o horário novo é conferido como antes e, só se ele passar, o pedido anterior é cancelado como no cancelamento pelo paciente e o novo é gravado, então o pedido novo recusado deixa o anterior como estava. A consulta já confirmada não conta como pedido, e o prescritor continua marcando consulta direto sem antecedência mínima |
| RF11 | *Decisão de 13/11/2026:* cada período de atendimento precisa caber pelo menos a consulta mais curta, entre a primeira consulta e o retorno. A QA salvou um período menor que a consulta, a tela respondeu que os pacientes já podiam pedir consulta nele, e ele nunca gerou horário livre. A API recusa com 400 no campo do período, inclusive quando a duração enviada sobe acima de um período que já existia, e a tela mostra o mesmo erro na linha antes de enviar. O período em que só cabe a mais curta das duas continua valendo, com horários só daquele tipo. *Também em 13/11/2026:* o prescritor não marca consulta nova para paciente arquivado. A tela já não oferecia o arquivado, mas uma chamada montada à mão marcava, e o paciente recebia *Consulta marcada* com a tela dizendo que o acompanhamento tinha acabado. A API recusa como já recusa o pedido do próprio paciente, e o prescritor reativa o paciente para marcar. As consultas marcadas antes do arquivamento ficam como estão (07/11/2026), e remarcar e cancelar continuam iguais |
| RF18 | Restrito a dados pessoais, senha e preferência de e-mail, sem escolha de idioma |
| RF26 | *Critério acrescentado:* a escolaridade usada no ponto de corte e a data da avaliação são registradas |
| RF31 | Tabela de auditoria somente de inserção, gravada pelos serviços |
| RF33 | Exportação em CSV, com opção sem identificação, e página de impressão |
| RF34 | E-mail e notificação no sistema para formulário pendente e consulta. O lembrete de dose fica para fase posterior: a clínica ajusta a dose pelo WhatsApp, a partir das respostas do formulário, e a pergunta sobre lembrete segue aberta (01/10/2026). *Acrescentado em 21/11/2026:* a antecedência do lembrete de consulta é fixa, sem configuração: o job das 8h avisa quem tem consulta no dia seguinte, e a consulta marcada, confirmada ou remarcada para amanhã recebe o lembrete na hora. A antecedência configurável ficou fora desta entrega |
| RF14/RF15 | Lista de notificações com marcação de leitura, sem filtros avançados |
| RNF06 | Paginação apenas nas listagens que crescem sem limite: notificações e auditoria |
| RN09, RF08, RF27, RF28, RF30 e RF33 | *Decisão de 13/11/2026:* o paciente não vê o Mini-Exame do Estado Mental (MEEM), e o resultado é conversado na consulta. O MEEM é um teste cognitivo aplicado pelo profissional (RN09), e um resultado como "abaixo do esperado para a escolaridade", lido sozinho, sem o profissional explicar, pode assustar o paciente ou a família de um menor. Como a RN14 proíbe mostrar o escore sem a faixa, não existe meio-termo de mostrar uma versão mais branda. A regra é da API, e as telas só seguem: quando quem chama é o paciente, nenhuma rota devolve as respostas, o escore, a faixa ou o ponto de corte do MEEM — central de escalas, histórico, lista por escala, resposta aberta pelo número (com o mesmo 403 de uma resposta que não é dele, para ele não saber nem que o exame existe), formulário do exame, catálogo e série do progresso, `scales.csv`, `progress.csv` e a impressão, que usa essas mesmas rotas. O prescritor do paciente continua vendo tudo, como antes, no histórico, no progresso, na exportação e na impressão. **Isso não esconde dado do titular:** o direito de acesso ao prontuário inteiro (LGPD, art. 18) é atendido pela clínica, a pedido do paciente ou do responsável, com o prescritor exportando ou imprimindo o prontuário completo, com o MEEM. O resto não muda: só o prescritor aplica o exame, em consulta confirmada que já aconteceu, o exame conclui a consulta agendada que já passou, a anulação segue igual e a trilha é do prescritor |
| RN10 | *Critério acrescentado:* o frontend nunca pré-preenche item de escala com valor. Vale também para a anamnese: fuma e bebe álcool começam em branco, a pergunta pulada vai sem resposta em vez de "Não" e a correção mantém sem resposta o que estava sem resposta (30/10/2026) |

### 8.4 Descartado

| Item | Justificativa |
|---|---|
| RNF12 — internacionalização | Uma clínica, apenas pt-BR. A infraestrutura de tradução não se paga nesta entrega |
| Tela de dados do consultório | Não corresponde a nenhum requisito |
| Abas de objetivos e arquivos da consulta | Não correspondem a requisito, e o envio de arquivo clínico acrescenta armazenamento e obrigações de LGPD |
| Filtros avançados de notificação | Complexidade sem demanda registrada |
| Exclusão física de dado clínico | O prontuário tem guarda mínima de 20 anos (Lei 13.787/2018). Substituída por desativação |

### 8.5 Acrescentado

| ID | Requisito | Prioridade |
|---|---|---|
| RF35 | Recuperação de senha por e-mail, com link de uso único e validade curta | E |
| RF36 | Termo de consentimento (LGPD, art. 11) aceito no cadastro, com versão e data registradas. Paciente com menos de 16 anos: quem aceita é o responsável legal, em nome do paciente; com 16 e 17 anos, o paciente aceita junto com o responsável. O aceite de menor de 18 anos guarda o nome e o parentesco do responsável (LGPD, art. 14, e Código Civil; clínica, 16/10/2026) | E |
| RF37 | Consentimento para pesquisa separado do termo do cadastro, opcional e revogável, com versão e data registradas. A exportação para pesquisa só inclui quem aceitou (clínica, 01/10/2026). Fechado para menores de 18 anos até o comitê de ética definir o assentimento do menor | I |
| RF38 | Responsável ou cuidador do paciente: nome, parentesco e telefone, opcionais para adulto, fora da exportação sem identificação (clínica, 01/10/2026). Para menor de 18 anos, o nome e o parentesco do responsável legal são obrigatórios (clínica, 16/10/2026) | I |
| RF39 | Indicadores do piloto para o prescritor: pacientes no ciclo de acompanhamento e formulários respondidos no prazo (meta de 70%), só com números somados dos pacientes dele (proposta de extensão) | I |
| RNF16 | Operação: fuso horário único `America/Sao_Paulo`, verificação de saúde da API e integração contínua | E |

O RNF07 (backup automático com restauração testada) permanece essencial e é pré-requisito do piloto.

### 8.6 Ordem de trabalho

| Etapa | Conteúdo |
|---|---|
| 0 — Fundação | Migração base, configuração, tratamento de erro, sessão, conta administrativa, integração contínua, layout e rotas do frontend |
| Módulos | Requisito por requisito, na ordem deste documento: acesso e identidade, autorização, atendimento, agenda, escalas, evolução, notificações e exportação. Cada requisito é entregue completo, com backend, frontend e teste |

O RF03 (tela inicial) sai do módulo de acesso e identidade e passa para depois do módulo de escalas, porque reúne consultas, escalas e formulários que só são reescritos nesses módulos (decisão de 13/09/2026).

As questões que dependem da clínica ficam num documento de perguntas para a clínica, fora do repositório, porque ele traz dados da clínica e da prescritora. O que já foi respondido e mudou o sistema fica registrado aqui e nos cards do GitHub.

**Acesso e identidade concluído em 13/09/2026:** RF01, RN06, RF02.1, RF36, RF18 e RF35. O vínculo do paciente passou a ser por convite de uso único, o cadastro exige o aceite do termo (ainda em rascunho) e a recuperação de senha grava o link no log da API até o envio de e-mail ser configurado.

**Autorização concluída em 13/09/2026:** RF29, RF30 e RF31, com os critérios de RNF03 e RNF04 que dependem deles.

- **Perfil:** toda rota declara os perfis que podem usá-la. Um teste percorre a API inteira e falha se alguma rota não declarar.
- **Listagens:** saíram as que devolviam o sistema inteiro.
- **Consultas:** o paciente marca consulta por uma rota própria, que não aceita campo clínico.
- **Vínculo:** é conferido no servidor em toda leitura e escrita, e a edição não troca mais o dono do registro.
- **Exclusão física:** as rotas que apagavam dado clínico foram removidas.
- **Trilha de auditoria:** registra quem cria ou altera consulta, prescrição, anamnese, escala, designação e acompanhamento. Registra também quando o prescritor abre o prontuário, uma linha por visita de até 30 minutos.
- **Quem consulta a trilha:**
  - o prescritor, para os próprios pacientes;
  - o administrador, com as ações de prescritores e do sistema e o paciente identificado só pelo número. Esse número é o mesmo que a busca de conta por e-mail devolve, então, para o administrador, todo registro clínico aparece só como "Prontuário", sem dizer se foi escala, MEEM, anamnese, consulta ou prescrição (RF30, 16/10/2026). Os eventos de conta continuam com o tipo. Os de conta de prescritor trazem também o nome do prescritor dono da conta, que a administração já vê na lista de prescritores: antes, com dois prescritores, não dava para saber de quem foi a senha nova, a desativação ou a reativação (13/11/2026). Agora (decisão de 14/11/2026, revisão das regras R20) o registro clínico chega à administração também sem o número do paciente, que fica só nos eventos de conta: com o número, quem buscava um e-mail via a linha do tempo do tratamento daquela pessoa, com aberturas, criações, anulações e arquivamento, que é dado de saúde. E a busca de conta por e-mail e a abertura da lista de contas desativadas, que devolvem nome e e-mail, entram na trilha como *Acesso* à conta de paciente: a busca com o número do paciente achado, que aparece também na trilha do prontuário dele, e cada abertura da lista sem número. A busca que não acha conta não entra, porque não devolve ninguém. O cadastro e a correção de prescritor também contam algo à administração: o CPF ou o e-mail que já tem conta é recusado com "Este CPF já tem conta no sistema" ou "Este e-mail já tem conta no sistema", e quem digita o CPF ou o e-mail de um conhecido fica sabendo que ele tem conta numa clínica de cannabis medicinal. A resposta continua a mesma para qualquer conta, mas, quando a conta é de paciente, entra na trilha uma linha de *Acesso* à conta de paciente, com o número dele e o que aconteceu ("CPF de paciente informado no cadastro de prescritor", "E-mail de paciente informado na correção do prescritor"), que aparece na Auditoria da administração, sem o nome, e no histórico de acesso do prontuário dele, como a busca por e-mail. O conflito com conta de prescritor ou da administração não gera linha (decisão de 17/11/2026).
- **URLs:** nenhuma rota leva dado pessoal. Os caminhos usam só identificadores numéricos.

**Atendimento concluído em 13/09/2026:** RF04, RF05, RF19, RF12 e RF13, com o arquivamento de paciente.

- **Anulação:** consulta, prescrição e anamnese registradas por engano não são apagadas. O prescritor anula com motivo obrigatório e o registro continua no histórico, com data, motivo e autor. Uma consulta com prescrição ativa só é anulada depois da prescrição. Pelo mesmo motivo, a data da consulta com prescrição ou Mini-Exame não anulados é presa ao que eles protegem: a prescrição vigente sai da consulta mais recente e o exame fica no dia da consulta (16/10/2026). Na correção do registro, a consulta com prescrição só não muda de data quando a nova data a coloca antes ou depois de outra consulta do paciente com prescrição, e a com Mini-Exame só não muda de dia; corrigir só a hora no mesmo dia, como trocar 18:00 por 10:00, passa, e antes obrigava a anular a receita e emitir de novo (decisão de 14/11/2026, revisão das regras R26). Na remarcação pela agenda, que leva a consulta para o futuro, nada muda: com prescrição ou Mini-Exame a data não muda, porque os dois só existem em consulta que já aconteceu. Com eles a consulta também não vira falta nem cancelada, porque eles mostram que ela aconteceu: o paciente recebia o aviso de falta com a receita daquela consulta em uso (30/10/2026). *Decisão de 14/11/2026, revisão das regras R7:* o erro mais grave que a anulação corrige é o registro lançado no paciente errado, e aí o conteúdo é de outra pessoa. Por isso, do lado do paciente (telas, impressão e CSV que ele baixa), a consulta e a prescrição anuladas aparecem só com a data, a marca de anulada e o motivo, sem o conteúdo clínico: a API já não manda esse conteúdo para ele. A prescritora continua vendo tudo, e a guarda e a trilha não mudam. A anamnese e as respostas de escala, que o próprio paciente preencheu, continuam com o conteúdo na tela dele, e o MEEM ele não vê. O modal de anulação diz que o paciente lê o motivo e, na consulta e na prescrição, pede para não escrever nele o nome nem dados de outra pessoa. Fica para a clínica um tipo de anulação "lançado no paciente errado" que também esconda o motivo e tire o registro do arquivo de pesquisa.
- **Consulta (RF04):** o prescritor registra a consulta que já aconteceu ou completa a que estava agendada. Consulta futura é assunto da agenda. Data com o ano digitado errado é recusada: consulta e anamnese antes de 2000 e data de nascimento antes de 1900 (16/10/2026). A prescrição ou o Mini-Exame lançado direto do histórico numa consulta que ainda estava agendada conclui a consulta, como o registro do atendimento, e o registro clínico fica vazio até o prescritor preencher. Antes ela ficava agendada para sempre: a próxima consulta do paciente voltava a ser primeira consulta e o gráfico não marcava o dia (30/10/2026). Anular essa prescrição ou esse Mini-Exame, com o registro clínico ainda vazio e nada mais valendo na consulta, devolve a consulta para agendada, porque a prescrição ou o Mini-Exame anulado deixa de mostrar que a consulta aconteceu: a consulta deixa de contar como atendimento e a falta volta a valer (30/10/2026). O cancelamento volta só enquanto a consulta não terminou, porque depois do fim sobram a falta e o registro do atendimento (decisão de 17/11/2026).
- **Prescrição (RF05):** a nova prescrição substitui a vigente, e a anterior fica guardada como substituída. O óleo é descrito por uma lista de canabinoides, cada um com a sua concentração, até a clínica responder a questão 1. O paciente vê as próprias prescrições em tela própria. A prescrição que passa a valer e a anulação da que estava em uso avisam o paciente dentro do sistema, com link para Minhas prescrições: a troca avisa uma vez só, da nova, e a receita que entra só no histórico ou a anulação de uma já substituída não avisam, porque não mudam o que o paciente toma (RF14, 07/11/2026).
- **Anamnese (RF19):** o paciente preenche e corrige a ficha. A data de preenchimento é a do dia, e o preenchimento dá baixa na tarefa enviada pelo prescritor. A correção e a anulação feitas ao mesmo tempo não se apagam: a que chega depois espera a outra, e a correção de uma anamnese que acabou de ser anulada é recusada (16/10/2026). A correção guarda a versão anterior, que a prescritora vê no histórico e na impressão com a data da correção (decisão de 14/11/2026, revisão das regras R4). A ficha anulada não conta como preenchida: o paciente em acompanhamento que só tem ficha anulada vê de novo o *Preencher anamnese* no histórico, junto da anulada, e antes o botão só aparecia com a lista vazia (18/11/2026). Anular a anamnese avisa o paciente dentro do sistema, com o motivo e o link para preencher a ficha de novo, como a anulação de uma resposta de escala; a tarefa da avaliação inicial continua como está, para não mexer no indicador, e nada clínico vai por e-mail. Antes o paciente não recebia aviso nenhum e só descobria abrindo o histórico (decisão da dona do projeto, novembro de 2026). O aviso só sai quando a anulação deixa o paciente sem nenhuma ficha válida: quando ele tem outra anamnese valendo, como a preenchida depois de a avaliação inicial ser enviada de novo, anular a repetida não pede ficha nova, e a anulação aparece no histórico com o motivo (27/11/2026).
- **Histórico (RF12 e RF13):**
  - as duas telas mostram consultas, prescrições, anamnese e escalas com os dados reais;
  - o botão de exportar saiu do histórico do paciente até o RF33;
  - o prescritor anula registros no próprio histórico e chega de lá às outras telas do paciente.
- **Arquivamento de paciente:**
  - tira o paciente da lista de ativos e da contagem do painel;
  - encerra o acompanhamento automático, e um novo só começa depois de reativar. A tela do acompanhamento automático de um paciente arquivado diz isso no lugar do formulário, em vez de oferecer o que a API recusa (13/11/2026);
  - fecha as escalas que estavam em aberto, para o paciente não ser cobrado delas (02/10/2026). A que ainda estava no prazo e fecha sem resposta não vira escala vencida nem conta nos indicadores (revisão das regras R22, 14/11/2026). Anular depois a resposta de uma escala que o arquivamento encerrou, do acompanhamento ou enviada à mão antes dele, também não volta a cobrar o arquivado: a tarefa fecha sem resposta e com o dia marcado, sem o aviso de responder de novo, sem lembrete e fora dos indicadores. A escala enviada à mão depois do arquivamento segue a regra de sempre (14/11/2026). Reativar o paciente não desfaz o fechamento: a escala que estava em aberto no arquivamento não volta a ser cobrada (18/11/2026);
  - o paciente continua vendo o próprio histórico, e as escalas enviadas pelo prescritor continuam chegando, menos a avaliação inicial, que o arquivado não consegue mandar: o envio dela é recusado até reativar (16/10/2026). Em *Enviar escalas*, o cartão da avaliação inicial do paciente arquivado já aparece bloqueado, com o motivo e o caminho para reativar, em vez de a recusa só aparecer no envio e parar as outras escalas marcadas; as outras continuam indo num clique só (decisão da dona do projeto, novembro de 2026);
  - as telas do paciente avisam que o acompanhamento foi encerrado e que ele deve falar com a clínica, em vez de oferecer o que a API recusa: a de consultas não mostra mais o pedido nem diz que falta horário livre, o histórico e a anamnese não abrem a ficha nova e o painel fica sem o botão de agendar. As consultas já marcadas e a correção da anamnese continuam valendo (30/10/2026);
  - recusa os pedidos de consulta que ainda esperavam resposta, como a recusa do prescritor: o horário volta para a agenda, o paciente recebe o aviso e a mudança entra na trilha, porque o pedido segurava horário de quem segue em tratamento. As consultas já marcadas não são canceladas, porque cancelar é decisão clínica do prescritor: o aviso que confirma o arquivamento diz quantas consultas marcadas ficaram na agenda, e ele cancela à mão as que não forem acontecer. Nenhum lembrete de consulta sai para o paciente arquivado, nem o que o job manda no mesmo instante do arquivamento. O pedido que o paciente faz no mesmo instante também não fica aberto: ou ele espera o arquivamento e é recusado, ou o arquivamento é recusado com "O paciente acabou de pedir uma consulta. Tente arquivar de novo para recusar o pedido" e, na segunda vez, recusa o pedido junto (07/11/2026);
  - o prescritor também não marca consulta nova para o paciente arquivado: a API recusa e pede para reativar o paciente antes. As consultas marcadas antes do arquivamento continuam sendo remarcadas e canceladas (13/11/2026, §8.3);
  - o prontuário do paciente arquivado não recebe registro de consulta, prescrição nem Mini-Exame novos: a API recusa e pede para reativar o paciente antes ("Este paciente está arquivado. Reative o paciente antes de registrar a consulta", "antes de emitir a prescrição" ou "antes de aplicar o Mini-Exame"). Vale para a consulta nova, para a consulta marcada antes do arquivamento que ainda não tinha registro, para a falta que seria desfeita e para a consulta lançada depois. Antes, a prescrição emitida depois do arquivamento já nascia como "Última prescrição", com "acompanhamento encerrado em" uma data anterior à dela, e o paciente recebia o aviso "Prescrição nova" numa tela que dizia que o acompanhamento tinha acabado. Corrigir o registro de uma consulta já concluída e anular o que já existe continuam valendo, e as escalas enviadas pelo prescritor seguem a regra delas. O histórico do paciente arquivado não mostra "Nova consulta", "Emitir prescrição", "Mini-exame" nem "Desfazer falta e registrar atendimento", a agenda não mostra "Registrar atendimento" na consulta dele, e as telas da consulta, da prescrição e do Mini-Exame abrem com uma linha no lugar do formulário: o paciente está arquivado e é reativado na aba Arquivados da lista de pacientes. A marca "Última prescrição" continua valendo para a receita que estava em uso no arquivamento (decisão de 17/11/2026);
  - arquivar e reativar entram na trilha de auditoria;
  - arquivar não mexe na prescrição, que continua vigente, mas o paciente arquivado não tem mais prescrição em uso: nas telas do paciente e do prescritor e na impressão, a receita que estava em uso aparece como "Última prescrição", com "acompanhamento encerrado em" e a data do arquivamento, e a seção "Prescrição em uso" da impressão vira "Última prescrição, acompanhamento encerrado em dd/mm/aaaa". Em Minhas prescrições, o texto do topo também deixa de explicar a vigente e diz que o acompanhamento foi encerrado na data e que a última prescrição fica guardada abaixo (14/11/2026, reteste da revisão das regras R2). O CSV de prescrições continua com a situação gravada. Antes, meses depois de suspender o óleo e arquivar, a impressão levada a outro serviço ainda dizia "Prescrição em uso". Reativar volta ao que era. Anular essa receita também não fala em uso: o aviso ao paciente diz "Sua última prescrição foi anulada", e o modal do prescritor avisa que o paciente fica sem prescrição, mesmo depois de reativado, e que a anterior não volta a valer. Uma situação "Encerrada" para a receita fica para depois, com o ok da prescritora (decisão de 14/11/2026, revisão das regras R2).
- **Escalas enviadas:** o paciente sempre recebe a escala que o prescritor envia. Reenviar uma escala ainda pendente não cria tarefa repetida. A escala que o paciente já respondeu hoje não é enviada de novo no mesmo dia: o envio é recusado com "Esta escala já foi respondida hoje. Veja a resposta no histórico do paciente", que aparece na própria escala na tela de envio, e as outras marcadas continuam podendo ir. Na escala pontual conta a avaliação com a data de hoje; nos acompanhamentos semanais de dor e de TEA, que falam da última semana, conta a semana que termina hoje. A resposta do dia é uma só, então a tarefa nova ficaria pendente sem o paciente conseguir fechar no dia. A resposta anulada, a de outra data (nos acompanhamentos semanais, a semana que terminou ontem) e o dia do diário não seguram o envio (16/10/2026). A avaliação inicial também não vai para quem enviou a anamnese hoje, mesmo com outra data de preenchimento, porque a tarefa nova só fecharia com uma segunda ficha (30/10/2026).
- **Trilha de auditoria:** a abertura do prontuário gera uma linha por visita, mesmo quando a tela carrega várias listas ao mesmo tempo.

**E-mail configurado em 13/09/2026:** com `MAIL_HOST` definido, o link de recuperação de senha sai por e-mail pelo servidor SMTP. Sem ele, o link continua indo para o log da API.

**Troca do e-mail de acesso em 13/10/2026:** o e-mail só muda depois que a pessoa abre o link enviado para o endereço novo (RF18). Sem `MAIL_HOST` esse link também vai só para o log da API: a troca nunca é direta, porque é o link que prova que a pessoa recebe e-mail naquele endereço. A confirmação encerra as sessões da conta do link e a tela também sai da conta que estiver aberta naquele navegador, que pode ser outra (o responsável com dois filhos): antes a tela ia para o login e a outra conta continuava aberta para quem recarregasse a página. Se a saída falhar, a tela não vai para o login: diz que o e-mail foi confirmado e que não conseguiu sair daquela conta, com o botão para sair (18/11/2026).

**HTTPS na pilha em 13/10/2026 (RNF05):** o TLS termina no nginx do compose com o certificado da clínica (pasta `certs/`), a porta 80 só redireciona, o cookie de sessão sai com `Secure` por padrão (`.env.example`) e o smoke do CI sobe a pilha em https com certificado autoassinado. O passo a passo do servidor está em `docs/guia-de-deploy.md`. O desenvolvimento sem docker continua em http://localhost:5173 com `SESSION_SECURE_COOKIE=false`.

**Responsável ou cuidador em 14/10/2026 (RF38):** nome, parentesco e telefone, todos opcionais, informados pelo próprio paciente no cadastro e no perfil. Aparecem só no cabeçalho do prontuário, para o prescritor do paciente, com link para ligar, e não saem na lista de pacientes nem em nenhuma exportação. O termo de consentimento passou a citar esse dado. Login do cuidador, lembrete para ele e aceite do termo por responsável de criança esperavam a resposta da clínica. O aceite pelo responsável foi resolvido em 16/10/2026 (nota abaixo); o lembrete para o responsável continua em aberto.

**Aceite pelo responsável legal em 16/10/2026 (RF36, RF37 e RF38):** a clínica respondeu que, no caso de criança, quem aceita o termo é o responsável legal, que ele preenche os formulários entrando com a conta do paciente e que basta uma pessoa, com nome, parentesco e telefone, sem e-mail. O limite ficou em 18 anos, e não em 12: pelo Código Civil, até os 16 anos a pessoa é representada pelos pais e dos 16 aos 17 é assistida por eles, então um aceite feito só pelo adolescente pode ser anulado.

- **Duas faixas de idade:** menor de 16 anos (absolutamente incapaz, Código Civil, art. 3º; LGPD, art. 14, § 1º, para menor de 12) tem o termo aceito pelo responsável legal, em nome do paciente. Com 16 e 17 anos (relativamente incapaz, art. 4º, I), o paciente aceita junto com o responsável, que assiste. A caixa do aceite continua uma só e o texto dela muda com a faixa: "Sou o responsável legal pelo paciente e aceito, em nome dele, ..." com o botão "Li e aceito como responsável" para menor de 16, e "Eu, paciente, e meu responsável legal lemos e aceitamos ..." com o botão "Lemos e aceitamos" para 16 e 17 anos. A tela do aceite de uma versão nova do termo usa os mesmos textos. Trocar no cadastro a data de nascimento para outra faixa (adulto, 16 e 17 anos ou menor de 16) desmarca o termo e a pesquisa, para a pessoa certa marcar o texto certo.
- **Idade:** a API calcula pela data de nascimento, no horário de Brasília, em anos completos (`Period`): quem nasceu em 29 de fevereiro faz 18 anos em 1º de março nos anos que não são bissextos (Código Civil, art. 132, § 3º), igual ao front. O front nunca manda se o paciente é menor; ele só usa a data para trocar os textos da tela.
- **Cadastro e perfil:** para menor de 18 anos, o grupo "Familiar ou cuidador (opcional)" vira "Responsável legal", com nome e parentesco obrigatórios. O telefone continua opcional. O perfil não deixa apagar o responsável de um menor, nem mudar a data de nascimento para a de um menor sem informar o responsável.
- **Aceite:** cada aceite de menor de 18 anos, nas duas faixas, guarda o nome e o parentesco do responsável daquele momento (`consent_acceptance.guardian_name` e `guardian_relationship`, `V29__aceite_pelo_responsavel.sql`), porque o responsável salvo no paciente pode mudar depois e a prova do consentimento é da clínica (LGPD, art. 8º, § 2º). Isso vale no cadastro e no aceite de uma versão nova do termo. Conta de menor sem responsável salvo, que só existe de antes dessa regra, informa o nome e o parentesco dele na própria tela do aceite, porque com o termo pendente pela versão o perfil não salva. O mesmo vale para a conta criada quando o parentesco ainda era opcional, com o nome do responsável e sem o parentesco: a tela traz o nome preenchido e pede o parentesco, e nenhum aceite de menor é gravado sem os dois. Adulto com cuidador, como um idoso, aceita sozinho e o aceite fica sem responsável.
- **Aceite da faixa de idade de hoje:** o aceite que vale é o último da versão atual, e ele precisa combinar com a idade de hoje: de menor, com o responsável; de adulto, sem ele. A data de nascimento corrigida no perfil que cruza os 18 anos, ou o paciente que faz 18 anos, deixa o termo pendente de novo, e o aceite novo, da mesma versão, é gravado com ou sem o responsável, conforme a faixa. Antes valia o aceite antigo: o adulto corrigido para menor ficava só com o aceite dele, sem o responsável, e o menor corrigido para adulto continuava com o aceite feito em nome dele (#163). O termo abre logo depois de salvar o perfil, e a tela dele mostra a data de nascimento salva. Se a data foi digitada errada, ela se corrige ali mesmo, sem aceitar: com o termo pendente só pela faixa de idade, o perfil continua salvando, e com a data certa o aceite que já existia volta a valer. Assim a criança cuja data virou a de um adulto por engano não fica com um aceite sem o responsável. Com o termo pendente pela versão, o perfil continua bloqueado até aceitar. Desde 14/11/2026 o mesmo vale para o termo pendente pelo aniversário de 18 anos (item abaixo).
- **Faixa de idade conferida pela prescritora (decisão de 14/11/2026, revisão das regras R9):** toda a proteção do menor depende da data de nascimento, que o próprio paciente informa e corrige. Por isso a troca da data no perfil entra na trilha de auditoria como alteração da conta de paciente, com a data antiga e a nova, e a prescritora recebe um aviso no sistema sempre que a faixa muda, de menor para adulto ou o contrário, pela data trocada ou pelo aniversário de 18 anos. O aviso do aniversário sai no job das 8h, uma vez só, e o job olha quem fez 18 anos na última semana, para a API que ficou fora do ar não perder o aviso. O aviso *Novo paciente vinculado* traz a idade e, para menor, o nome e o parentesco do responsável legal, para a prescritora conferir a data digitada no cadastro. O termo pendente que veio do aniversário de 18 anos não oferece *Corrigir a data*: a data não mudou desde o aceite e não há o que corrigir, então o perfil fica bloqueado até o aceite de adulto, como na versão nova do termo, e o responsável que ainda usa a conta não volta a data para fugir desse aceite. Já a data trocada no perfil depois do último aceite, mesmo que pela data nova os 18 anos caiam entre o aceite e hoje, deixa a pendência pela faixa de idade e *Corrigir a data* continua na tela, porque pode ser o ano digitado errado, como 2008 no lugar de 2018; a troca já ficou na trilha e avisou a prescritora. A passagem da conta ao jovem de 18 anos é conversada na consulta, porque muitos adultos com TEA continuam precisando do cuidador. A data que cruza os 18 anos continua valendo sem a confirmação da prescritora.
- **Termo:** ganhou a cláusula em destaque "9. Paciente com menos de 18 anos", que cita a LGPD (art. 14) e o Código Civil, separa as duas faixas e diz que a clínica explica ao paciente, com palavras simples e adequadas à idade, como os dados são usados (por exemplo na consulta). A declaração final cobre as duas faixas. A versão passou para `2026-10-rascunho-2`, e quem aceitou a anterior lê e aceita de novo.
- **Pesquisa:** fechada para menor de 18 anos até o comitê de ética definir o assentimento do menor. A caixa some do cadastro para menor, e no perfil o cartão só aparece para retirar um aceite que já existe. A API recusa o aceite, a exportação sem identificação não sai de menor nem com aceite antigo, e mudar no perfil a data de nascimento para a de um menor retira o aceite de pesquisa, como se o paciente tivesse retirado.
- **Fica para depois:** mostrar no prontuário quem aceitou pelo menor.

**Prova do texto aceito (RF36 e RF37, decisão de 14/11/2026, revisão das regras R21):** a razão social, o CNPJ e o contato do encarregado de dados entram no termo por variáveis de configuração, e trocar uma delas muda o texto sem mudar a versão. Por isso cada aceite, do termo do cadastro e do de pesquisa, guarda também o hash (SHA-256) do texto mostrado, já com os dados da clínica, como prova do que a pessoa leu (LGPD, art. 8º), sem pedir aceite novo a ninguém. A tela manda de volta o hash do texto que mostrou, e o aceite é recusado como versão desatualizada quando o texto mudou depois que a tela abriu, para o hash guardado nunca ser o de um texto que a pessoa não viu. E, fora do desenvolvimento e do teste, a API não sobe sem as três variáveis, como já acontecia com `JWT_SECRET` e `PUBLIC_URL`: no ar com os colchetes, os primeiros pacientes aceitariam um termo sem a clínica e não seriam chamados a aceitar de novo. A versão continua mudando só quando o texto do termo muda, e não quando muda só um dado da clínica, porque trocar o telefone do encarregado travaria todos os pacientes até aceitarem de novo.

**Consentimento de pesquisa em 14/10/2026 (RF37):** separado do termo do cadastro, opcional e revogável. O paciente aceita no cadastro ou no perfil e retira no perfil quando quiser; cada aceite guarda a versão e a data, e a retirada fica marcada sem apagar o registro. Só vale o aceite da versão atual do texto: se o texto mudar, quem quiser continuar participando aceita de novo. A exportação sem identificação, que é a de pesquisa, só sai de quem tem o consentimento valendo. Entrar na pesquisa pede o termo do cadastro em dia: com ele pendente (versão nova do texto, ou aceite de outra faixa de idade), a tela não oferece a pesquisa e a API recusa o aceite com o mesmo aviso de termo pendente das outras gravações; ler os textos, aceitar o termo, sair da conta e retirar a pesquisa continuam liberados (decisão da dona do projeto, novembro de 2026; antes só a tela segurava). Desde 27/11/2026 a tela do termo pendente mostra a situação da pesquisa e, quando há aceite valendo, o botão *Retirar consentimento*, o mesmo do perfil, que fica escondido enquanto o termo não é aceito: quem não concorda com o termo novo não precisa aceitá-lo para sair da pesquisa. Nada mais fica acessível com o termo pendente (decisão da dona do projeto, novembro de 2026). O texto ainda é um rascunho e espera o projeto aprovado no comitê de ética.

**Reforço no banco em 15/10/2026 (RN08 e escalas):** no Postgres, três índices únicos parciais repetem regras que o código já garante com trava de linha: uma resposta valendo por paciente, escala e dia de início (o MEEM fica fora, porque é por consulta), um MEEM valendo por consulta e um acompanhamento ativo por paciente. A RN08 não ganhou restrição no banco: o registro de uma consulta feita fora da agenda grava no horário do atendimento sem passar pela agenda, e uma restrição de sobreposição recusaria esse registro clínico. A RN08 continua garantida no agendamento, pela trava do prescritor.

**Ações no mesmo instante em 16/10/2026:** o job das 8h e as ações das telas não gravam mais um por cima do outro. A consulta de amanhã cancelada ou remarcada antes de o job chegar nela fica como foi deixada e não recebe lembrete, e a mudança que chega depois do lembrete continua valendo; o pedido vencido que o prescritor cancela, recusa ou remarca antes de o job chegar nele fica como o prescritor deixou, e se o job chegou antes a ação do prescritor é recusada porque o pedido já foi recusado; a resposta ou a anamnese gravada antes de o job ler a tarefa vencida conta, mesmo chegando no mesmo instante, e a que fica esperando o job encontra a tarefa já fechada, mas em nenhum dos casos o job grava não respondida por cima de uma resposta já gravada; e o paciente arquivado, o acompanhamento encerrado ou a conta desativada no mesmo instante da rodada não recebem a rodada. O cadastro do acompanhamento e a anamnese nova feitos enquanto o paciente é arquivado são recusados, a segunda anulação da mesma receita é recusada em vez de trocar o motivo da primeira, e a troca de senha pelo perfil feita no mesmo instante do link de senha nova confere a senha atual com a que o link gravou. Um lembrete, pedido vencido ou tarefa vencida com falha no job não segura mais os outros: os outros saem, o dia não conta como concluído e o que falhou é tentado de novo quando o ciclo rodar outra vez (o lembrete da consulta de amanhã, só se for no mesmo dia). Por baixo, todas as ações seguem uma ordem só de travas no banco (seção banco do README do backend). Antes, no Postgres, o registro da consulta e o MEEM contra o cadastro do acompanhamento, e a recusa do pedido vencido contra o cancelamento, podiam ficar um esperando o outro até o banco derrubar um dos dois.

**Nome social em 16/10/2026 (RN15):** opcional, como a clínica pediu (#109), e informado pelo próprio paciente no cadastro e no perfil. Quando existe, é ele que aparece em todas as telas (lista de pacientes, painéis, agenda, consulta, escalas, cabeçalho do sistema e trilha de auditoria), nos avisos e nos e-mails. O nome civil continua guardado e aparece ao lado do social só onde o registro identifica a pessoa:

- no cabeçalho do prontuário, como *Nome civil*;
- na impressão do histórico, junto do nome no campo *Paciente*;
- na exportação com identificação, na mesma célula, como "Nome social (nome civil: Nome civil)";
- na tela de contas de paciente da administração.

A exportação sem identificação não leva nenhum dos dois. Nome social em branco ou igual ao nome civil não é guardado. A lista de pacientes do prescritor e a de contas desativadas da administração seguem a ordem alfabética do nome mostrado, e a busca do prescritor procura por ele, sem diferença de acento nem de maiúscula ("joao" acha "João", 16/10/2026). O prescritor e a administração não editam o nome social, e a troca não entra na trilha de auditoria, como os outros dados do perfil, menos a data de nascimento, que entra (decisão de 14/11/2026, revisão das regras R9). Os avisos já criados continuam com o nome da época.

**Primeira consulta, retorno e dias sem atendimento em 16/10/2026 (RF10 e RF11):** a clínica respondeu que atende de segunda a sexta, que não marca consulta nos dias em que não pode atender e que a primeira consulta dura de 60 a 90 minutos e o retorno, menos.

- **Segunda a sexta:** já funcionava com os períodos da semana cadastrados de segunda a sexta. Nada mudou.
- **Duração por tipo:** os horários de atendimento guardam a duração da primeira consulta (90 minutos por padrão) e a do retorno (60 minutos por padrão). Cada consulta guarda o tipo; as marcadas antes disso ficam sem tipo.
- **Tipo pelo histórico:** é primeira consulta enquanto o paciente não tiver nenhuma consulta concluída e não anulada; falta e cancelamento não contam. O paciente não escolhe o tipo: os horários livres dele já têm a duração do tipo e andam no passo da consulta mais curta, para primeira consulta e retorno se encaixarem na mesma agenda. O prescritor escolhe o tipo ao marcar ou remarcar, ou deixa o sistema decidir pelo histórico, e a duração que ele escolher vale mais que a do tipo. A consulta registrada fora da agenda, depois de acontecer, também recebe um tipo, decidido pelas consultas concluídas antes dela, e a duração desse tipo.
- **Dias sem atendimento:** o prescritor marca um dia, de hoje em diante, em que não vai atender, e o dia some dos horários livres dos pacientes. O que já estava marcado nele continua valendo, sem cancelamento e sem aviso automático: a tela mostra quantas consultas marcadas e pedidos ainda por vir ficaram no dia, para o prescritor remarcar ou cancelar. O prescritor ainda pode marcar consulta direto num dia sem atendimento.
- **Pacientes do piloto:** quem já era paciente da clínica e não tem consulta concluída no sistema aparece como primeira consulta. O prescritor corrige o tipo e a duração ao remarcar o pedido, ou escolhe o tipo ao marcar a consulta direto.

**Agenda concluída em 13/09/2026:** RF10 e RF11.

- **Horários de atendimento:** o prescritor cadastra os períodos da semana em que atende e a duração padrão das consultas. Os horários livres saem da divisão desses períodos pela duração. O período em que não cabe nem a consulta mais curta é recusado ao salvar, com o erro na linha dele: antes ele era salvo com *Seus pacientes já podem pedir consulta nesses períodos* e nunca gerava horário (13/11/2026, §8.3).
- **Pedido do paciente:**
  - o paciente escolhe um horário livre dos próximos 30 dias e pode escrever o motivo da consulta, que fica fora dos campos clínicos;
  - a API segue a mesma janela de 30 dias contando o dia de hoje e recusa o pedido para depois dela (16/10/2026);
  - o pedido é para um horário com pelo menos 24 horas de antecedência, contadas do momento do pedido, as mesmas 24 horas do cancelamento pelo paciente. A tela só oferece esses horários, e a API recusa o mais cedo com "Escolha um horário com pelo menos 24 horas de antecedência" (13/11/2026);
  - o paciente tem um pedido esperando resposta por vez. Pedir outro horário substitui o pedido anterior: a tela mostra o pedido que espera, avisa que o novo vai substituí-lo e pergunta antes de enviar; a API confere o horário novo como sempre, sem contar o horário do próprio pedido que vai ser substituído (então dá para trocar por um horário vizinho que encosta nele), e, só se ele passar, cancela o anterior como no cancelamento pelo paciente (trilha e horário liberado) e grava o novo, tudo de uma vez. Recusado o novo, o anterior fica como estava, e pedir de novo o horário do próprio pedido é recusado com "Este já é o horário do seu pedido. Para trocar, escolha outro horário". A troca só cancela o pedido que a tela mostrou e o paciente confirmou: se o prescritor respondeu esse pedido ou outra aba fez um pedido depois que a tela abriu, a API recusa com "Seus pedidos de consulta mudaram enquanto você escolhia o horário..." e nada muda, para a tela nunca dizer que cancelou o que não cancelou. O prescritor recebe um aviso só, o do pedido novo, dizendo qual pedido ele substituiu. A consulta já confirmada não conta como pedido. O limite de 3 pedidos em aberto, de 16/10/2026, saiu, e dois pedidos do mesmo paciente enviados ao mesmo tempo terminam com um só esperando resposta (13/11/2026, §8.3);
  - o pedido segura o horário até o prescritor confirmar ou recusar;
  - a recusa pode levar um motivo, que chega ao paciente no aviso, e libera o horário;
  - na lista de consultas do paciente, o que ele escreveu no pedido aparece como *Seu motivo*, e o pedido recusado diz que o motivo da recusa, quando houver, está em *Avisos*. Antes, logo abaixo de *Recusada*, o texto do próprio paciente vinha como *Motivo* e parecia o motivo da recusa (13/11/2026);
  - o pedido não tem prazo de resposta e a prescritora só é avisada dentro do sistema, então o sistema não é canal de urgência. O campo do motivo, as observações das escalas e da ficha diária e cada parte da anamnese (no alto de *Sobre você*, *Histórico de saúde*, *Hábitos*, *Como você está hoje* e *Sobre o tratamento*) trazem o aviso fixo *Isto não é atendimento de urgência. Se algo piorou, fale com a clínica ou procure um serviço de saúde.*, o mesmo componente nas três telas, e o leitor de tela lê o aviso junto de cada um desses campos. E o job das 8h avisa o paciente cujo pedido para amanhã ainda não teve resposta: *Pedido ainda sem resposta*, dizendo que o pedido ainda não foi confirmado e que ele fale com a clínica. Sai uma vez só por pedido, só dentro do sistema, e o pedido continua esperando a resposta. Antes o paciente só ficava sabendo depois que o horário tinha passado (decisão de 14/11/2026, revisão das regras R1).
- **Agenda do prescritor:** mostra os pedidos esperando resposta e as consultas da semana. O prescritor também marca consulta direto, com a duração padrão ou outra, remarca e cancela. Dois agendamentos do mesmo prescritor nunca ocupam o mesmo horário (RN08), nem quando a consulta da noite passa da meia-noite (16/10/2026). Pedido cujo horário passou sem resposta não é mais confirmado: o prescritor recusa, e o paciente escolhe outro horário (16/10/2026). O aviso do pedido que passou da data diz "Escolha outro horário na agenda"; quando a conta do prescritor está desativada, a agenda do paciente não oferece horário, e o aviso diz "Fale com a clínica para marcar", sem falar da conta do prescritor (decisão de 17/11/2026). A semana esconde os pedidos recusados e as consultas canceladas até o prescritor tocar em *Mostrar recusados e cancelados* (17/11/2026).
- **Cancelamento:** o paciente cancela o pedido a qualquer hora e a consulta marcada até 24 horas antes. Depois disso, só o prescritor cancela. O aviso do prescritor diz se o paciente cancelou um pedido, que o prescritor ainda não tinha confirmado, ou uma consulta marcada (13/11/2026). O prescritor cancela a consulta só até o fim dela; depois do fim sobram a falta e o registro do atendimento, e a agenda não oferece mais o *Cancelar* (17/11/2026).
- **Avisos:** pedir, marcar, confirmar, recusar, remarcar e cancelar geram notificação no sistema para a outra parte. O lembrete antes da consulta (RF34) fica para o módulo de notificações.
- **Aviso da remarcação (decisão de 17/11/2026):** o aviso "Consulta remarcada" diz o que mudou. Antes ele dizia sempre "foi remarcada para" a data e a hora, mesmo quando o dia e a hora ficavam iguais e só a modalidade mudava, e o paciente podia entrar na chamada de uma consulta que passou a ser na clínica. Agora, com dia ou hora novos, o texto diz a data e a hora novas e, se a modalidade também mudou, termina com ", agora presencial" ou ", agora remota". Com o mesmo dia e a mesma hora, o texto diz o que mudou: "Sua consulta com (nome) de (data e hora) agora é presencial" (ou remota), "agora tem 45 minutos" ou "agora é um retorno". Várias mudanças vão numa frase só. O e-mail da remarcação usa as mesmas palavras, e as regras de quando ele sai não mudam (RF11).
- **Registro clínico:** só a consulta confirmada na agenda recebe registro clínico e prescrição.
- **Falta marcada por engano:** registrar o atendimento desfaz a falta e conclui a consulta, e o paciente recebe o aviso de que a falta foi desfeita, já que o aviso da falta pedia para ele remarcar. A agenda e o histórico do prescritor mostram "Desfazer falta e registrar atendimento" na consulta com falta, que abre o registro; antes isso só era possível digitando o endereço na mão. A prescrição e o Mini-Exame aparecem depois do registro (30/10/2026).

**Escalas concluídas em 13/09/2026:** RF08, RF09, RF32, RF06/RF20 e RF21 a RF26.

- **Motor único:** uma tabela de respostas para todas as escalas, um cálculo por instrumento e a definição de cada escala — itens, âncoras, faixas e direção — em um só lugar, servida a um formulário genérico. Escala nova não pede tabela nem tela nova (RNF08). Saíram sete tabelas, sete serviços e seis telas.
- **Formulários conferidos com o papel:** os itens e as âncoras saem dos formulários de `docs/scales`, inclusive as que mudam de sentido de um item para outro — na dor 10 é ruim e no sono 10 é bom (RF20).
- **Escalas validadas:** HAM-A com os 14 itens e escore de 0 a 56, PSQI calculado pelos 7 componentes de 0 a 21 e MEEM com as 11 seções, de 0 a 30. A faixa do MEEM usa o ponto de corte por escolaridade de Brucki 2003, registrada junto com a data da avaliação (decisão de 13/09/2026, a confirmar com a clínica na pergunta 10).
- **Tarefa com prazo:** cada envio vale por um período. Sem resposta até o fim do prazo, a tarefa fica como não respondida, vira lacuna no gráfico (RN10) e o período seguinte é enviado, em vez de a mesma pendência arrastar para sempre.
- **Diários:** a ficha de acompanhamento e o diário do sono são um registro por dia, apresentados como a grade da semana do papel. A tarefa mostra quantos dias já foram preenchidos e fecha no fim do período; a semana sem nenhum dia conta como não respondida. Na tarefa aberta a grade cobre o período inteiro até hoje, e o paciente preenche de uma vez os dias que esqueceu; dia futuro é recusado, e dia de outro período fica fora da contagem da tarefa aberta (16/10/2026). A semana que venceu e ainda aceita os dias dela, antes do job das 8h ou reaberta pela anulação, abre a grade do período dela, e não a semana de hoje (16/10/2026). Depois que o job fecha a semana, ela continua na grade, antes do período aberto, até o fim do dia seguinte ao último dia dela, e o dia preenchido ali fica com ela como atraso: entra nos dias preenchidos e não deixa a semana no prazo. Ele também não segura a semana como respondida: anular o único dia no prazo reabre a semana mesmo com o dia atrasado, com o aviso de responder de novo. Sem período aberto, a grade continua sendo a semana de hoje até hoje, e a semana que fechou entra antes dela: ela só acrescenta dias, e hoje nunca sai da grade. Um diário conta como respondido com um dia preenchido, e por isso dois dias seguidos em branco geram um lembrete (decisão de 14/11/2026, revisão das regras R6; aplicar a meta aos dias preenchidos fica com a clínica).
- **Correção:** o paciente corrige a própria resposta enquanto o prescritor não marca como analisada. Depois disso, corrigir é anular com motivo, como já é na consulta e na prescrição. A resposta anulada fica só no histórico: a data dela abre em branco e o paciente responde de novo, inclusive no mesmo dia (16/10/2026). No histórico, na central de escalas e na lista de anamneses, o registro refeito no mesmo dia aparece antes do anulado (30/10/2026). A tarefa reaberta pela anulação depois do prazo fecha com a resposta nova, em vez de virar não respondida no dia seguinte, e a anulação entra na trilha como anulação, não como alteração. A anulação que reabre a tarefa avisa o paciente no sistema, com o motivo, para responder de novo, e o prazo que acabaria antes de depois de amanhã passa para depois de amanhã: a anulação costuma vir depois do prazo, e o job das 8h fechava a tarefa no dia seguinte, antes de o paciente saber e de o lembrete sair. O aviso abre a tela da escala já na data anulada e, quando a rodada seguinte da mesma escala já está pendente e cobre hoje, diz com qual data (ou período) responder: a tela sugeria hoje, e a resposta de hoje fechava a rodada nova enquanto a reaberta seguia pendente, levava lembrete de uma escala já respondida e virava não respondida (22/10/2026). O botão de responder do cartão dessa tarefa, na central de escalas e no painel, e o lembrete do prazo dela abrem a tela na mesma data quando hoje é de outra pendente da mesma escala ou fica fora da tarefa reaberta (14/11/2026). No diário a semana reaberta continua a mesma, porque os dias dela são os da grade, e o job das 8h do dia seguinte fecha ela, também quando a anulação chega antes do job do próprio dia, que deixa essa semana aberta (14/11/2026): o aviso diz qual dia preencher de novo e que é até amanhã de manhã, e abre a grade nesse dia (16/10/2026, 22/10/2026). O aviso sai também quando a semana venceu ontem e o job do dia ainda não tinha fechado ela, porque a anulação do único dia preenchido segura essa semana até amanhã do mesmo jeito (20/11/2026). A correção que deixa pela metade uma escala pontuada que estava completa devolve a tarefa para pendente (16/10/2026). Toda correção guarda a versão anterior, também a do dia do diário respondido de novo: a prescritora vê "corrigida em dd/mm/aaaa" com o resultado de antes no histórico e na impressão, e a resposta aberta para leitura mostra o que estava antes de cada correção. O mesmo conteúdo enviado de novo não vira versão. A tela do paciente não mostra o valor antigo e a administração não lê nenhum dos dois. O prazo da correção fica para decidir depois (decisão de 14/11/2026, revisão das regras R4).
- **Valores conferidos:** cada resposta é gravada como chegou ou recusada, nunca trocada por outro valor. Número quebrado ou fora da faixa do item, horário que não vem como texto, resposta só com itens em branco, mais tempo acordado do que na cama no diário do sono e período que termina depois de hoje são recusados (16/10/2026). Dormir e levantar no mesmo horário no diário do sono também, com o erro no campo do horário de levantar (decisão de 13/11/2026, RF22). Desde 14/11/2026 (revisão das regras R13), o tempo na cama acima de 16 horas, no diário e no PSQI, as horas de sono do PSQI maiores que o tempo na cama, os minutos acima de 1.440 e as contagens acima de 200 (gotas por dose, despertares, doses, xícaras, cigarros) são recusados com o erro no campo (RF22, RF23).
- **Resultado:** a central do paciente mostra o escore com a faixa interpretativa (RN14). Formulário da clínica sem faixa publicada mostra o item principal e nenhuma interpretação inventada (RN13).
- **Respondidas por escala (decisão da dona do projeto, novembro de 2026):** a central do paciente mostrava todas as respostas desde o começo do tratamento, cada uma com *Ver respostas* e um *Ver progresso* repetido, o que no celular dava cerca de 11 telas de rolagem e escondia o resultado recente. Agora **Respondidas** tem uma linha por escala, com o período e o resultado da resposta mais recente, *Ver respostas* (ou *Ver e corrigir*) dela e um único *Ver progresso*. As outras respostas da escala ficam dentro da linha, atrás do botão *Ver as outras N respostas*, que abre e fecha pelo toque ou pelo teclado e diz ao leitor de tela se está aberto; cada uma mostra o período, o resultado, a marca de anulada ou de analisada e o seu *Ver respostas*. A resposta anulada não vale como resultado da escala: a linha mostra a mais recente que vale, e a anulada fica com as outras. O número ao lado de **Respondidas** conta as escalas. O MEEM continua fora da tela do paciente, porque a API não manda o exame para ele (§8.3).
- **MEEM:** aplicado pelo prescritor dentro de consulta confirmada, nunca designado ao paciente (RN09), e aparece só no histórico do prescritor: desde 13/11/2026 o paciente não vê o exame, e o resultado é conversado na consulta (§8.3).
- **Avisos:** a escala enviada avisa o paciente e a resposta completa avisa o prescritor (RF15), inclusive a avaliação inicial, que antes dava baixa na tarefa sem aviso (16/10/2026). Nos diários o prescritor recebe um aviso só, quando o período fecha, com a quantidade de dias preenchidos (02/10/2026), contando também os dias do período preenchidos com atraso antes do fechamento (16/10/2026). As pendências vencidas aparecem no painel do prescritor, menos as de paciente arquivado, que saiu do acompanhamento (16/10/2026). Sem outra pendência da mesma escala, a resposta que chega depois de a última tarefa daquela escala fechar como não respondida tira essa tarefa do painel no mesmo dia, e anular essa resposta devolve a tarefa às vencidas sem reabrir. Quando a rodada seguinte da escala já está pendente, a resposta fica com ela e a rodada perdida continua nas vencidas, como a rodada mais velha que uma já respondida; nos diários a semana perdida também continua nas vencidas, a não ser que um dia dela seja preenchido no dia seguinte ao fechamento: aí ela sai das vencidas e continua fora do prazo (decisão de 14/11/2026, revisão das regras R6). A anamnese tira do painel toda avaliação inicial perdida, com ou sem uma pendente, porque a ficha é uma só. Essas tarefas continuam não respondidas e contam como fora do prazo nos indicadores (30/10/2026). Vale também para a avaliação inicial que venceu e ainda esperava o job das 8h quando foi enviada de novo: a ficha fecha a nova como respondida e a antiga como não respondida, fora das vencidas (18/11/2026).
- **Escala pontuada pela metade:** fica guardada como "Incompleta: sem escore", mas a tarefa continua pendente e no prazo vira não respondida; a tela só deixa salvar com todos os itens respondidos (02/10/2026).
- **Escala avulsa e ciclo:** a tarefa guarda a origem, e só a do acompanhamento marca a próxima rodada (RF32, 02/10/2026). A rodada que chega no dia em que a mesma escala já foi respondida fica com essa resposta, porque a resposta do dia é uma só e a rodada nova ficaria pendente. Cada escala do acompanhamento guarda a própria rodada, então o acompanhamento que substitui outro começa o próprio ciclo com a periodicidade nova, em vez de esperar a rodada mensal do anterior vencer (16/10/2026). A tarefa do acompanhamento anterior que ainda espera resposta, ou que foi respondida no dia, vira a primeira rodada do novo e fica com o prazo dela, para mais ou para menos, com lembrete de novo para o prazo novo: o diário semanal trocado no meio da semana segue até o fim da rodada nova, e a Hamilton semanal trocada por mensal vale o mês. Antes ela mantinha o prazo antigo e os dias até a rodada seguinte ficavam sem tarefa (16/10/2026). A escala que fica de fora do acompanhamento que substitui outro para de ser cobrada na hora da troca: a rodada pendente dela fecha no dia, com a resposta já dada valendo; sem resposta, ela não vira escala vencida nem conta nos indicadores, porque foi escolha do prescritor e não atraso do paciente. A rodada que já tinha vencido antes da troca continua como atraso, e anular depois a resposta de uma escala que saiu do acompanhamento não volta a cobrar o paciente (07/11/2026).
- **Encerrar o acompanhamento à mão (decisão da dona do projeto, novembro de 2026):** o botão *Encerrar acompanhamento* para as rodadas novas e fecha na hora as rodadas do acompanhamento que ainda esperavam resposta, como a troca e o arquivamento já faziam, com a marca de interrompida pelo prescritor: o paciente deixa de ser cobrado por elas (lembrete, *Dias em branco*, esperando resposta), a resposta já dada continua valendo, e a que fecha sem resposta dentro do prazo não vira escala vencida nem conta no indicador. A que já tinha vencido continua como atraso. Antes as escalas já enviadas seguiam até o prazo e, sem resposta, contavam fora do prazo, mesmo com o acompanhamento encerrado. A escala enviada à mão e a avaliação inicial não são do acompanhamento e ficam como estão, e o acompanhamento que acaba sozinho, no fim do prazo, não fecha nada. A janela de confirmação diz isso antes de o prescritor encerrar. Por consequência, o acompanhamento criado depois de encerrar o anterior à mão começa com rodadas novas de todas as escalas, e a rodada aberta do anterior só vira a primeira do novo na renovação dos últimos 7 dias, que não passa pelo encerrar.
- **Limite conhecido, rodada das 8h no dia de uma resposta atrasada:** se o paciente responde antes do job das 8h a rodada que venceu ontem (às 7h, por exemplo), a resposta fecha essa rodada, e a rodada nova que o job manda às 8h fica pendente nesse dia: responder de novo com a data de hoje só corrige a resposta das 7h. A rodada nova vale pelo prazo da periodicidade, então o paciente responde a partir do dia seguinte (16/10/2026).
- **Convite:** o prescritor vê os convites em aberto e cancela o que foi para a pessoa errada; convite já usado não cancela (RN06, 02/10/2026). O link vale 7 dias contados da hora em que foi gerado, então a janela do convite e a lista dos convites em aberto mostram a data e a hora em que ele vence, e não só o dia (16/10/2026). Para o prescritor saber qual linha cancelar, os convites gerados no mesmo minuto mostram também os segundos, e o convite cujo link está aberto na janela vem marcado como "É o link acima" (13/11/2026).
- **Duração:** o acompanhamento de 90 dias termina no dia 90, e o que começa hoje envia a primeira rodada na criação (02/10/2026). O que terminaria antes de hoje é recusado na data de início, em vez de nascer vencido, e o que já passou do fim não trava a renovação no dia seguinte, antes do job das 8h: criar o novo encerra o anterior, sem o prescritor encerrar na mão, e a tela já mostra o anterior como encerrado. A mesma escala entra uma vez só no acompanhamento (30/10/2026). Desde 14/11/2026 (revisão das regras R5), o job das 8h avisa o prescritor no sistema 7 dias antes do fim e no dia do fim, cada aviso uma vez só e com o link da tela do acompanhamento; o de 7 dias só sai para o acompanhamento que começou antes da última semana, e com a API fora do ar no dia do fim o aviso sai no dia em que o job encerra, dizendo que já terminou. Nesses últimos 7 dias a renovação já é aceita: o novo começa hoje, no lugar do atual, e a escala que fica com a mesma frequência segue a rodada de agora, sem chegar de novo nem ganhar prazo novo. Começar depois de hoje é recusado na data de início, porque deixaria dias sem escala. Depois da data do fim o acompanhamento conta como encerrado, tenha o job das 8h já rodado ou não, como a tela mostra: a renovação feita depois do fim é sempre um ciclo novo, que envia a primeira rodada de todas as escalas. Antes, no dia seguinte ao fim, a renovação feita antes das 8h seguia as rodadas do anterior e a feita depois começava um ciclo novo, e o resultado dependia só da hora (decisão de 17/11/2026). A tela abre o formulário preenchido com o último acompanhamento, em andamento ou já encerrado (escalas, frequências, duração e horários do sono), e a renovação sai com um clique. A regra de encerrar não muda.

**Evolução concluída em 14/09/2026:** RF27, RF28 e RF07.

- **Uma página para os dois perfis:** o paciente vê a própria evolução e o prescritor a de cada paciente vinculado, os dois com o período "todo o tempo" (RF27 e RF28; para o paciente, desde a decisão de 14/11/2026, revisão das regras R27). O RF07 ficou dentro dela, como decidido em §8.3. O prescritor que abre pelo endereço o progresso de um paciente que não é dele vê só o aviso de acesso negado, sem filtros, gráfico nem comentários, que falariam de dados que a tela não leu (13/11/2026). Desde a decisão da dona do projeto em novembro de 2026 esse aviso é o cartão de falta de acesso, com o cadeado e o botão *Ir para o início* (RNF01), no lugar da frase com o caminho para a lista de pacientes.
- **Gráfico:** período, escala e atributo no topo, e a curva do atributo escolhido ao longo do tempo, com o eixo pela data. Dia sem resposta é lacuna, nunca zero (RN10): a linha se interrompe onde passou mais tempo do que a frequência da escala sem resposta, 1 dia na ficha e no diário e 7 dias nos acompanhamentos semanais de dor e de TEA. Na Hamilton, no Pittsburgh e no MEEM, que não têm frequência fixa, a linha liga uma resposta à outra (decisão de 14/11/2026, revisão das regras R3). A resposta que chega atrasada de um filtro antigo é descartada, então, com a internet lenta, a tela não mistura a curva, as gotas, as consultas e os comentários de dois períodos (13/11/2026).
- **Faixa no gráfico:** nas escalas validadas, cada faixa do instrumento vira uma linha de corte com o nome, então o escore nunca aparece como número solto (RN14). Formulário sem faixa publicada não ganha linha nenhuma (RN13). O MEEM, que não tem faixa fixa, mostra a faixa da escolaridade em cada ponto e a linha do ponto de corte quando a escolaridade é a mesma no período, e só avisa que a escolaridade mudou quando há dois ou mais pontos de corte diferentes. Exame antigo sem escolaridade registrada aparece como "sem faixa", contado à parte (16/10/2026). Desde 13/11/2026 o MEEM aparece só no progresso do prescritor (§8.3).
- **Qual lado é melhor:** abaixo do nome do atributo, as âncoras do formulário e se maior ou menor é melhor, porque em ansiedade, náusea e função intestinal a curva subindo é melhora e em dor e tremor é piora (16/10/2026).
- **Dose junto do sintoma:** nos atributos da ficha de acompanhamento, uma caixa liga a linha das gotas do dia. É o que responde se mexer na dose mudou o sintoma. As gotas têm eixo próprio, à direita, com os números na cor da linha delas, e a legenda diz *Gotas no dia (eixo da direita)*: no mesmo eixo da nota de 0 a 10, 100 gotas achatavam a curva do sintoma (decisão de 14/11/2026, revisão das regras R13). No dia em que só uma das doses foi preenchida, o ponto mostra a dose preenchida com um marcador vazado, *Dia com uma das doses em branco* na legenda, e a dica do ponto diz qual dose ficou em branco, como "10 (só a manhã, a tarde ficou em branco)". No dia em que as gotas foram anotadas e o item ficou em branco, a gota continua no gráfico, sem cortar a linha das gotas, e a dica do ponto diz *em branco* no item. A dose em branco não vale zero (RN10), e sem a marca a dose preenchida, lida como o total do dia, pareceria uma redução da dose. Em 13/11/2026 esse dia ficava sem ponto, e a linha de quem toma uma dose só por dia sumia inteira (decisão de 14/11/2026, revisão das regras R12). Ao trocar o período, o gráfico fica sem gota nenhuma até chegarem as gotas do período novo, em vez de mostrar as do anterior e esticar o eixo até elas; as gotas que não carregaram aparecem como "Não foi possível carregar as gotas do dia. Confira sua internet e tente de novo." quando o sistema não responde, ou "Não foi possível carregar as gotas do dia. Tente de novo mais tarde." quando ele responde com erro, sem a linha vazia com a legenda, como se nada tivesse sido anotado (14/11/2026).
- **Consultas no gráfico:** a consulta do período vira uma linha vertical no dia em que aconteceu, com marca própria quando saiu receita, e aparece também na lista embaixo. A que caiu em dia sem preenchimento também vira linha, no dia dela, porque o eixo vai pela data (decisão de 14/11/2026, revisão das regras R3). O eixo vai da primeira à última resposta do período, então a consulta de antes da primeira ou de depois da última fica só na lista, como a de um ano antes em "todo o tempo" (14/11/2026, reteste da revisão das regras R3). Receita anulada não conta: anular a única receita da consulta tira a marca (30/10/2026). Duas consultas no mesmo dia viram uma marca só, que sai como *consulta + receita* quando qualquer uma delas teve receita, e não só a primeira (14/11/2026). A consulta é marcada pela data completa, então, em "todo o tempo", a de 18/09 de um ano não cai no 18/09 do ano seguinte; quando as respostas passam de um ano, cada data do eixo mostra também o ano (13/11/2026).
- **Relato do paciente (RF07):** os comentários escritos dentro das escalas aparecem em uma linha do tempo abaixo do gráfico, com a data e a escala de onde vieram.
- **Extensibilidade:** acrescentar uma escala não exige tocar no serviço de progresso nem na tela: os atributos, as faixas e os limites do eixo vêm do catálogo (RNF08).

**Painel inicial concluído em 14/09/2026:** RF03.

- **Paciente:** próximas consultas de verdade, lidas do banco, incluindo o pedido que ainda espera resposta; escalas esperando resposta com o prazo e o botão de preencher; a prescrição que está valendo, com a posologia; e os avisos que ele ainda não leu.
- **Um próximo passo só (decisão da dona do projeto, novembro de 2026):** no painel do paciente só o **Responder** da escala mais urgente é um botão cheio: a atrasada vem primeiro e, sem atrasada, a de prazo mais perto. Os outros **Responder** e o *Agendar consulta* são botões de contorno, e a escala atrasada ganha o traço pêssego da identidade visual, como em Minhas avaliações. Antes a primeira tela do celular tinha cinco botões verdes cheios e o atrasado só se diferenciava por uma linha de texto.
- **Prescritor:** pacientes ativos, consultas de hoje com link direto para o registro do atendimento, pedidos de consulta esperando resposta e escalas que venceram sem resposta, cada uma com o paciente. A consulta de hoje já concluída aparece como concluída e leva ao histórico, como na agenda, em vez de pedir o registro de novo. Vale também para a consulta concluída só pela prescrição ou pelo Mini-Exame, que ainda está com o registro vazio: nas duas telas, ele se preenche pelo *Editar registro* do histórico (13/11/2026). O número de pacientes ativos do painel conta todo paciente fora do arquivo, e o dos indicadores (RF39) só quem está fora do arquivo e com a conta ativa. Para os dois números não parecerem um erro, o cartão do painel mostra a nota "N com a conta desativada" quando a administração desativou a conta de algum paciente ativo; sem conta desativada não há nota, e o texto dos indicadores continua o mesmo (decisão da dona do projeto, novembro de 2026).
- **Nada de fachada:** saíram os cartões que nunca tiveram dado por trás — alertas clínicos, fichas com prioridade, pacientes recentes — e os botões que não faziam nada. O painel mostra só o que o resto do sistema grava (RF30).
- **Resumo, não lista:** cada cartão traz no máximo cinco itens e leva para a tela que tem a lista inteira. Nos quatro cartões que vêm com total — esperando resposta e avisos não lidos no painel do paciente, pedidos esperando e escalas vencidas no do prescritor — o número é o total de verdade, contado no banco, e não o tamanho da lista: antes o prescritor com doze escalas vencidas via 5, e o paciente com oito avisos não lidos via 5 no cartão e 8 no menu. Quando há mais de cinco, esses cartões dizem "Mostrando 5 de N". As consultas de hoje não são cortadas, então o número é a lista mesma; as próximas consultas param em cinco sem número nem aviso. As escalas vencidas ainda não têm uma tela com a lista inteira, então o aviso é o que mostra que há mais (16/10/2026).

**Notificações concluídas em 14/09/2026:** RF14, RF15 e RF34.

- **Lembrete de consulta:** o job das 8 da manhã avisa quem tem consulta no dia seguinte. Sai uma vez só, e ainda dá tempo de remarcar. A consulta marcada, confirmada ou remarcada para amanhã recebe o lembrete na hora, com o horário novo, a qualquer hora do dia e sem depender de o job do dia ter terminado; antes ela ficava sem lembrete, ou só com o e-mail do horário antigo (16/10/2026). Sai uma vez só: a edição que mantém o horário e só troca a modalidade, a duração ou o tipo não repete o lembrete (22/10/2026). Na consulta cujo lembrete saiu por e-mail, o cancelamento pela prescritora e a remarcação para outro dia, quando o horário novo não recebe lembrete na hora, também vão por e-mail, com o mesmo tipo de conteúdo do lembrete (data, hora, nome da prescritora e o link) e a mesma preferência de e-mail: o e-mail tinha dito "sua consulta é amanhã" e o cancelamento só aparecia no sistema, que quem lê e-mail não abre. O e-mail da remarcação também diz um horário, então cancelar ou remarcar de novo depois dele vai por e-mail outra vez. Nas outras consultas, inclusive na que teve o lembrete só no sistema porque o e-mail estava desligado, e nos outros avisos, nada muda: o cancelamento e a remarcação ficam só no sistema, a troca só da hora no mesmo dia também, e o paciente que cancela a própria consulta não recebe e-mail (decisão de 14/11/2026, revisão das regras R11). O paciente de prescritor desativado não recebe o lembrete enquanto a conta dele estiver desativada: é uma pausa, a consulta continua marcada e volta a ter lembrete com a conta reativada (07/11/2026). O paciente arquivado também não recebe, mesmo com a consulta continuando na agenda (07/11/2026). Reativar o prescritor ou o paciente arquivado depois do job das 8h não envia na hora o lembrete da consulta de amanhã: o lembrete volta na próxima rodada do job em que ainda couber, porque reativar um prescritor dispararia de uma vez o lembrete de todos os pacientes dele, a qualquer hora, até de noite (decisão de 17/11/2026).
- **Lembrete de formulário:** a escala que vence em até dois dias e ainda não teve resposta nenhuma gera um lembrete, um por tarefa, sem repetir. Quem já começou a responder não recebe cobrança. A escala enviada à mão que vira rodada do acompanhamento ganha prazo novo e volta a ter lembrete para ele (16/10/2026). A reaberta pela anulação também, fora do diário, com pelo menos até depois de amanhã para responder de novo (16/10/2026). A tarefa de prescritor desativado continua pendente, mas o lembrete dela só sai depois que a conta dele for reativada, porque ninguém leria a resposta (07/11/2026). Na ficha diária e no diário do sono, que um dia preenchido calava, dois dias seguidos em branco (ontem e anteontem) geram um lembrete, com as duas datas e o link para a grade naquele dia, um só por sequência: a sequência volta a cobrar só depois de um dia preenchido, também quando passa de uma semana para a seguinte. Os dois últimos dias do período também contam, no job do dia seguinte ao fim, porque a semana que fechou ainda está na grade, e o último dia de uma semana com o primeiro da seguinte também são dois dias seguidos, com o link no primeiro dia da semana nova, porque a anterior já saiu da grade. A semana que o prescritor interrompeu, no arquivamento ou tirando a escala do acompanhamento, não cobra, nem depois de o paciente ser reativado ou de o acompanhamento novo ser encerrado (18/11/2026); o diário enviado à mão depois do arquivamento cobra como os outros. O lembrete do prazo que saiu no mesmo dia já conta por ela, e o e-mail não diz qual formulário (decisão de 14/11/2026, revisão das regras R6).
- **E-mail:** os dois lembretes também saem por e-mail, respeitando a preferência de cada conta (RF18). Saem também o cancelamento e a remarcação da consulta cujo lembrete já foi por e-mail, como diz o *Lembrete de consulta* acima (decisão de 14/11/2026, revisão das regras R11). Os outros avisos ficam só dentro do sistema, como o §8.3 decidiu. Sem `MAIL_HOST` configurado, a mensagem continua indo para o log da API.
- **Sem aviso repetido:** a consulta e a tarefa guardam a data em que o aviso foi criado, então o job do dia seguinte não manda de novo. O e-mail sai depois que o banco confirma e, se falhar, não é tentado de novo; o log diz de qual conta era (02/10/2026).
- **Lista de avisos:** uma tela só para os dois perfis, paginada de 20 em 20 (RNF06), com o número de não lidos, marcar como lido ao abrir, marcar todos e apagar. Os filtros avançados ficaram de fora, como o §8.4 decidiu.

**Exportação concluída em 14/09/2026:** RF33, que consolidou os RF16 e RF17 da v1.0.

- **Um arquivo por tipo:** consultas, prescrições, escalas respondidas e anamnese saem em CSV separados, e a série de evolução sai da própria tela de progresso, com a escala e o período já escolhidos.
- **Formato:** ponto e vírgula como separador e marca de UTF-8 no começo, para o arquivo abrir com acento certo na planilha. Texto clínico com quebra de linha não estraga as colunas.
- **Escalas e anamnese:** uma linha por item respondido, com a data, a escala, o escore e a faixa, que é o formato que a planilha filtra e agrupa.
- **Anulação:** os quatro arquivos marcam o registro anulado e trazem o motivo da anulação, que é o que separa um erro de digitação de uma correção clínica. Nas escalas e na anamnese o motivo se repete em cada linha do registro. No arquivo que o paciente baixa, a consulta e a prescrição anuladas saem só com a data, a marca e o motivo, com o resto da linha em branco, porque podem ser de outra pessoa lançada no paciente errado; o arquivo do prescritor continua inteiro (decisão de 14/11/2026, revisão das regras R7).
- **Código ao lado do texto e o lado melhor da nota (decisão de 14/11/2026, revisão das regras R16):** o RF33 diz que o paciente leva os arquivos a outras consultas, e o `progress.csv` trazia só "Ansiedade; 8", sem dizer que ali o 10 é "tranquilo", enquanto na dor o 10 é "dor intensa": o médico que recebia 2, 4 e 8 lia piora. Cada linha da série ganhou o significado do mínimo e do máximo, como a tela mostra ("0 = muito ansioso" e "10 = tranquilo"; no escore, a primeira e a última faixa do instrumento), e se o maior é melhor ("sim" ou "não"; em branco na contagem e nos minutos, que não têm âncora no papel). Situação e modalidade no `appointments.csv`, situação e espectro no `prescriptions.csv` e o atributo no `progress.csv` saem com o texto da tela e, na coluna ao lado, o código (`REMOTA`, `NAO_COMPARECEU`, `VIGENTE`, `FULL_SPECTRUM`, `ANSIEDADE`), que é o que não muda para quem filtra na planilha. A receita anulada sai com o código `ANULADA`, e não com o `SUBSTITUIDA` que a anulação grava. O peso da consulta e da anamnese e a concentração da composição já saíam com vírgula decimal (13/11/2026). Faltavam as horas do `scales.csv` (*Horas de sono por noite* no PSQI e *Tempo fora de casa* no diário), que aceitam meia hora e saíam como `6.5` e `7.0`: agora saem como `6,5` e `7`, na resposta e no código. Os outros números dos arquivos são inteiros. A resposta aberta para leitura na tela ainda mostrava essas horas com ponto (`6.5`) e passou a mostrar `6,5`, como o arquivo (18/11/2026).
- **Composição do óleo:** sai como na tela, por exemplo `CBD 10% + THC 0,3%`, com vírgula decimal e a unidade escrita. O banco guarda três casas, e `10.000` seria lido no Brasil como dez mil.
- **Modo sem identificação:** é do prescritor, para pesquisa. Sai sem nome, CPF, e-mail, telefone e endereço, com o paciente e o prescritor identificados só por um número (RNF04). Antes de 15/10/2026 se chamava "modo anônimo", mas não é anonimização: o número é o id do banco e a clínica sabe de quem é, as datas saem como estão e o texto livre sai como foi escrito e pode citar nomes. Por isso o arquivo continua sendo dado pessoal sensível de saúde (LGPD, art. 5º, II, e art. 12). O termo técnico mais próximo é pseudonimização, que a LGPD só define no art. 13, § 4º, para estudos em saúde pública feitos por órgão de pesquisa. O texto do consentimento de pesquisa diz o mesmo ao paciente (versão `2026-10-rascunho-2`), e a API recusa o parâmetro antigo `anonymous`, para uma aba desatualizada não baixar o arquivo com nome. Quando o paciente não aceitou a pesquisa, a dica da janela de exportação diz que só paciente maior de idade pode aceitar, no perfil dele: antes dizia que o paciente podia aceitar no perfil, o que não vale para menor de 18 anos (RF37) e podia levar o prescritor a orientar errado a família (decisão da dona do projeto, novembro de 2026).
- **PDF pela impressão:** em vez de gerar PDF no servidor, existe uma página de impressão com cabeçalho de paciente, prescritor, período e data de emissão, e o histórico do período. O navegador imprime ou salva em PDF. No papel o texto corre de uma folha para a outra, e a primeira folha já traz as consultas: segurar uma seção ou um cartão inteiro jogava ele para a folha seguinte e deixava a anterior em branco. Só o topo de cada cartão e cada linha da tabela de escalas não se partem; o topo do cartão fica na mesma folha que a linha de pressão, peso e altura e o começo do registro, e nenhum título fica sozinho no pé da folha. Desde 13/11/2026 o cartão impresso não tem borda nem espaço antes do topo (a moldura vira um contorno, que não ocupa lugar) e os campos vêm um embaixo do outro: o Chrome quebrava a folha dentro da borda de cima do cartão, deixando o título sozinho no pé da folha com a moldura vazia embaixo, e, com os campos em duas colunas, jogava a linha inteira para a folha seguinte e deixava meia folha em branco.
- **Trilha:** cada arquivo baixado é uma linha própria na trilha de auditoria, com a operação *Exportação*, quem baixou, quando, o tipo do arquivo (por exemplo *Consultas (appointments.csv)*) e se foi com identificação ou *sem identificação, para pesquisa* (RF31, decisão de 14/11/2026, revisão das regras R19). Antes exportar contava como uma abertura de prontuário, que grava uma linha por paciente a cada 30 minutos: baixar os arquivos logo depois de abrir o histórico não deixava marca nenhuma, e a clínica não conseguia dizer quem tirou o prontuário do sistema, quando, nem se os dados de alguém foram para o estudo. A janela de 30 minutos continua valendo só para abrir telas. O arquivo que o próprio paciente baixa também entra, porque também sai do sistema. Na trilha da administração a exportação aparece como *Prontuário*, sem o tipo do arquivo, como os outros registros clínicos. Imprimir não entra, porque o navegador imprime qualquer tela.
- **Consultas no CSV (07/11/2026):** o `appointments.csv` passou a seguir a regra do histórico e da impressão: entra a consulta que já passou e foi atendida, estava marcada ou virou falta; pedido sem resposta, recusado, cancelada e consulta futura ficam de fora. Antes o arquivo trazia toda consulta do paciente e misturava pedido com atendimento feito, enquanto o histórico já filtrava. O modal de exportação diz o que vai no arquivo.
- **Receita inteira no CSV (07/11/2026):** o `prescriptions.csv` trazia só óleo, composição, posologia e vigência, e a linha com posologia "conforme o plano" ficava sem dose nenhuma. Ganhou o prescritor (só o número dele no modo sem identificação), o plano de dose como texto numa célula só, as instruções de uso, as precauções e os efeitos esperados (RF05).
- **Prescrição em uso na impressão (07/11/2026):** a impressão filtrava as prescrições pela data da consulta, e a vigente emitida há mais de 90 dias, comum porque o tratamento dura de 90 a 180 dias, sumia da impressão padrão: quem recebia o papel lia "Nenhuma prescrição no período" e concluía que o paciente não toma nada. A receita em uso é estado, não histórico, então ganhou a seção *Prescrição em uso* antes da lista, independentemente do período; quando também está no período, aparece nos dois lugares.
- **Anamnese mais recente na impressão (decisão de 14/11/2026, revisão das regras R10):** com mais de 90 dias de tratamento, o papel padrão dizia "Nenhuma anamnese no período", e o médico que recebia a impressão não via os remédios em uso, as reações a medicamentos e os diagnósticos anteriores, num tratamento em que o canabidiol interage com remédios como varfarina e valproato. A impressão ganhou a seção *Anamnese mais recente*, logo depois da *Prescrição em uso*, com a última ficha não anulada, independentemente do período e com a data de preenchimento à vista, que mostra que é um relato daquela data. Quando ela também está no período, aparece nos dois lugares. O padrão de 90 dias continua.
- **Escala semanal na borda do período (decisão da dona do projeto, novembro de 2026):** os acompanhamentos semanais de dor e de TEA entravam no período, na impressão e no gráfico de progresso, pelo primeiro dia da semana a que se referem. A resposta dada dentro do período sobre uma semana que tinha começado até seis dias antes do corte não saía no papel nem no gráfico, enquanto a consulta do mesmo dia saía. Agora a resposta entra quando a semana tem ao menos um dia dentro do período, nos dois lugares e no `progress.csv`: na ponta de trás, basta o último dia da semana cair no dia do corte; na ponta da frente, o período termina hoje, e a semana que começa hoje ou que ainda está em andamento entra. A semana que terminou um dia antes do corte continua de fora. As escalas de um dia só (ficha de acompanhamento, diário do sono, Hamilton, Pittsburgh e MEEM) não mudam, e o dia continua sendo o de Brasília. A API compara o fim da semana com o começo do período e o começo da semana com hoje; a impressão compara o fim da semana com o começo do período.
- **MEEM fora do arquivo do paciente (13/11/2026):** o `scales.csv` que o paciente baixa sai sem as linhas do Mini-Exame do Estado Mental, o `progress.csv` do escore do exame pedido por ele é recusado com 403 e a impressão aberta por ele não traz o exame, porque usa as mesmas rotas. O arquivo e a impressão do prescritor continuam com o MEEM. Isso não esconde dado do titular: o prontuário completo, com o exame, a clínica entrega a pedido do paciente ou do responsável, com o prescritor exportando ou imprimindo (LGPD, art. 18; RF33, §8.3).

Com isso, a ordem de trabalho do §8.6 está concluída. O que segue em aberto não é módulo, e sim as perguntas que dependem da clínica, que ficam fora do repositório.

**RNF07 em 14/09/2026:** o backup ganhou script de cópia diária, script de restauração e o procedimento em `docs/backup-e-restauracao.md`, com a tabela onde cada restauração testada fica registrada. O requisito só fecha quando a primeira restauração for feita de verdade no servidor do piloto e anotada ali.

**Revisão geral em 14/09/2026:** com os módulos concluídos, o sistema foi revisto de ponta a ponta: rotas, permissões, telas e configuração.

- **Correção da anamnese:** o paciente corrige a ficha já enviada a partir do próprio histórico, como o RF19 pede. A API já aceitava a correção, mas nenhuma tela chegava a ela.
- **Correção de escala:** a resposta aberta para leitura leva à correção enquanto o prescritor não analisou. O MEEM deixou de aparecer como corrigível para o paciente, porque quem aplica é o prescritor (RN09).
- **Período salvo:** abrir um acompanhamento semanal já respondido mantém o fim do período que foi salvo, em vez de trocar pela data do dia.
- **Telas antigas:** o envio avulso de escalas e o acompanhamento de 90 dias foram reescritos no padrão das outras telas, com busca de escala e aviso da que já espera resposta.
- **Limpeza:** saiu uma rota de prescrições por consulta que nenhuma tela usava e os modais de confirmação passaram ao mesmo padrão de componente do resto do sistema.
- **Conferido sem mudança:** toda rota da API exige perfil e, quando o dado é de paciente, o vínculo com ele; o cookie da sessão é `HttpOnly` e `SameSite=Strict`; e o login bloqueia depois de tentativas erradas seguidas.

**Rotas em 14/09/2026:** as rotas da API passaram a seguir o inglês do código (`/patients`, `/appointments`, `/scales`) e as do site ficaram todas em português (`/entrar`, `/painel-paciente`, `/administracao`). O MEEM deixou de ter correção direta: aplicado com erro, o prescritor anula com motivo e aplica de novo, como já acontece com a consulta e a prescrição.

**Correção em 14/09/2026:** desligar o `open-in-view` na revisão geral quebrou a agenda, o prontuário e a lista de pacientes, porque vários controllers montam a resposta depois do serviço. Os testes não pegaram o erro porque rodam dentro de uma transação, que mantém o banco aberto até o fim. O `open-in-view` voltou a ficar ligado, e um teste novo abre as telas principais dos dois perfis com os dados de demonstração e sem essa transação.

**Segurança da sessão em 15/09/2026:** três pontos levantados na busca por falhas de segurança.

- **Sair da conta:** encerra as sessões abertas da pessoa em qualquer aparelho, mesmo que alguém tenha guardado o cookie, e toda sessão termina 12 horas depois do login, mesmo sendo renovada durante o uso. A renovação não salva a sessão que começou antes da saída, da troca de senha ou da troca de e-mail, nem a renovada no mesmo instante em que uma delas acontece (25/11/2026). O login feito com a senha antiga no mesmo instante da troca de senha também não fica valendo: se a troca chega ao banco antes de o login terminar, ele é recusado como senha errada, e se chega logo depois, a sessão cai com a troca. Quem entra com a senha nova continua dentro (27/11/2026).
- **Bloqueio de login:** saiu da conta. Antes, cinco senhas erradas travavam a conta por 15 minutos, e quem soubesse o e-mail de alguém conseguia impedir essa pessoa de entrar. Agora a contagem é por e-mail e endereço de origem, com um limite também por endereço, e e-mail sem cadastro é bloqueado do mesmo jeito, para a resposta não revelar quem tem conta. A contagem guarda só o hash do e-mail, e o login recusa e-mail com mais de 255 caracteres antes de contar: antes, cada login errado com um e-mail gigante ficava 15 minutos na memória da API (16/10/2026).
- **Origem das requisições:** requisição que muda dado usando o cookie da sessão só é aceita quando vem do endereço oficial do sistema, conferido pelo cabeçalho `Origin`. É uma segunda barreira além do cookie `SameSite=Strict`, para navegador antigo e para subdomínio vizinho, que o navegador trata como o mesmo site.

**Indicadores do piloto em 16/10/2026 (RF39):** a tela *Indicadores* do prescritor mostra os dois números do piloto, calculados com o que o sistema já grava, sem tabela nova. Só aparecem números somados dos pacientes do próprio prescritor, sem nome nem linha por paciente, por isso abrir a tela não entra na trilha como abertura de prontuário.

- **Período:** sem datas, vale os últimos 90 dias até ontem, um ciclo inteiro (RN01). O período termina no máximo ontem, porque o prazo de hoje ainda está correndo. Informar só uma das datas ou a inicial depois da final é recusado, como na agenda.
- **Pacientes no ciclo:** quantos pacientes têm um acompanhamento automático valendo hoje, com a conta ativa e fora do arquivo, ao lado do total de pacientes ativos hoje, contado no mesmo grupo (conta ativa e fora do arquivo). Um acompanhamento que terminou ontem sai na hora, mesmo antes do job das 8h encerrá-lo. Junto vem quantos pacientes têm formulário com prazo no período.
- **Convites:** os convites criados no período, sem os cancelados: quantos foram enviados, quantos viraram cadastro, quantos desses pacientes entraram em um acompanhamento e quantos estão com o link ainda válido e não contam como falha. O convite que venceu sem uso conta como enviado e não fica em aberto. A meta é todo convidado cadastrado e no ciclo. Um link vencido que é enviado de novo para a mesma pessoa conta duas vezes, porque o convite não guarda nome.
- **Formulários no prazo:** entram todas as tarefas com o prazo dentro do período, avulsas ou do acompanhamento, respondidas ou não. O MEEM não vira tarefa e não entra. A regra olha as datas e as respostas ligadas, não a situação da tarefa, então não depende de o job das 8h já ter fechado o prazo. Escala de período e avaliação inicial estão no prazo quando foram respondidas até o último dia; a pendente com o prazo vencido conta como fora do prazo; a avaliação inicial enviada depois do prazo conta como atrasada. A exceção à regra das datas é a tarefa que fechou como não respondida e recebeu a resposta ou a anamnese depois: ela continua fora do prazo (30/10/2026). A avaliação inicial que ainda estava no prazo no arquivamento fica fora da conta, como as outras tarefas no prazo (revisão das regras R22). A meta é 70%.
- **Diário:** conta como respondido com pelo menos um dia válido, a mesma regra que fecha a tarefa, mesmo que ela ainda esteja pendente. Um diário só com dias anulados fica fora do prazo. Os dias preenchidos aparecem à parte, contra os dias do prazo, para mostrar o quanto essa regra é generosa. Um dia preenchido entre a meia-noite e o job das 8h do dia seguinte ao prazo ainda conta, como na grade do paciente. O dia preenchido depois do job, até o fim do dia seguinte ao prazo, fica com a semana como atraso: entra nos dias preenchidos e não conta para o prazo, e a semana que fechou sem nenhum dia continua fora do prazo e sai das escalas vencidas, como a resposta atrasada das outras escalas (decisão de 14/11/2026, revisão das regras R6).
- **Resposta anulada:** não conta. A escala volta a pendente e só conta de novo com outra resposta. A que voltou com o prazo vencido ganha prazo novo, fora do diário, e conta por ele. A exceção é a avaliação inicial: anular a ficha não reabre a tarefa, que continua como respondida.
- **Pacientes arquivados:** nos formulários, continuam na conta, senão a desistência sumiria do número. Em *Pacientes no ciclo* eles ficam de fora. Arquivar encurta o prazo das tarefas abertas para o dia do arquivamento, pela mesma regra da escala tirada do acompanhamento (07/11/2026): o que já tinha vencido conta como atraso, e o que ainda estava no prazo e fechou sem resposta fica fora da conta e das escalas vencidas, porque quem interrompeu foi o prescritor. Antes ele contava como fora do prazo. A desistência continua aparecendo pelas rodadas perdidas antes do arquivamento (decisão de 14/11/2026, revisão das regras R22).
- **Arredondamento:** a porcentagem é arredondada para baixo, para 69,6% não aparecer como meta batida.
- **De fora por enquanto:** a separação entre envio avulso e acompanhamento e uma visão da clínica inteira para a administração. As duas saem das mesmas consultas, se forem pedidas.

**Ajustes nas telas em 16/10/2026:** correções que saíram da revisão das telas.

- **Horário de Brasília no navegador (RNF16):** o hoje e o agora das telas são os de Brasília, mesmo com o navegador em outro fuso. Valem para o dia que o diário abre, a data da anamnese, o início do acompanhamento, os períodos dos indicadores e da trilha, a idade e as comparações com o horário da consulta: se ela já começou ou já acabou e se ainda falta o prazo de 24 horas para o paciente cancelar. Antes, à noite, um navegador adiantado mandava o dia seguinte e a API recusava. Os horários continuam aparecendo como a API manda, no horário de Brasília. Sempre que o fuso do navegador não é `America/Sao_Paulo`, mesmo outro fuso de UTC-3 como `America/Fortaleza`, aparece "(horário de Brasília)" ao lado do horário: nas telas de consultas e do painel do paciente, inclusive nos avisos do painel, e na página de avisos, que o prescritor também vê. Assim quem está no AM, MT, MS, RO, RR, AC ou fora do país não lê 10:00 como o horário local. Os e-mails ainda vão sem essa indicação. O dia que a tela monta vale do começo do dia local, que nem sempre é meia-noite: onde o horário de verão pula a meia-noite (Açores, Beirute, Cairo) o dia da virada começa à 1h, e a grade do diário não trava mais nesse dia.
- **Plano de dose (RN02):** cada dose vai com a semana da linha que a prescritora vê na tela. A linha com observação e sem dose não sai calada: o formulário marca a dose dessa semana e pede para escrevê-la ou apagar a observação antes de enviar, para o texto clínico não se perder (13/11/2026). *Decisão de 14/11/2026, revisão das regras R23:* a semana vazia no meio do plano é recusada com "Preencha ou tire a semana N" na própria linha, porque "Semana 1: 2 gotas; Semana 3: 4 gotas" podia ser lido como parar a dose na semana 2, numa subida que precisa ser lenta. Tirar a linha sobe as semanas de baixo. As linhas vazias do fim continuam ficando de fora. A API recusa o plano que pula semana com 400 no campo da semana ("Preencha ou tire a semana N"). O formulário e o cartão da prescrição dizem "Semana 1 = primeira semana de uso", porque o óleo pode chegar semanas depois da consulta. O plano continua sem data de início.
- **Erro de campo:** a prescrição e o acompanhamento automático mostram no próprio campo o motivo da recusa da API, e o erro de um campo que não aparece na tela vai para o aviso de cima. A duração do acompanhamento já pede de 7 a 365 dias no campo, como a API.
- **Avisos:** apagar o único aviso da última página volta para a página anterior, em vez de mostrar a lista vazia sem os botões de página.
- **Termo atualizado no cadastro (RF36):** se o termo mudar com o cadastro aberto, a tela busca o texto novo e desmarca o aceite, para a pessoa ler e aceitar a versão nova.
- **Convite com outra conta aberta:** o link de convite aberto com alguém logado diz com qual conta o navegador está e oferece sair e continuar o cadastro com o mesmo convite. Antes o convite sumia e a pessoa caía na tela inicial da conta aberta, o que pega o responsável com dois filhos (RF38). A tela confere o convite antes: se ele já foi usado, venceu ou foi cancelado, a pessoa continua logada, vê que o convite não vale mais e volta para a conta aberta. Assim o paciente que abre de novo o link com que se cadastrou não sai da conta à toa. A conferência do convite passa mesmo quando a conta aberta é de paciente com uma versão nova do termo pendente, que só pode ler até aceitar, porque conferir o convite é rota pública e fica fora dessa trava. Se sair da conta falhar, a tela avisa e a pessoa continua logada, com o convite ainda no link, para tentar de novo em vez de cair na tela inicial da conta aberta e perder o convite.
- **Link de senha com outra conta aberta (RF35, RF02.2):** o link de senha nova e o link de primeiro acesso do prescritor, abertos com alguém logado, também dizem com qual conta o navegador está e oferecem sair e continuar com o mesmo link. Antes o link sumia e a pessoa caía na tela inicial da conta aberta, o que pega o administrador que entrega o link de primeiro acesso no computador da clínica.
- **Link aberto sem sessão:** quem abre uma tela do sistema sem estar logado, como a do link do lembrete por e-mail, passa pelo login e volta para essa tela, em vez de cair na tela inicial.
- **Termo atualizado com o aceite aberto (RF36):** se a versão do termo mudar de novo com a tela de aceite aberta, a tela busca o texto novo e o próximo aceite vai com a versão nova. Antes cada clique mandava a versão velha e era recusado até a pessoa sair da tela.
- **Sair da conta:** a tela só volta ao login quando a API confirma que a sessão acabou. Sem resposta da API aparece "Não foi possível sair da conta" e a pessoa continua logada, porque o cookie continuaria valendo e a próxima pessoa no mesmo computador entraria na conta dela. Uma sessão que já tinha acabado conta como saída.
- **Nome no menu:** o nome, ou o nome social, salvo no perfil aparece no menu lateral na hora, sem esperar a próxima tela.
- **Sair do perfil com mudança não salva (RF18):** com um dado pessoal mudado, um e-mail novo ou uma senha nova digitados e ainda não salvos, sair do perfil por dentro do sistema pergunta antes (*Sair sem salvar*), e recarregar ou fechar a aba faz o navegador perguntar, como na anamnese e nas escalas. O que já foi salvo e a mudança desfeita não perguntam. Vale para paciente, prescritor e administração. Antes o perfil saía sem perguntar e o que foi digitado se perdia (decisão da dona do projeto, novembro de 2026).
- **Lembrete por e-mail só para o paciente (RF18, RF34):** a opção de avisos por e-mail aparece só no perfil do paciente, o único que recebe lembrete por e-mail, e a API recusa a troca vinda de outra conta com `403`. Antes o prescritor ligava a opção e lia que receberia os avisos por e-mail, mas nenhum e-mail sai para ele.

---

## Anexo A — Algoritmos das escalas validadas

Este anexo é normativo. Nenhuma escala pode ser implementada com algoritmo diferente do especificado aqui (RN13).

Os instrumentos de referência são os que a clínica **efetivamente aplica**, versionados em `docs/scales/`:

| Arquivo | Instrumento |
| :--- | :--- |
| `anamnese.xlsx` | ficha de anamnese (formulário de triagem, 23 perguntas) |
| `acompanhamento-semanal.pdf` | ficha de acompanhamento semanal |
| `ham-a.pdf` | Escala de Ansiedade de Hamilton |
| `psqi-br.pdf` | Índice de Qualidade do Sono de Pittsburgh |
| `diario-sono.pdf` | diário de sono |
| `registro-dor.pdf` | acompanhamento semanal de paciente com dor |
| `registro-tea.pdf` | acompanhamento semanal de paciente com TEA |
| `meem.pdf` | Mini-Exame do Estado Mental |

Em caso de divergência entre este anexo e o formulário em `docs/scales/`, **o formulário prevalece** e o anexo deve ser corrigido: é o instrumento que a prescritora interpreta na prática.

### A.1 HAM-A — Escala de Ansiedade de Hamilton

14 itens, cada um de 0 (ausente) a 4 (muito grave). **Escore = soma simples dos 14 itens, faixa de 0 a 56.**

Itens: 1 humor ansioso · 2 tensão · 3 medos · 4 insônia · 5 comprometimento intelectual · 6 humor deprimido · 7 somatizações motoras · 8 somatizações sensoriais · 9 sintomas cardiovasculares · 10 sintomas respiratórios · 11 sintomas gastrointestinais · 12 sintomas geniturinários · 13 sintomas autonômicos · **14 comportamento durante a entrevista**

Faixas interpretativas, **conforme o formulário aplicado pela clínica** (`docs/scales/ham-a.pdf`, que referencia Hamilton M., 1959): abaixo de 9 sem ansiedade · 9 a 15 ansiedade temporária · 16 a 25 ansiedade moderada · acima de 26 ansiedade grave.

> A ficha põe de 16 a 25 em ansiedade moderada e "acima de 26" em ansiedade grave, então o escore 26 fica sem faixa no papel. O sistema põe o 26 em ansiedade grave (faixa de 26 a 56 em `ScaleCatalog.hamilton()`), o que ainda precisa ser confirmado com a clínica.

> Existem outras faixas em circulação para o HAM-A. O sistema deve usar as do instrumento que a prescritora efetivamente aplica, porque é contra elas que ela interpreta o resultado.

> Hoje a HAM-A é de autoaplicação no sistema (RN09): ela chega ao paciente como tarefa e é respondida em casa, inclusive o item 14, *Comportamento na entrevista*, que no papel é observado por quem entrevista. Sem o item 14 não sai escore, então o paciente, ou o responsável, se avalia num item que não existe fora da entrevista, e lê o escore com a faixa. Fica registrado assim até a clínica dizer quem aplica a HAM-A (decisão de 14/11/2026, revisão das regras R15).

*Atendido desde 12/09/2026:* o item 14, que faltava na v1.0 e na primeira implementação, está no formulário como "Comportamento na entrevista" (`ScaleCatalog.hamilton()`), e o escore soma os 14 itens, de 0 a 56 (`ScaleScorer.hamiltonScore`, coberto pelo `ScaleScorerTest`).

---

### A.2 PSQI — Índice de Qualidade do Sono de Pittsburgh

O índice global é a soma de **7 componentes**, cada um de 0 a 3. **Faixa de 0 a 21.** Corte: **> 5 indica qualidade de sono ruim.**

**C1 — Qualidade subjetiva do sono**
Avaliação geral informada. Muito boa = 0 · Boa = 1 · Ruim = 2 · Muito ruim = 3.

**C2 — Latência do sono**
Passo 1 — pontuar os minutos até adormecer: ≤ 15 = 0 · 16 a 30 = 1 · 31 a 60 = 2 · > 60 = 3.
Passo 2 — somar ao escore de frequência de "não conseguir adormecer em 30 minutos" (0 a 3).
Passo 3 — converter a soma: 0 = 0 · 1 a 2 = 1 · 3 a 4 = 2 · 5 a 6 = 3.

**C3 — Duração do sono**
Horas de sono por noite: > 7 = 0 · 6 a 7 = 1 · 5 a 6 = 2 · < 5 = 3.

**C4 — Eficiência habitual do sono** *(componente calculado)*
`eficiência = (horas dormidas ÷ horas na cama) × 100`, onde horas na cama é o intervalo entre o horário de deitar e o de levantar. Horas dormidas maiores que as horas na cama, o mesmo horário para deitar e levantar e mais de 16 horas na cama são recusados no preenchimento (decisão de 14/11/2026, revisão das regras R13), então a eficiência nunca passa de 100%.
≥ 85% = 0 · 75 a 84% = 1 · 65 a 74% = 2 · < 65% = 3.

**C5 — Distúrbios do sono**
Somar os escores de frequência (0 a 3) de **9 itens**: acordar no meio da noite ou de manhã muito cedo · levantar para ir ao banheiro · dificuldade para respirar · tossir ou roncar · sentir frio · sentir calor · ter sonhos ruins · sentir dor · **outros motivos**.
Converter a soma: 0 = 0 · 1 a 9 = 1 · 10 a 18 = 2 · 19 a 27 = 3.

**C6 — Uso de medicação para dormir**
Escore de frequência informado, de 0 a 3, aplicado diretamente.

**C7 — Disfunção diurna**
Somar a frequência de dificuldade para se manter acordado (0 a 3) com a dificuldade de manter entusiasmo (0 a 3).
Converter: 0 = 0 · 1 a 2 = 1 · 3 a 4 = 2 · 5 a 6 = 3.

**Índice global = C1 + C2 + C3 + C4 + C5 + C6 + C7**

*Atendido desde 12/09/2026:* os quatro ajustes que este anexo pedia estão no motor de escalas.
- A **frequência do item "outros motivos"** existe ao lado do campo descritivo (`freqOutrasRazoes` em `ScaleCatalog.pittsburgh()`) e entra no C5 com os outros 8 itens
- O índice global é a soma dos 7 componentes, de 0 a 21, e não mais a soma crua de 13 campos (`ScaleScorer.pittsburghScore`)
- O C4 é calculado das horas de sono e dos horários de deitar e levantar, e não perguntado (`ScaleScorer.pittsburghEfficiency`)
- A presença de parceiro de quarto continua no formulário como contexto e **não compõe** o índice global (teste `pittsburghNaoMudaComOParceiroDeQuarto`)

---

### A.3 MEEM — Mini-Exame do Estado Mental

**Escore = soma das seções, total de 0 a 30.**

| Seção | Pontos | Conteúdo |
|---|---|---|
| Orientação temporal | 5 | hora aproximada, dia da semana, dia do mês, mês, ano |
| Orientação espacial | 5 | local específico, instituição, bairro ou endereço, cidade, estado |
| Registro | 3 | repetir três palavras apresentadas |
| Atenção e cálculo | 5 | subtrair 7 sucessivamente de 100 (93, 86, 79, 72, 65) |
| Memória de evocação | 3 | lembrar as três palavras anteriores |
| Nomeação | 2 | nomear dois objetos comuns |
| Repetição | 1 | repetir uma frase exata |
| Comando de três etapas | 3 | executar comando em três passos |
| **Leitura** | **1** | ler e executar "feche os seus olhos" |
| **Escrita** | **1** | escrever uma frase completa com sentido |
| **Cópia** | **1** | copiar dois pentágonos com interseção |
| **Total** | **30** | |

Enunciados exatos do formulário aplicado pela clínica (`docs/scales/meem.pdf`), que a tela deve reproduzir:

| Seção | Enunciado |
| :--- | :--- |
| Registro / evocação | repetir as palavras **CARRO, VASO, TIJOLO** |
| Atenção e cálculo | subtrair 7 sucessivamente a partir de 100, em **cinco** passos: 93, 86, 79, 72, 65 |
| Nomeação | **relógio** e **caneta** |
| Repetição | "Nem aqui, nem ali, nem lá" |
| Comando de três estágios | "Apanhe esta folha de papel com a mão direita, dobre-a ao meio e coloque-a no chão" |
| Leitura | "Feche os seus olhos" |
| Escrita | escrever uma frase que tenha sentido |
| Cópia | copiar os dois pentágonos com intersecção |

> O formulário impresso da clínica traz a sequência de subtração como "100-7 = 93-7 = 86-7 = 79-7 = 65", omitindo o 72 por erro de digitação. O sistema implementa os cinco passos corretos.

Faixas interpretativas variam por escolaridade; o resultado é sempre exibido com a faixa correspondente (RN14). Os cortes são de adultos: o exame de quem tinha menos de 18 anos na data da consulta sai sem faixa, com "Instrumento não validado para a idade" (decisão de 14/11/2026, revisão das regras R14, RF26).

Pontos de corte por escolaridade usados pelo sistema (Brucki 2003, em `ScaleScorer`). Escore igual ou maior que o corte fica "Dentro do esperado para a escolaridade", e menor fica "Abaixo do esperado para a escolaridade", sempre com o corte ao lado.

| Escolaridade | Corte |
| :--- | :--- |
| Analfabeto | 20 |
| 1 a 4 anos de estudo | 25 |
| 5 a 8 anos de estudo | 26,5 |
| 9 a 11 anos de estudo | 28 |
| Mais de 11 anos de estudo | 29 |

> O formulário da clínica (`docs/scales/meem.pdf`) não traz pontos de corte. Estes são os da decisão de 13/09/2026 e ainda precisam ser confirmados com a clínica (#27).

*Atendido desde 12/09/2026:* leitura, escrita e cópia, que constavam do RF26 da v1.0 e faltavam na primeira implementação (com teto de 27 pontos), estão no formulário do prescritor (`ScaleCatalog.mentalStateExam()`), e o escore soma as 11 seções, de 0 a 30, com o ponto de corte por escolaridade (`ScaleScorer.mentalStateExamResult`).

---

## Anexo B — Ordem de execução recomendada

A sequência é deliberada: segurança antes de funcionalidade, porque cada tela nova amplia a superfície de um problema que hoje é total; validade clínica antes de gráfico, porque gráfico bem feito sobre número inválido é pior do que não ter gráfico.

| Fase | Objetivo | Requisitos |
|---|---|---|
| **0** | Destravar o ambiente e a verificação | RNF13, RNF15 |
| **1** | Fechar a exposição de dado clínico | RF29, RF30, RF02.2, RN12, RNF03, RNF04, RNF05 |
| **2** | Garantir integridade do dado | RF31, RN04, RN10, RN11, RNF14 |
| **3** | Corrigir validade clínica | RF21, RF23, RF26, RN13, RN14 |
| **4** | Completar o que está pela metade | RF03, RF08, RF24, RF25, RF27, RF28, RF33 |
| **5** | Entregar a automação prometida ao cliente | RF32, RF34, RF15 |
| **6** | Refinamento | RF05, RF10, RF11, RF18, RNF12, RNF08 |

**Fase 0 concluída em 12/09/2026:**

- Ambiente de build validado com Eclipse Temurin JDK 17.0.20.1 e o wrapper Maven do repositório (3.9.10)
- **RNF15:** segredos fora do arquivo versionado, configuração por variável de ambiente, `docker compose up` sobe banco, API e interface
- **RNF13:** perfil `test` com H2 em memória. `./mvnw clean install` passa sem PostgreSQL instalado e sem nenhuma variável de ambiente definida — o comando que antes falhava sempre

**Fase 1 concluída em 12/09/2026:** RF29, RF30, RF31 (parcial), RF02.2, RN12, RNF03, RNF04 e RNF05 (o RNF05 só fechou de fato em 13/10/2026, com o TLS no nginx, #57). Nenhuma entidade de usuário atravessa a API, autorização por papel e por vínculo em todas as rotas, dono verificado por registro nas escalas, e falha de autenticação responde 401.

**Fase 4 (parcial) e Fase 3 concluídas em 12/09/2026:** RF21, RF23, RF26, RF27, RF28, RN09, RN13 e RN14, além do religamento das telas de consulta e prescrição. Os três escores clínicos passaram a seguir o algoritmo oficial do instrumento, as oito escalas têm tela, e o gráfico de evolução funciona para qualquer uma delas.

**Fase 2 concluída em 12/09/2026:** RN04, RN10, RN11, RNF14 e a parte de datas do RF31. Escala incompleta ficou sem escore, texto clínico deixou de truncar em 255, CPF virou único, toda entidade clínica guarda quando foi criada e alterada, e o esquema passou a ter uma fonte de verdade só, com o Hibernate em `validate`.
