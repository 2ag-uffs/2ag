package dev.uffs.doisag.enums;

// qual parte do prontuario o evento de auditoria alcancou
public enum AuditRecordType {
    PRONTUARIO("Prontuário"),
    CONSULTA("Consulta"),
    PRESCRICAO("Prescrição"),
    MINI_EXAME("Mini-Exame do Estado Mental"),
    ANAMNESE("Anamnese"),
    ACOMPANHAMENTO_SEMANAL("Acompanhamento semanal"),
    ESCALA_HAMILTON("Escala de ansiedade de Hamilton"),
    ESCALA_PITTSBURGH("Índice de qualidade do sono de Pittsburgh"),
    REGISTRO_DOR("Registro de dor"),
    REGISTRO_SONO("Diário de sono"),
    REGISTRO_TEA("Registro de sintomas (TEA)"),
    DESIGNACAO_DE_ESCALA("Escala enviada ao paciente"),
    ACOMPANHAMENTO_AUTOMATICO("Acompanhamento de 90 dias");

    private final String label;

    AuditRecordType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
