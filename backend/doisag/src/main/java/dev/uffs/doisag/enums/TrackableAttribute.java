package dev.uffs.doisag.enums;

import java.util.Arrays;
import java.util.List;

// o que da pra acompanhar num grafico ao longo do tempo.
//
// cada atributo sabe de qual escala ele vem e qual eh a faixa dele, pq
// o front precisa saber onde plotar: 0 a 10 e 0 a 56 n podem usar o
// mesmo eixo. o nome de exibicao tbm fica aqui, entao a tela n precisa
// ter uma lista propria pra traduzir
public enum TrackableAttribute {

    // ficha de acompanhamento semanal, tudo de 0 a 10
    DOR(ScaleType.ACOMPANHAMENTO_SEMANAL, "Dor", 0, 10),
    SONO(ScaleType.ACOMPANHAMENTO_SEMANAL, "Sono", 0, 10),
    HUMOR(ScaleType.ACOMPANHAMENTO_SEMANAL, "Humor", 0, 10),
    TREMOR(ScaleType.ACOMPANHAMENTO_SEMANAL, "Tremor", 0, 10),
    ANSIEDADE(ScaleType.ACOMPANHAMENTO_SEMANAL, "Ansiedade", 0, 10),
    DISPOSICAO_ENERGIA(ScaleType.ACOMPANHAMENTO_SEMANAL, "Disposição e energia", 0, 10),
    FUNCAO_INTESTINAL(ScaleType.ACOMPANHAMENTO_SEMANAL, "Função intestinal", 0, 10),
    APETITE(ScaleType.ACOMPANHAMENTO_SEMANAL, "Apetite", 0, 10),
    CONCENTRACAO(ScaleType.ACOMPANHAMENTO_SEMANAL, "Concentração", 0, 10),
    INTERACAO_SOCIAL(ScaleType.ACOMPANHAMENTO_SEMANAL, "Interação social", 0, 10),
    RIGIDEZ_ESPASTICIDADE(ScaleType.ACOMPANHAMENTO_SEMANAL, "Rigidez e espasticidade", 0, 10),
    REDUCAO_SUBSTANCIA(ScaleType.ACOMPANHAMENTO_SEMANAL, "Redução de outra substância", 0, 10),
    NAUSEA_VOMITO(ScaleType.ACOMPANHAMENTO_SEMANAL, "Náusea e vômito", 0, 10),
    DESEMPENHO_ESPORTIVO(ScaleType.ACOMPANHAMENTO_SEMANAL, "Desempenho esportivo", 0, 10),
    DERMATOLOGICO(ScaleType.ACOMPANHAMENTO_SEMANAL, "Condição dermatológica", 0, 10),
    GOTAS_MANHA(ScaleType.ACOMPANHAMENTO_SEMANAL, "Gotas pela manhã", 0, null),
    GOTAS_TARDE(ScaleType.ACOMPANHAMENTO_SEMANAL, "Gotas à tarde", 0, null),

    // hamilton: 14 itens de 0 a 4
    ESCORE_HAMILTON(ScaleType.ESCALA_HAMILTON, "Escore de ansiedade", 0, 56),

    // pittsburgh: 7 componentes de 0 a 3
    ESCORE_PITTSBURGH(ScaleType.ESCALA_PITTSBURGH, "Índice de qualidade do sono", 0, 21),

    // registro de dor
    INTENSIDADE_DOR(ScaleType.REGISTRO_DOR, "Intensidade da dor", 0, 10),

    // registro de TEA
    QUALIDADE_DE_VIDA(ScaleType.REGISTRO_TEA, "Qualidade de vida", 0, 10),
    ESCORE_TEA(ScaleType.REGISTRO_TEA, "Escore de comportamentos", 0, 18),

    // diario de sono
    CANSACO(ScaleType.REGISTRO_SONO, "Cansaço", 0, 5),
    ESTRESSE(ScaleType.REGISTRO_SONO, "Estresse", 0, 5),
    SONOLENCIA_DIURNA(ScaleType.REGISTRO_SONO, "Sonolência diurna", 0, 5),
    IRRITABILIDADE(ScaleType.REGISTRO_SONO, "Irritabilidade", 0, 5),
    DESPERTARES(ScaleType.REGISTRO_SONO, "Vezes que acordou", 0, null),
    TEMPO_ATE_DORMIR(ScaleType.REGISTRO_SONO, "Minutos até adormecer", 0, null);

    private final ScaleType scaleType;
    private final String displayName;
    private final Integer minValue;
    private final Integer maxValue;

    TrackableAttribute(ScaleType scaleType, String displayName, Integer minValue, Integer maxValue) {
        this.scaleType = scaleType;
        this.displayName = displayName;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    public ScaleType getScaleType() {
        return scaleType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Integer getMinValue() {
        return minValue;
    }

    public Integer getMaxValue() {
        return maxValue;
    }

    // o que da pra acompanhar numa escala. o front usa isso pra montar
    // o seletor de atributo depois que a pessoa escolhe a escala
    public static List<TrackableAttribute> doTipo(ScaleType scaleType) {
        return Arrays.stream(values())
                .filter(atributo -> atributo.scaleType == scaleType)
                .toList();
    }
}
