package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.ScaleType;

// catalogo do que da pra acompanhar. o front usa isso pra montar os
// seletores de escala e de atributo, em vez de ter a lista repetida
// dentro do codigo da tela
public record TrackableAttributeDTO(
        String name,
        String displayName,
        ScaleType scaleType,
        String scaleName,
        Integer minValue,
        Integer maxValue
) {
}
