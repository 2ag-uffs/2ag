package dev.uffs.doisag.dto;

import dev.uffs.doisag.scale.ScaleItem;
import dev.uffs.doisag.scale.ScaleItemType;
import dev.uffs.doisag.scale.ScaleOption;

import java.util.List;

// um item do formulario do jeito q a tela precisa pra desenhar o campo
// o section eh a pergunta do papel q junta varios itens, q a tela mostra uma vez em cima deles
// o question eh a pergunta inteira do papel, q a tela mostra no lugar do label, e o intro eh a frase q o
// papel poe antes do item
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
        List<ScaleOption> options,
        String section,
        String question,
        String intro
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
                item.options(),
                item.section(),
                item.question(),
                item.intro()
        );
    }
}
