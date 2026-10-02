package dev.uffs.doisag.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// o e-mail vai no corpo pra n ficar no endereco nem no log de acesso
public record AdminPatientLookupDTO(
        @NotBlank(message = "Informe o e-mail da conta")
        @Email(message = "E-mail inválido")
        @Size(max = 255, message = "O e-mail pode ter até 255 caracteres")
        String email
) {
    // e-mail colado costuma vir com espaco no fim
    public AdminPatientLookupDTO {
        if (email != null) {
            email = email.trim();
        }
    }
}
