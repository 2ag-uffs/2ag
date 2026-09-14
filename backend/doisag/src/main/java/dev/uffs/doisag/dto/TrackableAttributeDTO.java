package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.scale.ScoreBand;

import java.util.List;

// catalogo do que da pra acompanhar. o front usa isso pra montar os
// seletores de escala e de atributo, em vez de ter a lista repetida
// dentro do codigo da tela
//
// as faixas vem junto pq o grafico marca os cortes do instrumento, e
// escore sem faixa n quer dizer nada (RN14)
public record TrackableAttributeDTO(
        String name,
        String displayName,
        ScaleType scaleType,
        String scaleName,
        Integer minValue,
        Integer maxValue,
        List<ScoreBand> bands
) {
}
