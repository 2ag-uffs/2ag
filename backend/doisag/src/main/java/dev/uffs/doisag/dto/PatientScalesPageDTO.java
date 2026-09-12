package dev.uffs.doisag.dto;

import java.util.List;

// o pacotão de dados que o meu backend vai mandar para a tela de escala-clinica-paciente sei la o nome
public record PatientScalesPageDTO(
        String patientName,
        List<PendingScaleInfoDTO> pendingScales,
        List<CompletedScaleInfoDTO> completedScales
) {}