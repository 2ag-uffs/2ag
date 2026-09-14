package dev.uffs.doisag.dto;

import java.util.List;

// tudo q a central de escalas do paciente mostra (RF08)
public record PatientScalesPageDTO(
        String patientName,
        List<ScaleTaskDTO> pending,
        List<ScaleResponseSummaryDTO> history
) {
}
