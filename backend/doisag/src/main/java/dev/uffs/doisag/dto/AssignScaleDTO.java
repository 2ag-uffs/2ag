package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.ScaleType;
import jakarta.validation.constraints.NotNull;

// dto simples pra receber qual escala o prescritor quer enviar
public record AssignScaleDTO(
        @NotNull(message = "Escolha a escala que o paciente vai responder")
        ScaleType scaleType
) {
}
