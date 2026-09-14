package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.ScaleType;

// as escalas q o prescritor pode enviar pro paciente (RF09 e RN09)
// a lista sai do proprio enum, entao a tela n repete nome de escala
public record AssignableScaleDTO(
        ScaleType type,
        String name,
        String description,
        String path
) {
    public AssignableScaleDTO(ScaleType scaleType) {
        this(scaleType, scaleType.getDisplayName(), scaleType.getDescription(), scaleType.getPath());
    }
}
