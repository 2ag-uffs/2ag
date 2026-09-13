package dev.uffs.doisag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// troca de senha da propria conta (RF18)
// pede a senha atual de proposito pq sem ela quem achasse um computador logado tomava a conta
public record ChangePasswordDTO(
        @NotBlank(message = "Informe a senha atual")
        String currentPassword,

        @NotBlank(message = "Informe a nova senha")
        @Pattern(regexp = PasswordRules.PATTERN, message = PasswordRules.MESSAGE)
        String newPassword
) {
}
