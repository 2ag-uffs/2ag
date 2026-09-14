// textos da agenda do jeito q a tela mostra (RF10 e RF11)

export const MODALITY_OPTIONS = [
    {value: "PRESENCIAL", label: "Presencial"},
    {value: "REMOTA", label: "Remota"},
];

// duracoes q o prescritor escolhe pras consultas
export const DURATION_OPTIONS = [15, 20, 30, 40, 45, 50, 60, 90, 120].map((minutes) => ({
    value: String(minutes),
    label: minutes + " minutos",
}));

// igual o java com 1 na segunda e 7 no domingo
export const WEEKDAYS = [
    {value: 1, label: "Segunda-feira"},
    {value: 2, label: "Terça-feira"},
    {value: 3, label: "Quarta-feira"},
    {value: 4, label: "Quinta-feira"},
    {value: 5, label: "Sexta-feira"},
    {value: 6, label: "Sábado"},
    {value: 7, label: "Domingo"},
];

const STATUS_LABELS = {
    SOLICITADA: "Aguardando resposta",
    AGENDADA: "Agendada",
    EM_ANDAMENTO: "Em andamento",
    CONCLUIDA: "Concluída",
    CANCELADA: "Cancelada",
    RECUSADA: "Recusada",
};

export function modalityLabelOf(modality) {
    const option = MODALITY_OPTIONS.find((currentOption) => currentOption.value === modality);
    return option ? option.label : "Modalidade não informada";
}

export function statusLabelOf(appointment) {
    if (appointment.annulled) {
        return "Anulada";
    }
    return STATUS_LABELS[appointment.status] || appointment.status;
}
