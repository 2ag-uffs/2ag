# Fase 1 — Identificação de Demandas

**Componente:** GCH1993 — Projeto de Integração de Extensão
**Docente:** Prof. Claunir Pavan
**Prazo de entrega:** 15/09/2026, 23:59
**Forma de entrega:** comentário na tarefa, por **um único integrante** do grupo

---

## Texto para envio

### 1) Título da demanda

Automação do acompanhamento terapêutico longitudinal de pacientes em tratamento com óleo de *Cannabis sativa* no Instituto EDMA (Chapecó/SC)

### 2) Descrição da demanda

**Parceiro.** Instituto EDMA, clínica de prática integrativa localizada em Chapecó/SC (Av. Getúlio Dorneles Vargas, 180S, sala 26, Centro), voltada à prescrição e ao acompanhamento terapêutico com fitocanabinoides. Interlocutora: Brunna Varela, biomédica responsável pela triagem, pela prescrição dos óleos e pela condução do tratamento. A clínica atende pacientes presenciais e remotos com quadros de ansiedade, insônia, dor crônica, TDAH, doenças autoimunes, condições neurodegenerativas, transtorno do espectro autista e cuidados complementares em oncologia.

**Como a demanda foi identificada.** A demanda foi levantada em processo de elicitação junto à clínica, por meio de entrevista estruturada com a profissional prescritora e de formulário eletrônico respondido por quatro pacientes em acompanhamento, com registro fotográfico dos instrumentos hoje utilizados (fichas de anamnese em papel, planilhas de acompanhamento semanal e escalas clínicas impressas).

**O problema.** O atendimento da clínica organiza-se em três etapas: triagem por formulário de anamnese, consulta clínica de 40 a 60 minutos e acompanhamento terapêutico ao longo de 90 dias, período em que o paciente deve relatar semanalmente sua evolução, sintomas e efeitos adversos para que a dose seja ajustada. Todo esse ciclo é operado hoje de forma manual e com ferramentas que não conversam entre si — WhatsApp, formulários do Google, planilhas e prontuário eletrônico:

- os dados de acompanhamento chegam por mensagem ou planilha preenchida pelo próprio paciente e precisam ser **transcritos manualmente** para o prontuário, gerando retrabalho e risco de perda, omissão e inconsistência no histórico clínico;
- a profissional acumula as funções clínicas e administrativas — agendamento, cadastro, envio e recebimento de formulários, transcrição —, **sem apoio de equipe administrativa**, o que sobrecarrega o atendimento e limita a capacidade de expansão da clínica;
- o acompanhamento depende do preenchimento **voluntário** de planilhas, e a adesão relatada é baixa, o que compromete a regularidade e a qualidade dos dados coletados entre consultas;
- escalas clínicas padronizadas (Hamilton para ansiedade, Índice de Pittsburgh para qualidade do sono, Mini-Exame do Estado Mental) são aplicadas de forma irregular pela mesma razão;
- não há envio automatizado de formulários periódicos, nem visualização gráfica da evolução dos sintomas, nem registro estruturado dos dados sintomáticos e terapêuticos que permita acompanhar a resposta ao escalonamento de dose;
- do lado dos pacientes, relatou-se dificuldade em lembrar os horários das doses, ausência de um espaço para registrar sintomas e efeitos adversos entre consultas, e preferência pelo uso de dispositivos móveis.

**Público beneficiado.** Diretamente, a profissional prescritora e os pacientes em acompanhamento no Instituto EDMA. Indiretamente, outras clínicas e serviços que prescrevem fitocanabinoides no país: a solução será publicada como software livre, sob licença AGPL-3.0, em repositório público, podendo ser instalada e adaptada por qualquer serviço com a mesma necessidade. A estruturação desses dados também contribui para uma área ainda carente de evidência nacional sistematizada sobre uso terapêutico de canabinoides.

**Ação de extensão proposta.** Desenvolver, implantar e validar junto à clínica um sistema livre de acompanhamento longitudinal de pacientes em uso de óleo de *Cannabis sativa*, contemplando: registro estruturado de anamnese, consulta e prescrição; aplicação digital das escalas clínicas padronizadas com cálculo dos escores conforme o algoritmo oficial de cada instrumento; envio automatizado dos formulários de acompanhamento ao longo dos 90 dias de tratamento; lembretes de dose; e visualização gráfica da evolução dos sintomas por período. O trabalho inclui a capacitação da profissional para uso do sistema, a execução de um piloto com pacientes que consentirem em participar e a avaliação do impacto das ações sobre a rotina da clínica.

**Transparência quanto ao ponto de partida.** O grupo parte de um protótipo desenvolvido pelas integrantes Maiqueli Eduarda Dama Mingoti e Caroline de Quadros Piazza nos componentes Engenharia de Software I e Programação II, em 2025, que **nunca foi implantado nem utilizado pela clínica**. Uma auditoria técnica realizada pelo grupo identificou que o protótipo não está apto ao uso com dados reais de paciente — há falhas de controle de acesso, ausência de conformidade com a LGPD e escalas clínicas com escore calculado fora do algoritmo oficial dos instrumentos. A ação de extensão consiste, portanto, em adequar, implantar, capacitar e avaliar o impacto na comunidade, publicando o resultado como software livre — etapas que não foram objeto dos componentes anteriores.

### 3) Membros do grupo

| Nome completo | Matrícula |
| :--- | :--- |
| Maiqueli Eduarda Dama Mingoti | 20230004643 |
| Caroline de Quadros Piazza | 20230000690 |
| *(a informar)* | *(a informar)* |
| *(a informar)* | *(a informar)* |
| *(a informar)* | *(a informar)* |

---

## Registro da entrega

- **Integrante responsável pelo envio:** *(definir — apenas um encaminha)*
- **Data e hora do envio:** *(preencher)*
- **Print da confirmação:** *(anexar em `docs/extensao/evidencias/`)*
