package dev.uffs.doisag.enums;

// enum com os tipos de escala que a gente pode designar
public enum ScaleType {
    ESCALA_HAMILTON("Escala de ansiedade de Hamilton", "/escala-hamilton"),
    ESCALA_PITTSBURGH("Índice de qualidade do sono de Pittsburgh", "/escala-pittsburgh"),
    MINI_EXAME_ESTADO_MENTAL("Mini-Exame do Estado Mental (MEEM)", "/mini-exame"),
    REGISTRO_DOR("Registro diário de dor", "/diario-dor"),
    REGISTRO_SONO("Registro diário de sono", "/diario-sono"),
    REGISTRO_TEA("Registro de sintomas (TEA)", "/diario-tea"),
    ANAMNESE("Avaliação inicial", "/anamnese"),
    ACOMPANHAMENTO_SEMANAL("Acompanhamento semanal", "/acompanhamento-paciente");

    private final String displayName;
    private final String path;

    ScaleType(String displayName, String path) {
        this.displayName = displayName;
        this.path = path;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPath() {
        return path;
    }
}