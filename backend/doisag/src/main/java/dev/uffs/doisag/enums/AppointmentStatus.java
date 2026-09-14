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
    RECUSADA;

    // pedido esperando resposta e consulta marcada seguram o horario na agenda
    public boolean holdsTimeSlot() {
        return this != CANCELADA && this != RECUSADA;
    }

    // consulta q o prescritor marcou ou confirmou e q vale como atendimento
    public boolean isConfirmed() {
        return this == AGENDADA || this == EM_ANDAMENTO || this == CONCLUIDA;
    }
}
