package dev.uffs.doisag.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// pedido do link pra criar uma senha nova (RF35)
public record PasswordResetRequestDTO(
        @NotBlank(message = "Informe o e-mail")
        @Email(message = "E-mail inválido")
        String email,

        // campo escondido na tela, pessoa n ve e robo preenche
        String site
) {
    // e-mail digitado no celular costuma vir com espaco no fim
    public PasswordResetRequestDTO {
        if (email != null) {
            email = email.trim();
        }
    }
}
