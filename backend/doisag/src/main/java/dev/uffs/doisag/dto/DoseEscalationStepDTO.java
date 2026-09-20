package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.DoseEscalationStep;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// um degrau do plano de subida de dose
// degrau sem semana ou sem dosagem n diz nada pra quem vai tomar
public record DoseEscalationStepDTO(
        @NotNull(message = "Informe a semana do degrau")
        @Min(value = 1, message = "A semana começa em 1")
        @Max(value = 104, message = "O plano de dose vai até a semana 104")
        Integer week,

        @NotBlank(message = "Informe a dosagem do degrau")
        @Size(max = 255, message = "A dosagem pode ter até 255 caracteres")
        String dosage,

        @Size(max = 255, message = "A observação pode ter até 255 caracteres")
        String note
) {
    public DoseEscalationStepDTO(DoseEscalationStep step) {
        this(step.getWeek(), step.getDosage(), step.getNote());
    }
}
