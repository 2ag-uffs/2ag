# Documento de Requisitos do Sistema — 2AG

**Versão:** 2.0
**Data:** 12 de setembro de 2026
**Substitui:** `docs/historico/requisitos-v1-2025.pdf` (v1.0, 06/07/2025)
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

**O que o sistema não é:** não é ERP de clínica (sem faturamento, estoque ou financeiro), não comercializa produtos, não substitui o prontuário legal da clínica (Amplimed) — convive com ele via exportação.

### 2.1 Perfis de usuário

| Perfil | Papel técnico | Descrição |
|---|---|---|
| Paciente | `ROLE_PATIENT` | Preenche anamnese, escalas e fichas de acompanhamento; consulta seu histórico, progresso e prescrições; agenda consultas |
| Prescritor | `ROLE_PRESCRIBER` | Conduz consultas, emite prescrições, designa escalas, acompanha evolução dos **seus** pacientes |
| Administrador | `ROLE_ADMIN` | Provisiona contas de prescritor. Perfil operacional, sem acesso a dado clínico |

> **Mudança em relação à v1.0:** a v1.0 usava `ROLE_ADMIN` como sinônimo de prescritor. Isso criou uma confusão que, na implementação, virou falha de segurança: um perfil chamado "admin" naturalmente recebeu acesso irrestrito. Os papéis agora são três e `ROLE_ADMIN` **não vê prontuário**.

---

## 3. Decisões de escopo desta versão

### 3.1 Descartado

| Item (origem v1.0) | Justificativa |
|---|---|
| **Integração com WhatsApp** (RNF10; tabelas de necessidades §1.5) | Exige conta comercial verificada, templates aprovados pela Meta e custo por conversa. Uma integração parcial é pior que nenhuma: gera expectativa de canal oficial sem confiabilidade. **Substituído por** notificação in-app (RF14/RF15) + e-mail (RF34), que atendem a mesma necessidade — comunicação entre consultas |
| **Integração com Amplimed** (RNF09) | O próprio documento original a classifica como "futura". Não há API pública. **Substituído por** exportação em formatos abertos (RF33) |
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
- **Status** é um conjunto fechado: `AGENDADA`, `EM_ANDAMENTO`, `CONCLUIDA`, `CANCELADA`
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

---

**RF07 — Acompanhamento (prescritor)** · `mantido` · **I** · Prescritor

Visualização da evolução clínica do paciente por gráficos, tabelas e comentários inseridos pelo paciente, organizados por data.

---

**RF21 — Escala de Ansiedade de Hamilton (HAM-A)** · `alterado` · **E** · Paciente

Avaliação com pontuação de 0 a 4 em cada item, onde valores maiores indicam sintomas mais severos. Cada item exibe descrição comportamental de apoio. O sistema calcula e armazena o escore total.

*Critérios de aceite:*
- **14 itens**, conforme o instrumento original: humor ansioso, tensão, medos, insônia, comprometimento intelectual, humor deprimido, somatizações motoras, somatizações sensoriais, sintomas cardiovasculares, sintomas respiratórios, sintomas gastrointestinais, sintomas geniturinários, sintomas autonômicos e **comportamento durante a entrevista**
- Escore total de 0 a 56, com faixa interpretativa (Anexo A.1)
- A v1.0 e a implementação contemplam apenas 13 itens, com máximo de 52 — fora da escala do instrumento

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

*Justificativa:* a implementação atual soma 13 campos crus, gerando faixa de 0 a 39, incomparável a qualquer ponto de corte publicado. O próprio código documenta a limitação em comentário. Como o valor é apresentado à prescritora rotulado como índice de qualidade do sono e pode influenciar ajuste de dose, a soma crua é inaceitável.

---

**RF24 — Acompanhamento semanal de paciente com TEA** · `mantido` · **I** · Paciente

Registro do período avaliado e autoavaliação da qualidade de vida (0 a 10). Frequência de comportamentos na última semana em quatro níveis — nenhum dia, até 3 dias, entre 3 e 6 dias, todos os dias — para: agressividade/impulsividade, agitação psicomotora/ansiedade, sono, interação social, estereotipia e apetite. Campo de observação opcional.

*Critérios de aceite:*
- **Possui tela de preenchimento acessível.** Existe no backend, sem interface

---

**RF25 — Acompanhamento semanal de paciente com dor** · `mantido` · **I** · Paciente

Data avaliada e intensidade da dor em escala visual de 0 a 10 (0 ausência, 1–3 leve, 4–6 moderada, 7–10 intensa). Frequência de interferência da dor — nenhum dia, até 3 dias, entre 3 e 6 dias, todos os dias — em: atividades básicas, atividades sociais, produtividade no trabalho, qualidade do sono e necessidade de medicação extra. Identificador único e observação opcional.

*Critérios de aceite:*
- **Possui tela de preenchimento acessível.** Existe no backend, sem interface

---

**RF26 — Mini-Exame do Estado Mental (MEEM)** · `alterado` · **E** · Prescritor

Aplicado pelo prescritor durante a consulta. Registra nome do paciente, data da avaliação e a pontuação de cada seção. O sistema calcula o total automaticamente.

*Critérios de aceite:*
- **Pontuação máxima de 30 pontos**, com todas as seções do instrumento (Anexo A.3): orientação temporal (5), orientação espacial (5), registro (3), atenção e cálculo (5), memória de evocação (3), nomeação (2), repetição (1), comando de três etapas (3), **leitura (1)**, **escrita (1)** e **cópia dos pentágonos (1)**
- Faixa interpretativa ajustada por escolaridade
- A implementação atual contempla 8 seções, com máximo de **27 pontos** — faltam leitura, escrita e cópia, todas descritas no RF26 da v1.0. Um teto de 27 desloca todas as faixas de interpretação
- Vinculado à consulta que o originou
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

---

**RF28 — Tela de progresso (prescritor)** · `mantido` · **E** · Prescritor

Mesma estrutura do RF27, para o paciente selecionado, com período adicional "todo o tempo". Restrito a pacientes vinculados (RF30).

---

**RF33 — Exportação de dados** · `novo (consolida RF16 e RF17)` · **I** · Paciente, Prescritor

Exportação de histórico clínico, escalas respondidas e séries de evolução em **PDF** (leitura e impressão) e **CSV** (análise).

*Critérios de aceite:*
- O paciente exporta apenas os próprios dados, para levar a outras consultas
- O prescritor exporta dados dos seus pacientes
- Existe modo **anonimizado** para fins de pesquisa, sem nome, CPF, e-mail, telefone e endereço
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
- Exportação anonimizada disponível para uso científico (RF33)
- Nenhuma resposta da API expõe hash de senha ou credencial
- Nenhum dado pessoal em parâmetro de URL

**RNF05 — Criptografia** · `ampliado` · **E**
Toda conexão externa por HTTPS/TLS. Senha armazenada apenas como hash BCrypt.
*Critério de aceite:* não há URL `http://` fixa no código; a origem da API é configurável por ambiente.

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
Exportação em formatos abertos, PDF e CSV (RF33). *Integração direta com Amplimed fora de escopo (§3.1).*

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
| **RN03** | Formulações possíveis: **isolado** (CBD, THC, CBG), **broad spectrum** (sem THC) e **full spectrum** |
| **RN04** | E-mail e CPF são únicos em todo o sistema. *Hoje o CPF não possui restrição de unicidade nem na entidade nem no script SQL, permitindo paciente duplicado — embora a documentação técnica afirme o contrário* |
| **RN05** | Cada paciente está vinculado a exatamente um prescritor. O vínculo é estabelecido no cadastro pelo código do prescritor e só pode ser alterado por administrador |
| **RN06** | O código de vínculo do prescritor é único e **não é adivinhável**. *O formato atual — três letras do nome + dois dígitos — oferece 90 combinações por prefixo, permitindo que um paciente se vincule a prescritor alheio por tentativa, e a geração pode entrar em laço infinito se as 90 esgotarem* |
| **RN07** | O prescritor não comercializa produtos. O sistema registra orientação quanto a marca, lote e concentração, sem função de venda |
| **RN08** | Dois agendamentos do mesmo prescritor não podem ocupar o mesmo horário |
| **RN09** | Escalas de **autoaplicação** (ficha de acompanhamento, HAM-A, PSQI, diário de sono, registro de dor, registro TEA, anamnese) são designáveis ao paciente. Escalas de **heteroaplicação** (MEEM) são aplicadas pelo prescritor em consulta e não geram tarefa ao paciente. *Hoje o MEEM é designável, criando tarefa que o paciente não pode concluir* |
| **RN10** | Item de escala não respondido é registrado como **ausente**, nunca como zero. *Hoje os campos usam tipo primitivo e um formulário incompleto grava `dor = 0` ("sem dor") e `sono = 0` ("muito ruim"), e esses zeros são plotados como dado real no gráfico de evolução* |
| **RN11** | Campo de texto clínico livre (observação, plano terapêutico, evolução, diagnóstico, campos descritivos da anamnese) não tem limite prático de tamanho. *Hoje esses campos são limitados a 255 caracteres, truncando ou rejeitando registro clínico legítimo* |
| **RN12** | Senha nunca é gravada em texto puro nem transita em resposta da API. *Hoje a atualização de paciente grava o valor recebido diretamente, sem aplicar o hash, invalidando o acesso do paciente* |
| **RN13** | Escore de escala validada é calculado exclusivamente pelo algoritmo oficial do instrumento (Anexo A). Adaptação ou simplificação de algoritmo não é permitida: escore fora do instrumento não pode ser exibido com o nome do instrumento |
| **RN14** | O escore é sempre apresentado com sua **faixa interpretativa**, nunca como número isolado |
| **RN15** | O paciente é identificado pelo **nome social** em toda a interface, nos formulários e nos relatórios. O nome de registro civil é armazenado apenas onde houver exigência legal. *O formulário de PSQI-BR aplicado pela clínica já traz o campo "NOME DO PACIENTE (SOCIAL)"; o sistema hoje guarda um único campo `name`* |

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
| RNF10 WhatsApp | **descartado** | §3.1 |
| Multilíngue pt/en/es | **rebaixado** | RNF12 — arquitetura preparada, só pt-BR |
| — | **novo** | RNF13 Testabilidade |
| — | **novo** | RNF14 Versionamento de esquema |
| — | **novo** | RNF15 Configuração por ambiente |

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

*Ajuste necessário:* acrescentar o item 14, ausente na v1.0 e na implementação.

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

*Ajustes necessários:*
- Acrescentar o campo de **frequência do item "outros motivos"**. É o único dado faltante: o campo descritivo existe, mas não há frequência, e sem ela o componente C5 não fecha os 9 itens
- Substituir a soma crua de 13 campos pelo cálculo dos 7 componentes
- Calcular C4, em vez de coletar eficiência
- A presença de parceiro de quarto **não compõe** o índice global; é informação de contexto

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

*Ajuste necessário:* acrescentar leitura, escrita e cópia. As três constam do RF26 da v1.0 mas não foram implementadas, e sua ausência limita o teto a 27 pontos, deslocando todas as faixas de interpretação.

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

**Fase 0 concluída em 12/09/2026:** ambiente de build validado com Eclipse Temurin JDK 17.0.20.1 e o wrapper Maven do repositório (3.9.10); `clean package` gera artefato com sucesso.
