package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.model.Annulment;
import dev.uffs.doisag.model.ScaleResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
        String annulledByName
) {
    public ScaleResponseDTO(ScaleResponse response, String result) {
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
                response.getPatient().getName(),
                response.getPrescriber() == null ? null : response.getPrescriber().getName(),
                response.getAppointment() == null ? null : response.getAppointment().getId(),
                response.isReviewed(),
                response.getReviewedAt(),
                response.getScaleType().isFilledByPatient() && !response.isReviewed() && !response.isAnnulled(),
                response.getCreatedAt(),
                response.isAnnulled(),
                annulledAtOf(response.getAnnulment()),
                reasonOf(response.getAnnulment()),
                annulledByNameOf(response.getAnnulment())
        );
    }

    private static LocalDateTime annulledAtOf(Annulment annulment) {
        return annulment == null ? null : annulment.getAnnulledAt();
    }

    private static String reasonOf(Annulment annulment) {
        return annulment == null ? null : annulment.getAnnulmentReason();
    }

    private static String annulledByNameOf(Annulment annulment) {
        return annulment == null || annulment.getAnnulledBy() == null ? null : annulment.getAnnulledBy().getName();
    }
}
