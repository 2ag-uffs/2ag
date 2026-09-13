package dev.uffs.doisag.dto;

import jakarta.validation.constraints.NotNull;

// escolha de receber ou n os avisos por e-mail (RF18)
public record EmailPreferenceDTO(
        @NotNull(message = "Informe se quer receber avisos por e-mail")
        Boolean emailNotificationsEnabled
) {
}
