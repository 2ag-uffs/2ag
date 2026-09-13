package dev.uffs.doisag.dto;

import jakarta.validation.constraints.NotBlank;

// dados do formulario de login
public record LoginDTO(
        @NotBlank(message = "Informe o e-mail")
        String email,

        @NotBlank(message = "Informe a senha")
        String password
) {
}
