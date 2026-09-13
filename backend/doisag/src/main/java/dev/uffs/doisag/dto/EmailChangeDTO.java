package dev.uffs.doisag.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// troca do e-mail de acesso (RF18)
// pede a senha atual pq o e-mail eh o login e o caminho pra recuperar a senha
public record EmailChangeDTO(
        @NotBlank(message = "Informe o novo e-mail")
        @Email(message = "E-mail inválido")
        @Size(max = 255, message = "O e-mail pode ter até 255 caracteres")
        String newEmail,

        @NotBlank(message = "Informe a senha atual")
        String currentPassword
) {
    // e-mail digitado no celular costuma vir com espaco no fim
    public EmailChangeDTO {
        if (newEmail != null) {
            newEmail = newEmail.trim();
        }
    }
}
