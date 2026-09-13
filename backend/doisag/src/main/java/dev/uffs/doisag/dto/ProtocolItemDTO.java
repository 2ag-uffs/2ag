package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.Periodicity;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.model.ProtocolItem;

public record ProtocolItemDTO(
        ScaleType scaleType,
        String scaleName,
        Periodicity periodicity
) {
    public ProtocolItemDTO(ProtocolItem item) {
        this(item.getScaleType(), item.getScaleType().getDisplayName(), item.getPeriodicity());
    }
}
