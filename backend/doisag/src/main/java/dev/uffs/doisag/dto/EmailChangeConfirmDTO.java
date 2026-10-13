package dev.uffs.doisag.dto;

import jakarta.validation.constraints.NotBlank;

// codigo do link q chegou no e-mail novo (RF18, issue 130)
public record EmailChangeConfirmDTO(
        @NotBlank(message = "O link de confirmação está incompleto")
        String token
) {
}
