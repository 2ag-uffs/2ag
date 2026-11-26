// paciente arquivado saiu do acompanhamento e a api recusa o pedido de consulta e a anamnese nova dele
// as telas avisam antes em vez de deixar ele descobrir so no erro do envio

export const ARCHIVED_APPOINTMENT_NOTICE =
    "Seu acompanhamento foi encerrado. Fale com a clínica para voltar a marcar consultas.";

export const ARCHIVED_ANAMNESIS_NOTICE =
    "Seu acompanhamento foi encerrado. Fale com a clínica antes de preencher uma anamnese nova.";

// do lado do prescritor: o prontuario do arquivado n recebe consulta, receita nem mini-exame novos, e a api
// pede pra reativar antes (decisao de 17/11/2026). as telas dizem isso no lugar do botao ou do formulario
// reativar fica onde sempre ficou, no botao Reativar da aba Arquivados da lista de pacientes
const HOW_TO_REACTIVATE = "Reative o paciente, na aba Arquivados da lista de pacientes, antes de ";

export const ARCHIVED_CHART_NOTICE = "Paciente arquivado. " + HOW_TO_REACTIVATE
    + "registrar consulta, emitir prescrição ou aplicar o Mini-Exame.";

export const ARCHIVED_RECORD_NOTICE = "Paciente arquivado. " + HOW_TO_REACTIVATE + "registrar a consulta.";

export const ARCHIVED_PRESCRIPTION_NOTICE = "Paciente arquivado. " + HOW_TO_REACTIVATE + "emitir a prescrição.";

export const ARCHIVED_EXAM_NOTICE = "Paciente arquivado. " + HOW_TO_REACTIVATE + "aplicar o Mini-Exame.";

export const ARCHIVED_ATTENDANCE_NOTICE = "Paciente arquivado: reative antes de registrar o atendimento.";

// em Enviar escalas o cartao da avaliacao inicial do arquivado ja vem bloqueado com este motivo
export const ARCHIVED_INITIAL_ASSESSMENT_NOTICE = "Paciente arquivado não preenche a avaliação inicial. "
    + "Reative o paciente, na aba Arquivados da lista de pacientes, antes de enviar.";
