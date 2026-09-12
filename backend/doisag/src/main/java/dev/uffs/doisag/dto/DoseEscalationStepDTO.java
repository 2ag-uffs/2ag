package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.DoseEscalationStep;

// um degrau do plano de subida de dose
public record DoseEscalationStepDTO(
        Integer week,
        String dosage,
        String note
) {
    public DoseEscalationStepDTO(DoseEscalationStep step) {
        this(step.getWeek(), step.getDosage(), step.getNote());
    }
}
