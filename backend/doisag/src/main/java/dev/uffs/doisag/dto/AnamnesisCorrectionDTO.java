package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.AnamnesisCorrection;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

// a versao da anamnese antes da correcao do paciente, com o nome de campo da api
// so a prescritora recebe (decisao de 14/11/2026, revisao das regras R4)
public record AnamnesisCorrectionDTO(
        LocalDateTime correctedAt,
        LocalDate assessmentDate,
        Map<String, Object> answers
) {
    public AnamnesisCorrectionDTO(AnamnesisCorrection correction) {
        this(correction.getCorrectedAt(), correction.getAssessmentDate(), correction.getAnswers());
    }
}
