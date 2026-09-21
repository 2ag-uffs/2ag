package dev.uffs.doisag.enums;

// em q pe esta a consulta na agenda (RF04 RF10 e RF11)
public enum AppointmentStatus {
    // o paciente pediu o horario e o prescritor ainda n respondeu
    SOLICITADA,
    AGENDADA,
    EM_ANDAMENTO,
    CONCLUIDA,
    CANCELADA,
    // o prescritor n aceitou o pedido do paciente
    RECUSADA,
    // a hora passou e o paciente n apareceu, quem marca eh o prescritor
    NAO_COMPARECEU;

    // pedido esperando resposta e consulta marcada seguram o horario na agenda
    public boolean holdsTimeSlot() {
        return this != CANCELADA && this != RECUSADA && this != NAO_COMPARECEU;
    }

    // consulta q o prescritor marcou ou confirmou e q vale como atendimento
    public boolean isConfirmed() {
        return this == AGENDADA || this == EM_ANDAMENTO || this == CONCLUIDA;
    }

    // consulta q aceita o registro clinico do atendimento
    // a falta entra pq registrar o atendimento eh como o prescritor desfaz
    // uma falta marcada por engano
    public boolean acceptsClinicalRecord() {
        return isConfirmed() || this == NAO_COMPARECEU;
    }
}
