package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.AssignedScale;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.enums.AssignmentStatus;
import java.time.LocalDate;

// dto pra gente mandar de volta pro front só as infos que ele precisa
public record AssignedScaleResponseDTO(
        Long id,
        Long patientId,
        String patientName,
        ScaleType scaleType,
        AssignmentStatus status,
        LocalDate assignedDate
) {
    // um método construtor extra pra facilitar a criação a partir da entidade
    public AssignedScaleResponseDTO(AssignedScale assignedScale) {
        this(
                assignedScale.getId(),
                assignedScale.getPatient().getId(),
                assignedScale.getPatient().getName(),
                assignedScale.getScaleType(),
                assignedScale.getStatus(),
                assignedScale.getAssignedDate()
        );
    }
}