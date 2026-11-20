package dev.uffs.doisag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// senha nova escolhida pelo link de recuperacao (RF35)
public record PasswordResetDTO(
        @NotBlank(message = "Este link está incompleto. Abra de novo o link que chegou no seu e-mail ou peça um novo")
        String token,

        @NotBlank(message = "Informe a nova senha")
        @Pattern(regexp = PasswordRules.PATTERN, message = PasswordRules.MESSAGE)
        String newPassword
) {
}
