package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.ScaleResponseCorrection;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

// a versao de uma resposta antes da correcao do paciente, com o resultado q ela tinha
// so a prescritora recebe (decisao de 14/11/2026, revisao das regras R4)
public record ScaleCorrectionDTO(
        LocalDateTime correctedAt,
        LocalDate periodStart,
        LocalDate periodEnd,
        Map<String, Object> answers,
        String result
) {
    public ScaleCorrectionDTO(ScaleResponseCorrection correction, String result) {
        this(
                correction.getCorrectedAt(),
                correction.getPeriodStart(),
                correction.getPeriodEnd(),
                correction.getAnswers(),
                result
        );
    }
}
