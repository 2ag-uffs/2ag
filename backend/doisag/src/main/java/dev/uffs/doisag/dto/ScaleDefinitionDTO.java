package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.scale.ScaleDefinition;
import dev.uffs.doisag.scale.ScoreBand;

import java.util.List;

// a escala inteira descrita pra tela generica: itens ancoras e faixas
// a tela n sabe o nome de escala nenhuma, ela so desenha o q vem aqui
public record ScaleDefinitionDTO(
        ScaleType type,
        String slug,
        String title,
        String instruction,
        ScaleDefinition.FillMode fillMode,
        boolean filledByPatient,
        String scoreLabel,
        Integer minScore,
        Integer maxScore,
        List<ScaleItemDTO> items,
        List<ScoreBand> bands
) {
    public ScaleDefinitionDTO(ScaleDefinition definition) {
        this(
                definition.type(),
                definition.type().getSlug(),
                definition.title(),
                definition.instruction(),
                definition.fillMode(),
                definition.type().isFilledByPatient(),
                definition.scoreLabel(),
                definition.minScore(),
                definition.maxScore(),
                definition.items().stream().map(ScaleItemDTO::new).toList(),
                definition.bands()
        );
    }
}
