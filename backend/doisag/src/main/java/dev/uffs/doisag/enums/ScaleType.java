package dev.uffs.doisag.enums;

import dev.uffs.doisag.infra.NotFoundException;

import java.util.Arrays;

// as escalas q o sistema aplica (RF08 e RF09)
// o slug eh o q aparece na url e na api pra n expor o nome do enum
public enum ScaleType {

    ACOMPANHAMENTO_SEMANAL("Acompanhamento semanal", "acompanhamento-semanal", true),
    REGISTRO_SONO("Diário do sono", "diario-sono", true),
    ESCALA_HAMILTON("Escala de ansiedade de Hamilton", "hamilton", true),
    ESCALA_PITTSBURGH("Índice de qualidade do sono de Pittsburgh", "pittsburgh", true),
    REGISTRO_DOR("Acompanhamento semanal de dor", "registro-dor", true),
    REGISTRO_TEA("Acompanhamento semanal de TEA", "registro-tea", true),
    // RN09 o MEEM eh aplicado pelo prescritor na consulta e n vira tarefa
    MINI_EXAME_ESTADO_MENTAL("Mini-Exame do Estado Mental (MEEM)", "mini-exame", false),
    // a anamnese tem tela propria desde o modulo de atendimento (RF19)
    // e entra aqui so pq tbm eh uma tarefa q o prescritor envia
    ANAMNESE("Avaliação inicial", "anamnese", true);

    private final String displayName;
    private final String slug;
    private final boolean filledByPatient;

    ScaleType(String displayName, String slug, boolean filledByPatient) {
        this.displayName = displayName;
        this.slug = slug;
        this.filledByPatient = filledByPatient;
    }

    public String getDisplayName() {
        return displayName;
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
