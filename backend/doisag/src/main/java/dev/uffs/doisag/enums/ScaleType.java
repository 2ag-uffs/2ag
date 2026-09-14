package dev.uffs.doisag.enums;

import dev.uffs.doisag.infra.NotFoundException;

import java.util.Arrays;

// as escalas q o sistema aplica (RF08 e RF09)
// o slug eh o q aparece na url e na api pra n expor o nome do enum
public enum ScaleType {

    ACOMPANHAMENTO_SEMANAL("Acompanhamento semanal", "acompanhamento-semanal", true,
            "Gotas e sintomas do dia a dia, um dia de cada vez"),
    REGISTRO_SONO("Diário do sono", "diario-sono", true,
            "Horários, despertares e como foi o dia, um dia de cada vez"),
    ESCALA_HAMILTON("Escala de ansiedade de Hamilton", "hamilton", true,
            "Intensidade dos sintomas de ansiedade, com escore de 0 a 56"),
    ESCALA_PITTSBURGH("Índice de qualidade do sono de Pittsburgh", "pittsburgh", true,
            "Qualidade do sono no último mês, com índice de 0 a 21"),
    REGISTRO_DOR("Acompanhamento semanal de dor", "registro-dor", true,
            "Intensidade da dor na semana e o quanto ela atrapalhou"),
    REGISTRO_TEA("Acompanhamento semanal de TEA", "registro-tea", true,
            "Qualidade de vida e frequência dos comportamentos na semana"),
    // RN09 o MEEM eh aplicado pelo prescritor na consulta e n vira tarefa
    MINI_EXAME_ESTADO_MENTAL("Mini-Exame do Estado Mental (MEEM)", "mini-exame", false,
            "Aplicado pelo prescritor durante a consulta"),
    // a anamnese tem tela propria desde o modulo de atendimento (RF19)
    // e entra aqui so pq tbm eh uma tarefa q o prescritor envia
    ANAMNESE("Avaliação inicial", "anamnese", true,
            "Ficha de triagem com histórico de saúde, hábitos e expectativas");

    private final String displayName;
    private final String slug;
    private final boolean filledByPatient;
    private final String description;

    ScaleType(String displayName, String slug, boolean filledByPatient, String description) {
        this.displayName = displayName;
        this.slug = slug;
        this.filledByPatient = filledByPatient;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getSlug() {
        return slug;
    }

    public boolean isFilledByPatient() {
        return filledByPatient;
    }

    // a rota da tela q preenche a escala
    public String getPath() {
        return this == ANAMNESE ? "/anamnese" : "/escalas/" + slug;
    }

    // como essa escala aparece na trilha de auditoria (RF31)
    public AuditRecordType getAuditRecordType() {
        return switch (this) {
            case ACOMPANHAMENTO_SEMANAL -> AuditRecordType.ACOMPANHAMENTO_SEMANAL;
            case REGISTRO_SONO -> AuditRecordType.REGISTRO_SONO;
            case ESCALA_HAMILTON -> AuditRecordType.ESCALA_HAMILTON;
            case ESCALA_PITTSBURGH -> AuditRecordType.ESCALA_PITTSBURGH;
            case REGISTRO_DOR -> AuditRecordType.REGISTRO_DOR;
            case REGISTRO_TEA -> AuditRecordType.REGISTRO_TEA;
            case MINI_EXAME_ESTADO_MENTAL -> AuditRecordType.MINI_EXAME;
            case ANAMNESE -> AuditRecordType.ANAMNESE;
        };
    }

    public static ScaleType fromSlug(String slug) {
        return Arrays.stream(values())
                .filter(scaleType -> scaleType.slug.equals(slug))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Escala não encontrada: " + slug));
    }
}
