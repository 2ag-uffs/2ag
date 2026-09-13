package dev.uffs.doisag.dto;

import jakarta.validation.constraints.NotNull;

// liga ou desliga uma conta
public record ChangeActiveDTO(
        @NotNull(message = "Informe se a conta fica ativa")
        Boolean active
) {
}
