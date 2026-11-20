package dev.uffs.doisag.dto;

import jakarta.validation.constraints.NotBlank;

// codigo do link q chegou no e-mail novo (RF18, issue 130)
public record EmailChangeConfirmDTO(
        @NotBlank(message = "Este link está incompleto. Abra de novo o link que chegou no seu e-mail novo "
                + "ou peça a troca de novo no seu perfil")
        String token
) {
}
