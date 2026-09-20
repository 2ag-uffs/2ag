package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.Periodicity;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.model.ProtocolItem;
import jakarta.validation.constraints.NotNull;

public record ProtocolItemDTO(
        @NotNull(message = "Escolha a escala")
        ScaleType scaleType,

        String scaleName,

        @NotNull(message = "Escolha de quanto em quanto tempo a escala volta")
        Periodicity periodicity
) {
    public ProtocolItemDTO(ProtocolItem item) {
        this(item.getScaleType(), item.getScaleType().getDisplayName(), item.getPeriodicity());
    }
}
