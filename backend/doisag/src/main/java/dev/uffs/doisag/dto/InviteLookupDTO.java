package dev.uffs.doisag.dto;

import jakarta.validation.constraints.NotBlank;

// o codigo do convite vai no corpo e n no endereco, pq endereco fica em log de servidor
public record InviteLookupDTO(
        @NotBlank(message = "O cadastro de paciente é feito pelo link de convite que o seu prescritor envia. Peça o link a ele")
        String token
) {
}
