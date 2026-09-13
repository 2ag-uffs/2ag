package dev.uffs.doisag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// motivo de anular um registro clinico
public record AnnulmentDTO(
        @NotBlank(message = "Conte por que o registro está sendo anulado")
        @Size(max = 2000, message = "O motivo pode ter até 2000 caracteres")
        String reason
) {
    // espaco sobrando n conta como motivo
    public AnnulmentDTO {
        reason = reason == null ? null : reason.trim();
    }
}
