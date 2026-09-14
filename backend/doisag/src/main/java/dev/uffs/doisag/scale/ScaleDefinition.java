package dev.uffs.doisag.scale;

import dev.uffs.doisag.enums.ScaleType;

import java.util.List;
import java.util.Optional;

// tudo q descreve uma escala num lugar so: os itens, as ancoras, as
// faixas e como ela eh preenchida.
//
// a tela generica do paciente e o calculo do escore leem daqui, entao
// escala nova eh so acrescentar uma definicao e um calculo (RNF08)
public record ScaleDefinition(
        ScaleType type,
        String title,
        String instruction,
        FillMode fillMode,
        List<ScaleItem> items,
        List<ScoreBand> bands,
        Integer minScore,
        Integer maxScore,
        String scoreLabel,
        // item q vira o resultado na lista quando a escala n tem escore
        // proprio, pq inventar faixa em formulario n validado eh
        // justamente o q a RN13 proibe
        String summaryItemKey) {

    // como o formulario cobre o tempo
    public enum FillMode {
        // um registro por dia dentro da semana, mostrado como a grade do papel
        DIARIO,
        // um registro q fala de um periodo inteiro
        PERIODO,
        // um registro de um dia so, como o hamilton e o MEEM
        PONTUAL
    }

    public Optional<ScaleItem> itemOf(String key) {
        return items.stream()
                .filter(item -> item.key().equals(key))
                .findFirst();
    }

    public boolean hasScore() {
        return maxScore != null;
    }
}
