package dev.uffs.doisag.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.List;

// este record carrega apenas os dados que o prescritor precisa enviar
public record PrescriptionCreateDTO(
        @NotBlank(message = "A descrição do produto é obrigatória")
        String productDescription,

        @NotBlank(message = "A posologia é obrigatória")
        String posology,

        String brand,
        String concentration,
        String spectrum,
        String volume,
        String administrationRoute,
        String observation,
        String instructions,
        String precautions,
        String expectedEffects,
        Integer treatmentDurationDays,
        LocalDate nextConsultationDate,

        // o plano de subida de dose, semana a semana
        @Valid
        List<DoseEscalationStepDTO> escalationSteps
) {}
