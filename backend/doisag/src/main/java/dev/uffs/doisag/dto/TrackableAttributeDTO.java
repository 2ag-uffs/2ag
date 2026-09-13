package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.enums.TrackableAttribute;

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
    public TrackableAttributeDTO(TrackableAttribute attribute) {
        this(
                attribute.name(),
                attribute.getDisplayName(),
                attribute.getScaleType(),
                attribute.getScaleType().getDisplayName(),
                attribute.getMinValue(),
                attribute.getMaxValue()
        );
    }
}
