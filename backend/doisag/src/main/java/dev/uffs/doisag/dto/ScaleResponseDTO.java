package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.model.Annulment;
import dev.uffs.doisag.model.ScaleResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// uma escala respondida com o resultado pronto pra tela
public record ScaleResponseDTO(
        Long id,
        ScaleType scaleType,
        String scaleName,
        String slug,
        LocalDate periodStart,
        LocalDate periodEnd,
        Map<String, Object> answers,
        Integer score,
        String scoreBand,
        String result,
        Long patientId,
        String patientName,
        String prescriberName,
        Long appointmentId,
        boolean reviewed,
        LocalDateTime reviewedAt,
        // o paciente so corrige a escala q ele mesmo responde e enquanto o prescritor n analisou
        boolean editableByPatient,
        LocalDateTime filledAt,
        boolean annulled,
        LocalDateTime annulledAt,
        String annulmentReason,
        String annulledByName,
        // as versoes q as correcoes do paciente substituiram, da mais velha pra mais nova
        // vem so pra prescritora na leitura da resposta, e vazio no resto (revisao das regras R4)
        List<ScaleCorrectionDTO> corrections
) {
    public ScaleResponseDTO(ScaleResponse response, String result) {
        this(response, result, List.of());
    }

    public ScaleResponseDTO(ScaleResponse response, String result, List<ScaleCorrectionDTO> corrections) {
        this(
                response.getId(),
                response.getScaleType(),
                response.getScaleType().getDisplayName(),
                response.getScaleType().getSlug(),
                response.getPeriodStart(),
                response.getPeriodEnd(),
                response.getAnswers(),
                response.getScore(),
                response.getScoreBand(),
                result,
                response.getPatient().getId(),
                response.getPatient().getDisplayName(),
                response.getPrescriber() == null ? null : response.getPrescriber().getName(),
                response.getAppointment() == null ? null : response.getAppointment().getId(),
                response.isReviewed(),
                response.getReviewedAt(),
                isEditableByPatient(response),
                response.getCreatedAt(),
                response.isAnnulled(),
                Annulment.annulledAtOf(response.getAnnulment()),
                Annulment.reasonOf(response.getAnnulment()),
                Annulment.annulledByNameOf(response.getAnnulment()),
                corrections
        );
    }

    private static boolean isEditableByPatient(ScaleResponse response) {
        return response.getScaleType().isFilledByPatient() && !response.isReviewed() && !response.isAnnulled();
    }
}
