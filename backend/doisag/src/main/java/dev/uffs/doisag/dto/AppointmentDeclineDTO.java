package dev.uffs.doisag.dto;

import jakarta.validation.constraints.Size;

// o motivo da recusa eh opcional e vai no aviso pro paciente
public record AppointmentDeclineDTO(
        @Size(max = 500, message = "O motivo pode ter até 500 caracteres")
        String reason
) {
}
