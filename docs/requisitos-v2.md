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

---

**RF02.1 — Cadastro de paciente** · `mantido` · **E** · Paciente

Autoatendimento. O paciente informa nome completo, CPF, e-mail, data de nascimento, telefone, endereço, senha e o **código do prescritor** que o acompanhará.

*Critérios de aceite:*
- E-mail e CPF são únicos no sistema (ver RN04)
- Senha com mínimo de 8 caracteres, uma maiúscula, um número e um caractere especial
- CPF validado por dígito verificador
- Código de prescritor inexistente rejeita o cadastro com **400** e mensagem clara
- O vínculo paciente→prescritor é criado no cadastro e é imutável pelo próprio paciente (ver RN05)

---

**RF02.2 — Provisionamento de prescritor** · `alterado` · **E** · Administrador

Contas de prescritor são criadas por administrador, por seed de inicialização ou por fluxo de convite. **Não há endpoint público de cadastro de prescritor.**

Dados obrigatórios: nome completo, CPF, e-mail, data de nascimento, telefone, endereço, profissão e registro profissional (tipo + número: CRM, COREN, CRBM, CRP).

*Critérios de aceite:*
- A combinação tipo + número de registro é única
- O sistema gera um **código de vínculo** único por prescritor (ver RN06)
- Requisição anônima a qualquer rota de criação de prescritor retorna **401/403**
- O administrador **não escolhe nem conhece** a senha do prescritor: a conta nasce com um link de primeiro acesso de uso único (48 h), enviado por e-mail ou, sem SMTP, mostrado ao administrador para entregar. Quem cria a senha é o próprio prescritor
- O seed de desenvolvimento roda **somente** no profile `dev` e nunca em produção

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

*Ampliação:* a tabela de necessidades da v1.0 exigia "histórico de alterações" do produto prescrito e "controle de ajuste de dose: histórico de ajustes, datas, justificativas, inclusive casos de desmame". O RF05 original não mencionava versionamento e a implementação sobrescreve a prescrição.

---

**RF19 — Anamnese** · `mantido` · **E** · Paciente

Formulário de triagem com: profissão, motivo principal da consulta, diagnósticos prévios, tratamentos anteriores, medicações em uso, tipo de dieta, tabagismo, uso de álcool, peso, altura, uso de substâncias recreativas, exercícios físicos, qualidade do sono, ansiedade, dor, histórico familiar, reações adversas, condições genéticas, expectativas com o tratamento, formas de monitoramento e observações gerais.

*Critérios de aceite:*
- Identificador único e data de preenchimento
- Campos descritivos aceitam texto longo sem truncamento (RN11)
- Preenchimento dá baixa automática na tarefa correspondente (RF08)

---

**RF12 — Histórico clínico (paciente)** · `mantido` · **I** · Paciente

Acesso ao próprio histórico completo: anamnese, diagnósticos, tratamentos prescritos, evoluções e prescrições anteriores. Exportável (RF33).

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

---

**RF11 — Agenda (prescritor)** · `mantido` · **I** · Prescritor

Visualização e gestão da própria agenda, criação e edição de consultas, com notificação ao paciente em caso de alteração.

*Critérios de aceite:*
- Alteração ou cancelamento notifica o paciente (RF14)
- Só é possível agendar para pacientes vinculados (RF30)

---

### 4.5 Módulo — Escalas e Acompanhamento

---

**RF08 — Central de escalas (paciente)** · `ampliado` · **E** · Paciente

Tela central listando as avaliações pendentes e o histórico. Pendentes em cards, com nome da escala, indicador de status e botão de preenchimento. Histórico em tabela, com nome, data de conclusão, **resultado** e ações (ver respostas, ver progresso).

*Critérios de aceite:*
- A coluna **resultado** exibe o escore real da escala com sua faixa interpretativa. A implementação atual exibe a string fixa `"Concluído"` — este critério existe para fechar esse defeito
- "Ver progresso" leva à tela de progresso já filtrada naquela escala (RF27)
- **Toda escala designável possui tela de preenchimento acessível.** Hoje três tipos (`ESCALA_PITTSBURGH`, `REGISTRO_DOR`, `REGISTRO_TEA`) apontam para rotas inexistentes
- Escala aplicada pelo prescritor (MEEM) não aparece como tarefa do paciente (RN09)

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

---

**RF23 — Índice de Qualidade do Sono de Pittsburgh (PSQI-BR)** · `alterado` · **E** · Paciente

Avaliação do sono do último mês. O sistema calcula o índice global conforme o **algoritmo oficial de 7 componentes** (Anexo A.2).

*Critérios de aceite:*
- O índice global é a soma de 7 componentes derivados, cada um de 0 a 3, **total de 0 a 21**
- O componente de eficiência habitual do sono é **calculado** a partir de horas dormidas ÷ horas na cama, não coletado
- Ponto de corte interpretativo: escore > 5 indica qualidade de sono ruim
- Existe teste automatizado com caso de referência publicado
- A entidade recebe o campo faltante de **frequência do item "outros motivos"**, necessário para compor o componente de distúrbios do sono

*Justificativa:* a primeira implementação somava 13 campos crus, gerando faixa de 0 a 39, incomparável a qualquer ponto de corte publicado, e o próprio código documentava a limitação em comentário. Como o valor era apresentado à prescritora rotulado como índice de qualidade do sono e podia influenciar ajuste de dose, a soma crua é inaceitável. O cálculo pelos 7 componentes está no sistema desde 12/09/2026 (Anexo A.2).

---

**RF24 — Acompanhamento semanal de paciente com TEA** · `mantido` · **I** · Paciente

Registro do período avaliado e autoavaliação da qualidade de vida (0 a 10). Frequência de comportamentos na última semana em quatro níveis — nenhum dia, até 3 dias, entre 3 e 6 dias, todos os dias — para: agressividade/impulsividade, agitação psicomotora/ansiedade, sono, interação social, estereotipia e apetite. Campo de observação opcional.

*Critérios de aceite:*
- **Possui tela de preenchimento acessível.** Existe no backend, sem interface

---

**RF25 — Acompanhamento semanal de paciente com dor** · `mantido` · **I** · Paciente

Período avaliado (`De __/__/__ A __/__/__`) e intensidade da dor em escala visual de 0 a 10. Frequência de interferência da dor — nenhum dia, até 3 dias, entre 3 e 6 dias, todos os dias — em: atividades básicas (comer, levantar, tomar banho), atividades sociais (sair com amigos, passear, ficar com a família), produtividade no trabalho, sono e necessidade de medicação extra para dor. Identificador único e observação opcional.

*Critérios de aceite:*
- **Possui tela de preenchimento acessível.** Existe no backend, sem interface
- Faixas da escala visual **conforme o formulário aplicado** (`docs/scales/registro-dor.pdf`): **leve 0 a 2 · moderada 3 a 7 · intensa 8 a 10**. A v1.0 especificava 1–3 leve, 4–6 moderada e 7–10 intensa, que não são as do instrumento em uso
- A escala é apresentada visualmente, com as três faixas nomeadas e coloridas, não como um campo numérico solto. *Atendido em 16/10/2026: o formulário genérico desenha a nota com as faixas da definição — Dor leve (0 a 2) em azul, Dor moderada (3 a 7) em âmbar e Dor intensa (8 a 10) em vermelho, cores que seguem o formulário impresso (no papel a faixa moderada vai do verde ao âmbar; na tela foi simplificada num âmbar só) — com o nome e o intervalo de cada faixa escritos em cima das notas dela, então a cor não é o único sinal. Vale para qualquer item de nota que vá de ponta a ponta das faixas da escala; no HAM-A as faixas são do total de 0 a 56 e o item de 0 a 4 continua com a régua simples (`ScaleForm`)*
- O campo de observação exibe a orientação do formulário: relato de efeito colateral, ausência de tomada de alguma medicação, momento significativo na rotina

---

**RF26 — Mini-Exame do Estado Mental (MEEM)** · `alterado` · **E** · Prescritor

Aplicado pelo prescritor durante a consulta. Registra nome do paciente, data da avaliação e a pontuação de cada seção. O sistema calcula o total automaticamente.

*Critérios de aceite:*
- **Pontuação máxima de 30 pontos**, com todas as seções do instrumento (Anexo A.3): orientação temporal (5), orientação espacial (5), registro (3), atenção e cálculo (5), memória de evocação (3), nomeação (2), repetição (1), comando de três etapas (3), **leitura (1)**, **escrita (1)** e **cópia dos pentágonos (1)**
- Faixa interpretativa ajustada por escolaridade
- Exame com seção ou escolaridade em branco não é gravado: a tela aponta o que falta e a API recusa o envio. É a exceção à RN10: como o MEEM não tem correção, um exame incompleto ficaria sem escore ou sem faixa (RN14)
- A primeira implementação contemplava 8 seções, com máximo de **27 pontos** — faltavam leitura, escrita e cópia, todas descritas no RF26 da v1.0, e um teto de 27 desloca todas as faixas de interpretação. As 11 seções, de 0 a 30, estão no sistema desde 12/09/2026 (Anexo A.3)
- Vinculado à consulta que o originou, e só depois que ela aconteceu: em consulta futura o envio é recusado
- Não é designável como tarefa ao paciente (RN09)

---

### 4.6 Módulo — Evolução e Exportação

---

**RF27 — Tela de progresso (paciente)** · `mantido` · **E** · Paciente

Seletor de período no topo (15, 30, 60, 90 dias), seleção da **escala** e filtro do **atributo**. Visualização principal: gráfico de evolução do atributo selecionado (eixo Y) ao longo das datas de preenchimento (eixo X).

*Critérios de aceite:*
- **Existe interface.** Hoje o endpoint existe e nenhuma tela o consome
- O seletor de escala funciona para todas as escalas pontuadas, não apenas a ficha de acompanhamento
- Períodos sem dado aparecem como lacuna, nunca como zero (RN10)
- Adicionar uma escala nova não exige alterar o serviço de progresso (RNF08)
- O gráfico diz o que o 0 e o máximo significam e qual lado é melhor, com as âncoras do formulário da clínica (RF20). Em ansiedade o 10 é "tranquilo" e em dor o 10 é "dor intensa", então sem isso a curva subindo pode ser lida ao contrário. *Atendido em 16/10/2026: o catálogo de atributos traz as âncoras e o `higherIsBetter` do item, e a tela mostra, abaixo do nome do atributo, por exemplo "0 = muito ansioso · 10 = tranquilo · Maior é melhor". No escore, as pontas são a primeira e a última faixa do instrumento. Contagem e minutos não têm âncora no papel e ficam sem lado melhor, em vez de um inventado*
- Escore de escala validada no gráfico vem com a faixa (RN14). *Atendido em 16/10/2026: cada ponto do escore traz a faixa gravada na resposta, que aparece ao lado do valor no ponto do gráfico e no último registro. O MEEM, cuja faixa depende da escolaridade, traz também o ponto de corte da escolaridade de cada exame: quando é o mesmo no período inteiro, vira uma linha de corte no gráfico; quando há dois ou mais pontos de corte diferentes, a tela avisa que a escolaridade mudou e a faixa fica em cada ponto. Exame antigo sem escolaridade registrada vem sem corte e sem faixa e é contado à parte, sem ser lido como mudança de escolaridade: a tela avisa "Exame sem escolaridade registrada: sem faixa", e o ponto e o último registro mostram esse texto ao lado do escore*

---

**RF28 — Tela de progresso (prescritor)** · `mantido` · **E** · Prescritor

Mesma estrutura do RF27, para o paciente selecionado, com período adicional "todo o tempo". Restrito a pacientes vinculados (RF30). As âncoras, o lado melhor e a faixa de cada escore do RF27 valem igual aqui.

---

**RF33 — Exportação de dados** · `novo (consolida RF16 e RF17)` · **I** · Paciente, Prescritor

Exportação de histórico clínico, escalas respondidas e séries de evolução em **PDF** (leitura e impressão) e **CSV** (análise).

*Critérios de aceite:*
- O paciente exporta apenas os próprios dados, para levar a outras consultas
- O prescritor exporta dados dos seus pacientes
- Existe modo **sem identificação** para fins de pesquisa, sem nome, CPF, e-mail, telefone e endereço
- O PDF identifica paciente, prescritor, período e data de emissão

*Origem:* consolida os RF16 e RF17 da v1.0 e atende as necessidades "exportação de dados para pesquisa" e "padronização para estudos científicos" da tabela de §1.5, além do RNF09.

---

### 4.7 Módulo — Notificações e Lembretes

---

**RF14 — Notificações (paciente)** · `mantido` · **I** · Paciente

Notificação sobre compromissos agendados, alertas de consulta e formulários pendentes, configurável conforme preferência (RF18).

*Critérios de aceite:*
- Todo link de notificação aponta para rota existente. Hoje há links para rotas inexistentes
- O paciente vê apenas as próprias notificações

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
- Lembrete de consulta enviado com antecedência configurável
- Lembrete de formulário pendente respeita a periodicidade do protocolo (RF32), sem duplicar

*Justificativa:* foi a necessidade mais citada pelos pacientes na elicitação (v1.0 §1.5 e Tabela 2): *"Pacientes relataram dificuldade para lembrar de tomar as doses"*. A v1.0 menciona lembretes em RF10 e RF14, mas nunca como requisito próprio, e nada foi implementado.

---

## 5. Requisitos Não Funcionais

---

**RNF01 — Usabilidade** · `mantido` · **E**
Interface simples e de navegação fácil, adequada a usuários com diferentes níveis de familiaridade com tecnologia.

**RNF02 — Responsividade** · `mantido` · **E**
Telas responsivas em dispositivos móveis e desktop. *Prioridade de projeto no smartphone*: a elicitação registrou preferência unânime dos pacientes por celular.

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
| **RN02** | A conduta terapêutica segue escalonamento gradual de dose (*start low, go slow*): toda prescrição inicia na menor dose eficaz e é ajustada conforme resposta clínica |
| **RN03** | Formulações possíveis: **isolado** (CBD, THC, CBG), **broad spectrum** (sem THC) e **full spectrum**. A concentração de cada canabinoide é definida **caso a caso**, porque a resposta varia por paciente e por condição tratada — não existe formulação padrão. *Desde 13/09/2026 o espectro é um campo próprio e o óleo é descrito por uma lista de canabinoides, cada um com a sua concentração e unidade, mg/mL ou % (`PrescriptionComponent`); desde 02/10/2026 a tela abre em mg/mL, que é como a clínica cadastra hoje. Segue em aberto se o mg/mL é de cada canabinoide ou do produto: está nas perguntas para a clínica* |
| **RN04** | E-mail e CPF são únicos em todo o sistema. *Atendido em 12/09/2026: o CPF é único na entidade (`Users`) e na migração base (`V1__esquema_base.sql`), e o cadastro confere e-mail e CPF antes de gravar, recusando com uma mensagem só, sem dizer qual dos dois já tem conta (#82)* |
| **RN05** | Cada paciente está vinculado a exatamente um prescritor. O vínculo é estabelecido no cadastro pelo código do prescritor e só pode ser alterado por administrador |
| **RN06** | O código de vínculo do prescritor é único e **não é adivinhável**. *Alterado em 13/09/2026 (§8.3): o código de três letras e dois dígitos, que oferecia 90 combinações por prefixo, saiu do banco (`V3__remove_codigo_do_prescritor.sql`), e o vínculo passou a ser feito por convite de uso único, com 32 bytes sorteados e validade de 7 dias (`PatientInviteService`, `SecureTokens`). Desde 02/10/2026 o prescritor também lista e cancela os convites em aberto* |
| **RN07** | O prescritor não comercializa produtos. O sistema registra orientação quanto a marca, lote e concentração, sem função de venda |
| **RN08** | Dois agendamentos do mesmo prescritor não podem ocupar o mesmo horário |
| **RN09** | Escalas de **autoaplicação** (ficha de acompanhamento, HAM-A, PSQI, diário de sono, registro de dor, registro TEA, anamnese) são designáveis ao paciente. Escalas de **heteroaplicação** (MEEM) são aplicadas pelo prescritor em consulta e não geram tarefa ao paciente. *Atendido em 12/09/2026: o MEEM é marcado como aplicado pelo prescritor (`ScaleType`), o envio avulso e o acompanhamento de 90 dias recusam a escala (`ScaleTaskService`, `TreatmentProtocolService`) e o check `ck_scale_task_type` da `V13__escalas.sql` não aceita tarefa de MEEM* |
| **RN10** | Item de escala não respondido é registrado como **ausente**, nunca como zero. *Atendido desde 12/09/2026: item em branco não entra nas respostas gravadas (`ScaleResponseService`), escala pontuada sem todos os itens fica sem escore (`ScaleScorer`) e o gráfico de evolução deixa lacuna onde falta o item (`ProgressReportService`). A exceção é o MEEM, que incompleto não é gravado (RF26)* |
| **RN11** | Campo de texto clínico livre (observação, plano terapêutico, evolução, diagnóstico, campos descritivos da anamnese) não tem limite prático de tamanho. *Atendido em 12/09/2026: esses campos são `TEXT` na migração base e nas entidades, e os testes gravam 10.000 caracteres na evolução da consulta e numa resposta da anamnese sem truncar (`ConsultationRecordTest`, `AnamnesisTest`)* |
| **RN12** | Senha nunca é gravada em texto puro nem transita em resposta da API. *Atendido desde 12/09/2026: a atualização de perfil não recebe senha (`ProfileUpdateDTO`), toda senha gravada passa pelo `passwordEncoder` (cadastro, troca no perfil, link de senha nova ou de primeiro acesso e conta do administrador), e o hash não sai em resposta nenhuma, com `@JsonIgnore` em `Users.getPassword` e o `PasswordExposureTest` conferindo* |
| **RN13** | Escore de escala validada é calculado exclusivamente pelo algoritmo oficial do instrumento (Anexo A). Adaptação ou simplificação de algoritmo não é permitida: escore fora do instrumento não pode ser exibido com o nome do instrumento |
| **RN14** | O escore é sempre apresentado com sua **faixa interpretativa**, nunca como número isolado. *No gráfico de evolução desde 16/10/2026: cada ponto de escore traz a faixa gravada, e o MEEM traz o ponto de corte da escolaridade de cada exame (`ProgressReportService`). A série exportada em `progress.csv` ganhou a coluna "Faixa", preenchida nos escores do HAM-A, do PSQI, do MEEM e da intensidade da dor, inclusive no arquivo sem identificação* |
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
| RF02.2 | Conta administrativa mínima criada por variável de ambiente na primeira inicialização, responsável por criar e desativar prescritores. *Acrescentado em 02/10/2026:* o prescritor cria a própria senha por link de primeiro acesso, e o administrador desativa e reativa conta de paciente achando-a pelo e-mail, sem lista geral e sem ver prontuário |
| RN06 | O vínculo passa a ser feito por link ou QR de convite gerado pelo prescritor, aleatório e com validade |
| RF04 | *Critério acrescentado:* pressão, peso e altura são opcionais. A clínica não mede sinais vitais, porque a maioria das consultas é remota (01/10/2026) |
| RF05 | Nova prescrição substitui a vigente quando a consulta dela não é mais antiga que a da vigente; lançada em consulta anterior, entra só no histórico (vigente ou substituída). O histórico resulta da própria sequência, sem tabela de versões. O paciente acessa suas prescrições em tela própria |
| RF06/RF20 e RF22 | Um registro por dia, apresentado como a grade semanal do formulário em papel. O diário de sono inclui a programação de horários definida pelo prescritor |
| RF20 | *Critério acrescentado:* as âncoras de cada item são idênticas às do formulário da clínica |
| RF24/RF25 | O período avaliado é registrado com início e fim, como no formulário |
| RF07 | Consolidado com o RF28 em uma página única do paciente |
| RF10/RF11 | O prescritor cadastra a disponibilidade, o paciente solicita o horário e o prescritor confirma |
| RF18 | Restrito a dados pessoais, senha e preferência de e-mail, sem escolha de idioma |
| RF26 | *Critério acrescentado:* a escolaridade usada no ponto de corte e a data da avaliação são registradas |
| RF31 | Tabela de auditoria somente de inserção, gravada pelos serviços |
| RF33 | Exportação em CSV, com opção sem identificação, e página de impressão |
| RF34 | E-mail e notificação no sistema para formulário pendente e consulta. O lembrete de dose fica para fase posterior: a clínica ajusta a dose pelo WhatsApp, a partir das respostas do formulário, e a pergunta sobre lembrete segue aberta (01/10/2026) |
| RF14/RF15 | Lista de notificações com marcação de leitura, sem filtros avançados |
| RNF06 | Paginação apenas nas listagens que crescem sem limite: notificações e auditoria |
| RN10 | *Critério acrescentado:* o frontend nunca pré-preenche item de escala com valor |

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
  - o administrador, com as ações de prescritores e do sistema e o paciente identificado só pelo número. Esse número é o mesmo que a busca de conta por e-mail devolve, então, para o administrador, todo registro clínico aparece só como "Prontuário", sem dizer se foi escala, MEEM, anamnese, consulta ou prescrição (RF30, 16/10/2026). Os eventos de conta continuam com o tipo.
- **URLs:** nenhuma rota leva dado pessoal. Os caminhos usam só identificadores numéricos.

**Atendimento concluído em 13/09/2026:** RF04, RF05, RF19, RF12 e RF13, com o arquivamento de paciente.

- **Anulação:** consulta, prescrição e anamnese registradas por engano não são apagadas. O prescritor anula com motivo obrigatório e o registro continua no histórico, com data, motivo e autor. Uma consulta com prescrição ativa só é anulada depois da prescrição. Pelo mesmo motivo, a data da consulta não muda na correção do registro nem na remarcação pela agenda enquanto ela tem prescrição ou Mini-Exame não anulados: a prescrição vigente sai da consulta mais recente e o exame fica no dia da consulta (16/10/2026).
- **Consulta (RF04):** o prescritor registra a consulta que já aconteceu ou completa a que estava agendada. Consulta futura é assunto da agenda. Data com o ano digitado errado é recusada: consulta e anamnese antes de 2000 e data de nascimento antes de 1900 (16/10/2026).
- **Prescrição (RF05):** a nova prescrição substitui a vigente, e a anterior fica guardada como substituída. O óleo é descrito por uma lista de canabinoides, cada um com a sua concentração, até a clínica responder a questão 1. O paciente vê as próprias prescrições em tela própria.
- **Anamnese (RF19):** o paciente preenche e corrige a ficha. A data de preenchimento é a do dia, e o preenchimento dá baixa na tarefa enviada pelo prescritor. A correção e a anulação feitas ao mesmo tempo não se apagam: a que chega depois espera a outra, e a correção de uma anamnese que acabou de ser anulada é recusada (16/10/2026).
- **Histórico (RF12 e RF13):**
  - as duas telas mostram consultas, prescrições, anamnese e escalas com os dados reais;
  - o botão de exportar saiu do histórico do paciente até o RF33;
  - o prescritor anula registros no próprio histórico e chega de lá às outras telas do paciente.
- **Arquivamento de paciente:**
  - tira o paciente da lista de ativos e da contagem do painel;
  - encerra o acompanhamento automático, e um novo só começa depois de reativar;
  - fecha as escalas que estavam em aberto, para o paciente não ser cobrado delas (02/10/2026);
  - o paciente continua vendo o próprio histórico, e as escalas enviadas pelo prescritor continuam chegando, menos a avaliação inicial, que o arquivado não consegue mandar: o envio dela é recusado até reativar (16/10/2026);
  - arquivar e reativar entram na trilha de auditoria.
- **Escalas enviadas:** o paciente sempre recebe a escala que o prescritor envia. Reenviar uma escala ainda pendente não cria tarefa repetida. A escala que o paciente já respondeu hoje não é enviada de novo no mesmo dia: o envio é recusado com "Esta escala já foi respondida hoje. Veja a resposta no histórico do paciente", que aparece na própria escala na tela de envio, e as outras marcadas continuam podendo ir. Na escala pontual conta a avaliação com a data de hoje; nos acompanhamentos semanais de dor e de TEA, que falam da última semana, conta a semana que termina hoje. A resposta do dia é uma só, então a tarefa nova ficaria pendente sem o paciente conseguir fechar no dia. A resposta anulada, a de outra data (nos acompanhamentos semanais, a semana que terminou ontem) e o dia do diário não seguram o envio (16/10/2026).
- **Trilha de auditoria:** a abertura do prontuário gera uma linha por visita, mesmo quando a tela carrega várias listas ao mesmo tempo.

**E-mail configurado em 13/09/2026:** com `MAIL_HOST` definido, o link de recuperação de senha sai por e-mail pelo servidor SMTP. Sem ele, o link continua indo para o log da API.

**Troca do e-mail de acesso em 13/10/2026:** o e-mail só muda depois que a pessoa abre o link enviado para o endereço novo (RF18). Sem `MAIL_HOST` esse link também vai só para o log da API: a troca nunca é direta, porque é o link que prova que a pessoa recebe e-mail naquele endereço.

**HTTPS na pilha em 13/10/2026 (RNF05):** o TLS termina no nginx do compose com o certificado da clínica (pasta `certs/`), a porta 80 só redireciona, o cookie de sessão sai com `Secure` por padrão (`.env.example`) e o smoke do CI sobe a pilha em https com certificado autoassinado. O passo a passo do servidor está em `docs/guia-de-deploy.md`. O desenvolvimento sem docker continua em http://localhost:5173 com `SESSION_SECURE_COOKIE=false`.

**Responsável ou cuidador em 14/10/2026 (RF38):** nome, parentesco e telefone, todos opcionais, informados pelo próprio paciente no cadastro e no perfil. Aparecem só no cabeçalho do prontuário, para o prescritor do paciente, com link para ligar, e não saem na lista de pacientes nem em nenhuma exportação. O termo de consentimento passou a citar esse dado. Login do cuidador, lembrete para ele e aceite do termo por responsável de criança esperavam a resposta da clínica. O aceite pelo responsável foi resolvido em 16/10/2026 (nota abaixo); o lembrete para o responsável continua em aberto.

**Aceite pelo responsável legal em 16/10/2026 (RF36, RF37 e RF38):** a clínica respondeu que, no caso de criança, quem aceita o termo é o responsável legal, que ele preenche os formulários entrando com a conta do paciente e que basta uma pessoa, com nome, parentesco e telefone, sem e-mail. O limite ficou em 18 anos, e não em 12: pelo Código Civil, até os 16 anos a pessoa é representada pelos pais e dos 16 aos 17 é assistida por eles, então um aceite feito só pelo adolescente pode ser anulado.

- **Duas faixas de idade:** menor de 16 anos (absolutamente incapaz, Código Civil, art. 3º; LGPD, art. 14, § 1º, para menor de 12) tem o termo aceito pelo responsável legal, em nome do paciente. Com 16 e 17 anos (relativamente incapaz, art. 4º, I), o paciente aceita junto com o responsável, que assiste. A caixa do aceite continua uma só e o texto dela muda com a faixa: "Sou o responsável legal pelo paciente e aceito, em nome dele, ..." com o botão "Li e aceito como responsável" para menor de 16, e "Eu, paciente, e meu responsável legal lemos e aceitamos ..." com o botão "Lemos e aceitamos" para 16 e 17 anos. A tela do aceite de uma versão nova do termo usa os mesmos textos. Trocar no cadastro a data de nascimento para outra faixa (adulto, 16 e 17 anos ou menor de 16) desmarca o termo e a pesquisa, para a pessoa certa marcar o texto certo.
- **Idade:** a API calcula pela data de nascimento, no horário de Brasília, em anos completos (`Period`): quem nasceu em 29 de fevereiro faz 18 anos em 1º de março nos anos que não são bissextos (Código Civil, art. 132, § 3º), igual ao front. O front nunca manda se o paciente é menor; ele só usa a data para trocar os textos da tela.
- **Cadastro e perfil:** para menor de 18 anos, o grupo "Familiar ou cuidador (opcional)" vira "Responsável legal", com nome e parentesco obrigatórios. O telefone continua opcional. O perfil não deixa apagar o responsável de um menor, nem mudar a data de nascimento para a de um menor sem informar o responsável.
- **Aceite:** cada aceite de menor de 18 anos, nas duas faixas, guarda o nome e o parentesco do responsável daquele momento (`consent_acceptance.guardian_name` e `guardian_relationship`, `V29__aceite_pelo_responsavel.sql`), porque o responsável salvo no paciente pode mudar depois e a prova do consentimento é da clínica (LGPD, art. 8º, § 2º). Isso vale no cadastro e no aceite de uma versão nova do termo. Conta de menor sem responsável salvo, que só existe de antes dessa regra, informa o nome e o parentesco dele na própria tela do aceite, porque com o termo pendente o perfil não salva. O mesmo vale para a conta criada quando o parentesco ainda era opcional, com o nome do responsável e sem o parentesco: a tela traz o nome preenchido e pede o parentesco, e nenhum aceite de menor é gravado sem os dois. Adulto com cuidador, como um idoso, aceita sozinho e o aceite fica sem responsável.
- **Termo:** ganhou a cláusula em destaque "9. Paciente com menos de 18 anos", que cita a LGPD (art. 14) e o Código Civil, separa as duas faixas e diz que a clínica explica ao paciente, com palavras simples e adequadas à idade, como os dados são usados (por exemplo na consulta). A declaração final cobre as duas faixas. A versão passou para `2026-10-rascunho-2`, e quem aceitou a anterior lê e aceita de novo.
- **Pesquisa:** fechada para menor de 18 anos até o comitê de ética definir o assentimento do menor. A caixa some do cadastro para menor, e no perfil o cartão só aparece para retirar um aceite que já existe. A API recusa o aceite, a exportação sem identificação não sai de menor nem com aceite antigo, e mudar no perfil a data de nascimento para a de um menor retira o aceite de pesquisa, como se o paciente tivesse retirado.
- **Fica para depois:** pedir ao paciente que confirme o termo sozinho quando fizer 18 anos e mostrar no prontuário quem aceitou pelo menor.

**Consentimento de pesquisa em 14/10/2026 (RF37):** separado do termo do cadastro, opcional e revogável. O paciente aceita no cadastro ou no perfil e retira no perfil quando quiser; cada aceite guarda a versão e a data, e a retirada fica marcada sem apagar o registro. Só vale o aceite da versão atual do texto: se o texto mudar, quem quiser continuar participando aceita de novo. A exportação sem identificação, que é a de pesquisa, só sai de quem tem o consentimento valendo. O texto ainda é um rascunho e espera o projeto aprovado no comitê de ética.

**Reforço no banco em 15/10/2026 (RN08 e escalas):** no Postgres, três índices únicos parciais repetem regras que o código já garante com trava de linha: uma resposta valendo por paciente, escala e dia de início (o MEEM fica fora, porque é por consulta), um MEEM valendo por consulta e um acompanhamento ativo por paciente. A RN08 não ganhou restrição no banco: o registro de uma consulta feita fora da agenda grava no horário do atendimento sem passar pela agenda, e uma restrição de sobreposição recusaria esse registro clínico. A RN08 continua garantida no agendamento, pela trava do prescritor.

**Ações no mesmo instante em 16/10/2026:** o job das 8h e as ações das telas não gravam mais um por cima do outro. A consulta de amanhã cancelada ou remarcada antes de o job chegar nela fica como foi deixada e não recebe lembrete, e a mudança que chega depois do lembrete continua valendo; o pedido vencido que o prescritor cancela, recusa ou remarca antes de o job chegar nele fica como o prescritor deixou, e se o job chegou antes a ação do prescritor é recusada porque o pedido já foi recusado; a resposta ou a anamnese gravada antes de o job ler a tarefa vencida conta, mesmo chegando no mesmo instante, e a que fica esperando o job encontra a tarefa já fechada, mas em nenhum dos casos o job grava não respondida por cima de uma resposta já gravada; e o paciente arquivado, o acompanhamento encerrado ou a conta desativada no mesmo instante da rodada não recebem a rodada. O cadastro do acompanhamento e a anamnese nova feitos enquanto o paciente é arquivado são recusados, a segunda anulação da mesma receita é recusada em vez de trocar o motivo da primeira, e a troca de senha pelo perfil feita no mesmo instante do link de senha nova confere a senha atual com a que o link gravou. Um lembrete, pedido vencido ou tarefa vencida com falha no job não segura mais os outros: os outros saem, o dia não conta como concluído e o que falhou é tentado de novo quando o ciclo rodar outra vez (o lembrete da consulta de amanhã, só se for no mesmo dia). Por baixo, todas as ações seguem uma ordem só de travas no banco (seção banco do README do backend). Antes, no Postgres, o registro da consulta e o MEEM contra o cadastro do acompanhamento, e a recusa do pedido vencido contra o cancelamento, podiam ficar um esperando o outro até o banco derrubar um dos dois.

**Nome social em 16/10/2026 (RN15):** opcional, como a clínica pediu (#109), e informado pelo próprio paciente no cadastro e no perfil. Quando existe, é ele que aparece em todas as telas (lista de pacientes, painéis, agenda, consulta, escalas, cabeçalho do sistema e trilha de auditoria), nos avisos e nos e-mails. O nome civil continua guardado e aparece ao lado do social só onde o registro identifica a pessoa:

- no cabeçalho do prontuário, como *Nome civil*;
- na impressão do histórico, junto do nome no campo *Paciente*;
- na exportação com identificação, na mesma célula, como "Nome social (nome civil: Nome civil)";
- na tela de contas de paciente da administração.

A exportação sem identificação não leva nenhum dos dois. Nome social em branco ou igual ao nome civil não é guardado. A lista de pacientes do prescritor e a de contas desativadas da administração seguem a ordem alfabética do nome mostrado, e a busca do prescritor procura por ele, sem diferença de acento nem de maiúscula ("joao" acha "João", 16/10/2026). O prescritor e a administração não editam o nome social, e a troca não entra na trilha de auditoria, como os outros dados do perfil. Os avisos já criados continuam com o nome da época.

**Primeira consulta, retorno e dias sem atendimento em 16/10/2026 (RF10 e RF11):** a clínica respondeu que atende de segunda a sexta, que não marca consulta nos dias em que não pode atender e que a primeira consulta dura de 60 a 90 minutos e o retorno, menos.

- **Segunda a sexta:** já funcionava com os períodos da semana cadastrados de segunda a sexta. Nada mudou.
- **Duração por tipo:** os horários de atendimento guardam a duração da primeira consulta (90 minutos por padrão) e a do retorno (60 minutos por padrão). Cada consulta guarda o tipo; as marcadas antes disso ficam sem tipo.
- **Tipo pelo histórico:** é primeira consulta enquanto o paciente não tiver nenhuma consulta concluída e não anulada; falta e cancelamento não contam. O paciente não escolhe o tipo: os horários livres dele já têm a duração do tipo e andam no passo da consulta mais curta, para primeira consulta e retorno se encaixarem na mesma agenda. O prescritor escolhe o tipo ao marcar ou remarcar, ou deixa o sistema decidir pelo histórico, e a duração que ele escolher vale mais que a do tipo. A consulta registrada fora da agenda, depois de acontecer, também recebe um tipo, decidido pelas consultas concluídas antes dela, e a duração desse tipo.
- **Dias sem atendimento:** o prescritor marca um dia, de hoje em diante, em que não vai atender, e o dia some dos horários livres dos pacientes. O que já estava marcado nele continua valendo, sem cancelamento e sem aviso automático: a tela mostra quantas consultas marcadas e pedidos ainda por vir ficaram no dia, para o prescritor remarcar ou cancelar. O prescritor ainda pode marcar consulta direto num dia sem atendimento.
- **Pacientes do piloto:** quem já era paciente da clínica e não tem consulta concluída no sistema aparece como primeira consulta. O prescritor corrige o tipo e a duração ao remarcar o pedido, ou escolhe o tipo ao marcar a consulta direto.

**Agenda concluída em 13/09/2026:** RF10 e RF11.

- **Horários de atendimento:** o prescritor cadastra os períodos da semana em que atende e a duração padrão das consultas. Os horários livres saem da divisão desses períodos pela duração.
- **Pedido do paciente:**
  - o paciente escolhe um horário livre dos próximos 30 dias e pode escrever o motivo da consulta, que fica fora dos campos clínicos;
  - a API segue a mesma janela de 30 dias contando o dia de hoje e recusa o pedido para depois dela, e o limite de 3 pedidos em aberto vale também para dois pedidos enviados ao mesmo tempo (16/10/2026);
  - o pedido segura o horário até o prescritor confirmar ou recusar;
  - a recusa pode levar um motivo, que chega ao paciente no aviso, e libera o horário.
- **Agenda do prescritor:** mostra os pedidos esperando resposta e as consultas da semana. O prescritor também marca consulta direto, com a duração padrão ou outra, remarca e cancela. Dois agendamentos do mesmo prescritor nunca ocupam o mesmo horário (RN08), nem quando a consulta da noite passa da meia-noite (16/10/2026). Pedido cujo horário passou sem resposta não é mais confirmado: o prescritor recusa, e o paciente escolhe outro horário (16/10/2026).
- **Cancelamento:** o paciente cancela o pedido a qualquer hora e a consulta marcada até 24 horas antes. Depois disso, só o prescritor cancela.
- **Avisos:** pedir, marcar, confirmar, recusar, remarcar e cancelar geram notificação no sistema para a outra parte. O lembrete antes da consulta (RF34) fica para o módulo de notificações.
- **Registro clínico:** só a consulta confirmada na agenda recebe registro clínico e prescrição.

**Escalas concluídas em 13/09/2026:** RF08, RF09, RF32, RF06/RF20 e RF21 a RF26.

- **Motor único:** uma tabela de respostas para todas as escalas, um cálculo por instrumento e a definição de cada escala — itens, âncoras, faixas e direção — em um só lugar, servida a um formulário genérico. Escala nova não pede tabela nem tela nova (RNF08). Saíram sete tabelas, sete serviços e seis telas.
- **Formulários conferidos com o papel:** os itens e as âncoras saem dos formulários de `docs/scales`, inclusive as que mudam de sentido de um item para outro — na dor 10 é ruim e no sono 10 é bom (RF20).
- **Escalas validadas:** HAM-A com os 14 itens e escore de 0 a 56, PSQI calculado pelos 7 componentes de 0 a 21 e MEEM com as 11 seções, de 0 a 30. A faixa do MEEM usa o ponto de corte por escolaridade de Brucki 2003, registrada junto com a data da avaliação (decisão de 13/09/2026, a confirmar com a clínica na pergunta 10).
- **Tarefa com prazo:** cada envio vale por um período. Sem resposta até o fim do prazo, a tarefa fica como não respondida, vira lacuna no gráfico (RN10) e o período seguinte é enviado, em vez de a mesma pendência arrastar para sempre.
- **Diários:** a ficha de acompanhamento e o diário do sono são um registro por dia, apresentados como a grade da semana do papel. A tarefa mostra quantos dias já foram preenchidos e fecha no fim do período; a semana sem nenhum dia conta como não respondida. Na tarefa aberta a grade cobre o período inteiro até hoje, e o paciente preenche de uma vez os dias que esqueceu; dia futuro é recusado, e dia de outro período fica fora da contagem da tarefa aberta (16/10/2026). A semana que venceu e ainda aceita os dias dela, antes do job das 8h ou reaberta pela anulação, abre a grade do período dela, e não a semana de hoje (16/10/2026).
- **Correção:** o paciente corrige a própria resposta enquanto o prescritor não marca como analisada. Depois disso, corrigir é anular com motivo, como já é na consulta e na prescrição. A resposta anulada fica só no histórico: a data dela abre em branco e o paciente responde de novo, inclusive no mesmo dia (16/10/2026). A tarefa reaberta pela anulação depois do prazo fecha com a resposta nova, em vez de virar não respondida no dia seguinte, e a anulação entra na trilha como anulação, não como alteração. A correção que deixa pela metade uma escala pontuada que estava completa devolve a tarefa para pendente (16/10/2026).
- **Valores conferidos:** cada resposta é gravada como chegou ou recusada, nunca trocada por outro valor. Número quebrado ou fora da faixa do item, horário que não vem como texto, resposta só com itens em branco, mais tempo acordado do que na cama no diário do sono e período que termina depois de hoje são recusados (16/10/2026).
- **Resultado:** a central do paciente mostra o escore com a faixa interpretativa (RN14). Formulário da clínica sem faixa publicada mostra o item principal e nenhuma interpretação inventada (RN13).
- **MEEM:** aplicado pelo prescritor dentro de consulta confirmada, nunca designado ao paciente (RN09), e aparece nos dois históricos.
- **Avisos:** a escala enviada avisa o paciente e a resposta completa avisa o prescritor (RF15). Nos diários o prescritor recebe um aviso só, quando o período fecha, com a quantidade de dias preenchidos (02/10/2026), contando também os dias do período preenchidos com atraso antes do fechamento (16/10/2026). As pendências vencidas aparecem no painel do prescritor, menos as de paciente arquivado, que saiu do acompanhamento (16/10/2026).
- **Escala pontuada pela metade:** fica guardada como "Incompleta: sem escore", mas a tarefa continua pendente e no prazo vira não respondida; a tela só deixa salvar com todos os itens respondidos (02/10/2026).
- **Escala avulsa e ciclo:** a tarefa guarda a origem, e só a do acompanhamento marca a próxima rodada (RF32, 02/10/2026). A rodada que chega no dia em que a mesma escala já foi respondida fica com essa resposta, porque a resposta do dia é uma só e a rodada nova ficaria pendente. Cada escala do acompanhamento guarda a própria rodada, então o acompanhamento que substitui outro começa o próprio ciclo com a periodicidade nova, em vez de esperar a rodada mensal do anterior vencer (16/10/2026). A tarefa do acompanhamento anterior que ainda espera resposta, ou que foi respondida no dia, vira a primeira rodada do novo e fica com o prazo dela, para mais ou para menos, com lembrete de novo para o prazo novo: o diário semanal trocado no meio da semana segue até o fim da rodada nova, e a Hamilton semanal trocada por mensal vale o mês. Antes ela mantinha o prazo antigo e os dias até a rodada seguinte ficavam sem tarefa (16/10/2026).
- **Limite conhecido, rodada das 8h no dia de uma resposta atrasada:** se o paciente responde antes do job das 8h a rodada que venceu ontem (às 7h, por exemplo), a resposta fecha essa rodada, e a rodada nova que o job manda às 8h fica pendente nesse dia: responder de novo com a data de hoje só corrige a resposta das 7h. A rodada nova vale pelo prazo da periodicidade, então o paciente responde a partir do dia seguinte (16/10/2026).
- **Convite:** o prescritor vê os convites em aberto e cancela o que foi para a pessoa errada; convite já usado não cancela (RN06, 02/10/2026).
- **Duração:** o acompanhamento de 90 dias termina no dia 90, e o que começa hoje envia a primeira rodada na criação (02/10/2026).

**Evolução concluída em 14/09/2026:** RF27, RF28 e RF07.

- **Uma página para os dois perfis:** o paciente vê a própria evolução e o prescritor a de cada paciente vinculado, com o período adicional "todo o tempo" (RF28). O RF07 ficou dentro dela, como decidido em §8.3.
- **Gráfico:** período, escala e atributo no topo, e a curva do atributo escolhido ao longo das datas de preenchimento. Dia sem resposta é lacuna, nunca zero (RN10).
- **Faixa no gráfico:** nas escalas validadas, cada faixa do instrumento vira uma linha de corte com o nome, então o escore nunca aparece como número solto (RN14). Formulário sem faixa publicada não ganha linha nenhuma (RN13). O MEEM, que não tem faixa fixa, mostra a faixa da escolaridade em cada ponto e a linha do ponto de corte quando a escolaridade é a mesma no período, e só avisa que a escolaridade mudou quando há dois ou mais pontos de corte diferentes. Exame antigo sem escolaridade registrada aparece como "sem faixa", contado à parte (16/10/2026).
- **Qual lado é melhor:** abaixo do nome do atributo, as âncoras do formulário e se maior ou menor é melhor, porque em ansiedade, náusea e função intestinal a curva subindo é melhora e em dor e tremor é piora (16/10/2026).
- **Dose junto do sintoma:** nos atributos da ficha de acompanhamento, uma caixa liga a linha das gotas do dia. É o que responde se mexer na dose mudou o sintoma.
- **Consultas no gráfico:** a consulta do período vira uma linha vertical no dia em que aconteceu, com marca própria quando saiu receita. A que caiu em dia sem preenchimento aparece na lista embaixo, porque não há ponto onde marcar.
- **Relato do paciente (RF07):** os comentários escritos dentro das escalas aparecem em uma linha do tempo abaixo do gráfico, com a data e a escala de onde vieram.
- **Extensibilidade:** acrescentar uma escala não exige tocar no serviço de progresso nem na tela: os atributos, as faixas e os limites do eixo vêm do catálogo (RNF08).

**Painel inicial concluído em 14/09/2026:** RF03.

- **Paciente:** próximas consultas de verdade, lidas do banco, incluindo o pedido que ainda espera resposta; escalas esperando resposta com o prazo e o botão de preencher; a prescrição que está valendo, com a posologia; e os avisos que ele ainda não leu.
- **Prescritor:** pacientes ativos, consultas de hoje com link direto para o registro do atendimento, pedidos de consulta esperando resposta e escalas que venceram sem resposta, cada uma com o paciente.
- **Nada de fachada:** saíram os cartões que nunca tiveram dado por trás — alertas clínicos, fichas com prioridade, pacientes recentes — e os botões que não faziam nada. O painel mostra só o que o resto do sistema grava (RF30).
- **Resumo, não lista:** cada cartão traz no máximo cinco itens e leva para a tela que tem a lista inteira. Nos quatro cartões que vêm com total — esperando resposta e avisos não lidos no painel do paciente, pedidos esperando e escalas vencidas no do prescritor — o número é o total de verdade, contado no banco, e não o tamanho da lista: antes o prescritor com doze escalas vencidas via 5, e o paciente com oito avisos não lidos via 5 no cartão e 8 no menu. Quando há mais de cinco, esses cartões dizem "Mostrando 5 de N". As consultas de hoje não são cortadas, então o número é a lista mesma; as próximas consultas param em cinco sem número nem aviso. As escalas vencidas ainda não têm uma tela com a lista inteira, então o aviso é o que mostra que há mais (16/10/2026).

**Notificações concluídas em 14/09/2026:** RF14, RF15 e RF34.

- **Lembrete de consulta:** o job das 8 da manhã avisa quem tem consulta no dia seguinte. Sai uma vez só, e ainda dá tempo de remarcar.
- **Lembrete de formulário:** a escala que vence em até dois dias e ainda não teve resposta nenhuma gera um lembrete, um por tarefa, sem repetir. Quem já começou a responder não recebe cobrança. A escala enviada à mão que vira rodada do acompanhamento ganha prazo novo e volta a ter lembrete para ele (16/10/2026).
- **E-mail:** os dois lembretes também saem por e-mail, respeitando a preferência de cada conta (RF18). Os outros avisos ficam só dentro do sistema, como o §8.3 decidiu. Sem `MAIL_HOST` configurado, a mensagem continua indo para o log da API.
- **Sem aviso repetido:** a consulta e a tarefa guardam a data em que o aviso foi criado, então o job do dia seguinte não manda de novo. O e-mail sai depois que o banco confirma e, se falhar, não é tentado de novo; o log diz de qual conta era (02/10/2026).
- **Lista de avisos:** uma tela só para os dois perfis, paginada de 20 em 20 (RNF06), com o número de não lidos, marcar como lido ao abrir, marcar todos e apagar. Os filtros avançados ficaram de fora, como o §8.4 decidiu.

**Exportação concluída em 14/09/2026:** RF33, que consolidou os RF16 e RF17 da v1.0.

- **Um arquivo por tipo:** consultas, prescrições, escalas respondidas e anamnese saem em CSV separados, e a série de evolução sai da própria tela de progresso, com a escala e o período já escolhidos.
- **Formato:** ponto e vírgula como separador e marca de UTF-8 no começo, para o arquivo abrir com acento certo na planilha. Texto clínico com quebra de linha não estraga as colunas.
- **Escalas e anamnese:** uma linha por item respondido, com a data, a escala, o escore e a faixa, que é o formato que a planilha filtra e agrupa.
- **Anulação:** os quatro arquivos marcam o registro anulado e trazem o motivo da anulação, que é o que separa um erro de digitação de uma correção clínica. Nas escalas e na anamnese o motivo se repete em cada linha do registro.
- **Composição do óleo:** sai como na tela, por exemplo `CBD 10% + THC 0,3%`, com vírgula decimal e a unidade escrita. O banco guarda três casas, e `10.000` seria lido no Brasil como dez mil.
- **Modo sem identificação:** é do prescritor, para pesquisa. Sai sem nome, CPF, e-mail, telefone e endereço, com o paciente e o prescritor identificados só por um número (RNF04). Antes de 15/10/2026 se chamava "modo anônimo", mas não é anonimização: o número é o id do banco e a clínica sabe de quem é, as datas saem como estão e o texto livre sai como foi escrito e pode citar nomes. Por isso o arquivo continua sendo dado pessoal sensível de saúde (LGPD, art. 5º, II, e art. 12). O termo técnico mais próximo é pseudonimização, que a LGPD só define no art. 13, § 4º, para estudos em saúde pública feitos por órgão de pesquisa. O texto do consentimento de pesquisa diz o mesmo ao paciente (versão `2026-10-rascunho-2`), e a API recusa o parâmetro antigo `anonymous`, para uma aba desatualizada não baixar o arquivo com nome.
- **PDF pela impressão:** em vez de gerar PDF no servidor, existe uma página de impressão com cabeçalho de paciente, prescritor, período e data de emissão, e o histórico do período. O navegador imprime ou salva em PDF. No papel o texto corre de uma folha para a outra, e a primeira folha já traz as consultas: segurar uma seção ou um cartão inteiro jogava ele para a folha seguinte e deixava a anterior em branco. Só o topo de cada cartão e cada linha da tabela de escalas não se partem; o topo do cartão fica na mesma folha que a linha de pressão, peso e altura e o começo do registro, e nenhum título fica sozinho no pé da folha.
- **Trilha:** exportar é leitura de prontuário e entra na trilha de auditoria como qualquer abertura (RF31).

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

- **Sair da conta:** encerra as sessões abertas da pessoa em qualquer aparelho, mesmo que alguém tenha guardado o cookie, e toda sessão termina 12 horas depois do login, mesmo sendo renovada durante o uso.
- **Bloqueio de login:** saiu da conta. Antes, cinco senhas erradas travavam a conta por 15 minutos, e quem soubesse o e-mail de alguém conseguia impedir essa pessoa de entrar. Agora a contagem é por e-mail e endereço de origem, com um limite também por endereço, e e-mail sem cadastro é bloqueado do mesmo jeito, para a resposta não revelar quem tem conta.
- **Origem das requisições:** requisição que muda dado usando o cookie da sessão só é aceita quando vem do endereço oficial do sistema, conferido pelo cabeçalho `Origin`. É uma segunda barreira além do cookie `SameSite=Strict`, para navegador antigo e para subdomínio vizinho, que o navegador trata como o mesmo site.

**Indicadores do piloto em 16/10/2026 (RF39):** a tela *Indicadores* do prescritor mostra os dois números do piloto, calculados com o que o sistema já grava, sem tabela nova. Só aparecem números somados dos pacientes do próprio prescritor, sem nome nem linha por paciente, por isso abrir a tela não entra na trilha como abertura de prontuário.

- **Período:** sem datas, vale os últimos 90 dias até ontem, um ciclo inteiro (RN01). O período termina no máximo ontem, porque o prazo de hoje ainda está correndo. Informar só uma das datas ou a inicial depois da final é recusado, como na agenda.
- **Pacientes no ciclo:** quantos pacientes têm um acompanhamento automático valendo hoje, com a conta ativa e fora do arquivo, ao lado do total de pacientes ativos hoje, contado no mesmo grupo (conta ativa e fora do arquivo). Um acompanhamento que terminou ontem sai na hora, mesmo antes do job das 8h encerrá-lo. Junto vem quantos pacientes têm formulário com prazo no período.
- **Convites:** os convites criados no período, sem os cancelados: quantos foram enviados, quantos viraram cadastro, quantos desses pacientes entraram em um acompanhamento e quantos estão com o link ainda válido e não contam como falha. O convite que venceu sem uso conta como enviado e não fica em aberto. A meta é todo convidado cadastrado e no ciclo. Um link vencido que é enviado de novo para a mesma pessoa conta duas vezes, porque o convite não guarda nome.
- **Formulários no prazo:** entram todas as tarefas com o prazo dentro do período, avulsas ou do acompanhamento, respondidas ou não. O MEEM não vira tarefa e não entra. A regra olha as datas e as respostas ligadas, não a situação da tarefa, então não depende de o job das 8h já ter fechado o prazo. Escala de período e avaliação inicial estão no prazo quando foram respondidas até o último dia; a pendente com o prazo vencido conta como fora do prazo; a avaliação inicial enviada depois do prazo conta como atrasada. A meta é 70%.
- **Diário:** conta como respondido com pelo menos um dia válido, a mesma regra que fecha a tarefa, mesmo que ela ainda esteja pendente. Um diário só com dias anulados fica fora do prazo. Os dias preenchidos aparecem à parte, contra os dias do prazo, para mostrar o quanto essa regra é generosa. Um dia preenchido entre a meia-noite e o job das 8h do dia seguinte ao prazo ainda conta, como na grade do paciente.
- **Resposta anulada:** não conta. A escala volta a pendente e só conta de novo com outra resposta. A exceção é a avaliação inicial: anular a ficha não reabre a tarefa, que continua como respondida.
- **Pacientes arquivados:** nos formulários, continuam na conta, senão a desistência sumiria do número. Em *Pacientes no ciclo* eles ficam de fora. Arquivar encurta o prazo das tarefas abertas para o dia do arquivamento, e a que ficou sem resposta conta como fora do prazo.
- **Arredondamento:** a porcentagem é arredondada para baixo, para 69,6% não aparecer como meta batida.
- **De fora por enquanto:** a separação entre envio avulso e acompanhamento e uma visão da clínica inteira para a administração. As duas saem das mesmas consultas, se forem pedidas.

**Ajustes nas telas em 16/10/2026:** correções que saíram da revisão das telas.

- **Horário de Brasília no navegador (RNF16):** o hoje e o agora das telas são os de Brasília, mesmo com o navegador em outro fuso. Valem para o dia que o diário abre, a data da anamnese, o início do acompanhamento, os períodos dos indicadores e da trilha, a idade e as comparações com o horário da consulta: se ela já começou ou já acabou e se ainda falta o prazo de 24 horas para o paciente cancelar. Antes, à noite, um navegador adiantado mandava o dia seguinte e a API recusava. Os horários continuam aparecendo como a API manda, no horário de Brasília. Sempre que o fuso do navegador não é `America/Sao_Paulo`, mesmo outro fuso de UTC-3 como `America/Fortaleza`, aparece "(horário de Brasília)" ao lado do horário: nas telas de consultas e do painel do paciente, inclusive nos avisos do painel, e na página de avisos, que o prescritor também vê. Assim quem está no AM, MT, MS, RO, RR, AC ou fora do país não lê 10:00 como o horário local. Os e-mails ainda vão sem essa indicação. O dia que a tela monta vale do começo do dia local, que nem sempre é meia-noite: onde o horário de verão pula a meia-noite (Açores, Beirute, Cairo) o dia da virada começa à 1h, e a grade do diário não trava mais nesse dia.
- **Plano de dose (RN02):** cada dose vai com a semana da linha que a prescritora vê na tela. A linha sem dose fica de fora e não adianta as semanas seguintes.
- **Erro de campo:** a prescrição e o acompanhamento automático mostram no próprio campo o motivo da recusa da API, e o erro de um campo que não aparece na tela vai para o aviso de cima. A duração do acompanhamento já pede de 7 a 365 dias no campo, como a API.
- **Avisos:** apagar o único aviso da última página volta para a página anterior, em vez de mostrar a lista vazia sem os botões de página.
- **Termo atualizado no cadastro (RF36):** se o termo mudar com o cadastro aberto, a tela busca o texto novo e desmarca o aceite, para a pessoa ler e aceitar a versão nova.
- **Convite com outra conta aberta:** o link de convite aberto com alguém logado diz com qual conta o navegador está e oferece sair e continuar o cadastro com o mesmo convite. Antes o convite sumia e a pessoa caía na tela inicial da conta aberta, o que pega o responsável com dois filhos (RF38).
- **Link de senha com outra conta aberta (RF35, RF02.2):** o link de senha nova e o link de primeiro acesso do prescritor, abertos com alguém logado, também dizem com qual conta o navegador está e oferecem sair e continuar com o mesmo link. Antes o link sumia e a pessoa caía na tela inicial da conta aberta, o que pega o administrador que entrega o link de primeiro acesso no computador da clínica.
- **Link aberto sem sessão:** quem abre uma tela do sistema sem estar logado, como a do link do lembrete por e-mail, passa pelo login e volta para essa tela, em vez de cair na tela inicial.
- **Termo atualizado com o aceite aberto (RF36):** se a versão do termo mudar de novo com a tela de aceite aberta, a tela busca o texto novo e o próximo aceite vai com a versão nova. Antes cada clique mandava a versão velha e era recusado até a pessoa sair da tela.
- **Sair da conta:** a tela só volta ao login quando a API confirma que a sessão acabou. Sem resposta da API aparece "Não foi possível sair da conta" e a pessoa continua logada, porque o cookie continuaria valendo e a próxima pessoa no mesmo computador entraria na conta dela. Uma sessão que já tinha acabado conta como saída.
- **Nome no menu:** o nome, ou o nome social, salvo no perfil aparece no menu lateral na hora, sem esperar a próxima tela.
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

> Existem outras faixas em circulação para o HAM-A. O sistema deve usar as do instrumento que a prescritora efetivamente aplica, porque é contra elas que ela interpreta o resultado.

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
`eficiência = (horas dormidas ÷ horas na cama) × 100`, onde horas na cama é o intervalo entre o horário de deitar e o de levantar.
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

Faixas interpretativas variam por escolaridade; o resultado é sempre exibido com a faixa correspondente (RN14).

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
