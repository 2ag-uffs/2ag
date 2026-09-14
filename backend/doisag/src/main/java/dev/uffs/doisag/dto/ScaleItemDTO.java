package dev.uffs.doisag.dto;

import dev.uffs.doisag.scale.ScaleItem;
import dev.uffs.doisag.scale.ScaleItemType;
import dev.uffs.doisag.scale.ScaleOption;

import java.util.List;

// um item do formulario do jeito q a tela precisa pra desenhar o campo
public record ScaleItemDTO(
        String key,
        String label,
        String help,
        ScaleItemType type,
        Integer minValue,
        Integer maxValue,
        String lowAnchor,
        String highAnchor,
        boolean higherIsBetter,
        List<ScaleOption> options
) {
    public ScaleItemDTO(ScaleItem item) {
        this(
                item.key(),
                item.label(),
                item.help(),
                item.type(),
                item.minValue(),
                item.maxValue(),
                item.lowAnchor(),
                item.highAnchor(),
                item.higherIsBetter(),
                item.options()
        );
    }
}
