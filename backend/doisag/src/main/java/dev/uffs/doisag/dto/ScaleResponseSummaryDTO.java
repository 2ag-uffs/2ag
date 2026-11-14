package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.model.ScaleResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;

// a linha do historico de escalas respondidas
// o resultado eh o escore com a faixa, nunca o texto fixo concluido (RF08)
public record ScaleResponseSummaryDTO(
        Long id,
        ScaleType scaleType,
        String scaleName,
        String slug,
        LocalDate periodStart,
        LocalDate periodEnd,
        String result,
        boolean reviewed,
        boolean annulled,
        boolean editableByPatient,
        // a ultima correcao do paciente e o resultado de antes dela, so pra prescritora (revisao das regras R4)
        LocalDateTime correctedAt,
        String previousResult
) {
    public ScaleResponseSummaryDTO(ScaleResponse response, String result) {
        this(response, result, null);
    }

    public ScaleResponseSummaryDTO(ScaleResponse response, String result, ScaleCorrectionDTO lastCorrection) {
        this(
                response.getId(),
                response.getScaleType(),
                response.getScaleType().getDisplayName(),
                response.getScaleType().getSlug(),
                response.getPeriodStart(),
                response.getPeriodEnd(),
                result,
                response.isReviewed(),
                response.isAnnulled(),
                response.getScaleType().isFilledByPatient() && !response.isReviewed() && !response.isAnnulled(),
                lastCorrection == null ? null : lastCorrection.correctedAt(),
                lastCorrection == null ? null : lastCorrection.result()
        );
    }
}
