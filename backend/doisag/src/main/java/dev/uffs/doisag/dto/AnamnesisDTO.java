package dev.uffs.doisag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

// o q o paciente responde na ficha de anamnese (RF19)
// texto descritivo n tem limite pratico de tamanho (RN11)
public record AnamnesisDTO(
        // vazio vira a data de hoje
        @PastOrPresent(message = "A data de preenchimento não pode estar no futuro")
        LocalDate assessmentDate,

        @Size(max = 255, message = "A ocupação pode ter até 255 caracteres")
        String profession,

        @NotBlank(message = "Conte o motivo principal da consulta")
        String reasonForVisit,

        String previousDiagnosis,
        String previousTreatment,
        String currentMedication,
        String familyHistory,
        String adverseReaction,
        String geneticCondition,
        String diet,
        String smokingHabits,
        String alcoholConsumption,

        @Size(max = 20, message = "Confira o peso")
        String weight,

        @Size(max = 20, message = "Confira a altura")
        String height,

        String substanceUse,
        String physicalActivity,
        String sleepHabits,
        String anxiety,
        String pain,
        String expectations,

        @NotBlank(message = "Responda se está ciente de que o tratamento precisa ser acompanhado")
        String treatmentAwareness,

        String observation
) {
}
