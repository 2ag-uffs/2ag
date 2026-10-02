package dev.uffs.doisag.dto;

import jakarta.validation.constraints.NotBlank;

// o codigo do convite vai no corpo e n no endereco, pq endereco fica em log de servidor
public record InviteLookupDTO(
        @NotBlank(message = "O link de convite é obrigatório")
        String token
) {
}
