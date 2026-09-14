package dev.uffs.doisag.enums;

import java.util.Arrays;
import java.util.List;

// o que da pra acompanhar num grafico ao longo do tempo (RF27 e RF28)
//
// cada atributo aponta pra um item de uma escala, ou pro escore dela
// quando o itemKey eh nulo. o nome de exibicao e a faixa saem do
// catalogo, entao mudar o formulario n deixa esta lista desatualizada
public enum TrackableAttribute {

    // ficha de acompanhamento semanal
    DOR(ScaleType.ACOMPANHAMENTO_SEMANAL, "dor"),
    SONO(ScaleType.ACOMPANHAMENTO_SEMANAL, "sono"),
    HUMOR(ScaleType.ACOMPANHAMENTO_SEMANAL, "humor"),
    TREMOR(ScaleType.ACOMPANHAMENTO_SEMANAL, "tremor"),
    ANSIEDADE(ScaleType.ACOMPANHAMENTO_SEMANAL, "ansiedade"),
    DISPOSICAO_ENERGIA(ScaleType.ACOMPANHAMENTO_SEMANAL, "disposicao"),
    FUNCAO_INTESTINAL(ScaleType.ACOMPANHAMENTO_SEMANAL, "funcaoIntestinal"),
    APETITE(ScaleType.ACOMPANHAMENTO_SEMANAL, "apetite"),
    CONCENTRACAO(ScaleType.ACOMPANHAMENTO_SEMANAL, "concentracao"),
    INTERACAO_SOCIAL(ScaleType.ACOMPANHAMENTO_SEMANAL, "interacaoSocial"),
    RIGIDEZ_ESPASTICIDADE(ScaleType.ACOMPANHAMENTO_SEMANAL, "rigidezEspasticidade"),
    REDUCAO_SUBSTANCIA(ScaleType.ACOMPANHAMENTO_SEMANAL, "reducaoSubstancia"),
    NAUSEA_VOMITO(ScaleType.ACOMPANHAMENTO_SEMANAL, "nauseaVomito"),
    DESEMPENHO_ESPORTIVO(ScaleType.ACOMPANHAMENTO_SEMANAL, "desempenhoEsporte"),
    DERMATOLOGICO(ScaleType.ACOMPANHAMENTO_SEMANAL, "doencaDermatologicaIntensidade"),
    GOTAS_MANHA(ScaleType.ACOMPANHAMENTO_SEMANAL, "gotasManha"),
    GOTAS_TARDE(ScaleType.ACOMPANHAMENTO_SEMANAL, "gotasTarde"),

    // escalas validadas entram pelo escore
    ESCORE_HAMILTON(ScaleType.ESCALA_HAMILTON, null),
    ESCORE_PITTSBURGH(ScaleType.ESCALA_PITTSBURGH, null),

    // acompanhamento semanal de dor
    INTENSIDADE_DOR(ScaleType.REGISTRO_DOR, "intensidadeDor"),

    // acompanhamento semanal de TEA
    QUALIDADE_DE_VIDA(ScaleType.REGISTRO_TEA, "qualidadeDeVida"),

    // diario do sono
    CANSACO(ScaleType.REGISTRO_SONO, "cansaco"),
    ESTRESSE(ScaleType.REGISTRO_SONO, "estresse"),
    SONOLENCIA_DIURNA(ScaleType.REGISTRO_SONO, "sonolenciaDiurna"),
    DESATENCAO(ScaleType.REGISTRO_SONO, "desatencao"),
    IRRITABILIDADE(ScaleType.REGISTRO_SONO, "irritabilidade"),
    DESPERTARES(ScaleType.REGISTRO_SONO, "vezesQueAcordou"),
    TEMPO_ATE_DORMIR(ScaleType.REGISTRO_SONO, "tempoAteDormir"),
    TEMPO_TOTAL_SONO(ScaleType.REGISTRO_SONO, "tempoTotalSono");

    private final ScaleType scaleType;
    private final String itemKey;

    TrackableAttribute(ScaleType scaleType, String itemKey) {
        this.scaleType = scaleType;
        this.itemKey = itemKey;
    }

    public ScaleType getScaleType() {
        return scaleType;
    }

    public String getItemKey() {
        return itemKey;
    }

    // sem item significa q o grafico segue o escore da escala inteira
    public boolean isScore() {
        return itemKey == null;
    }

    public static List<TrackableAttribute> doTipo(ScaleType scaleType) {
        return Arrays.stream(values())
                .filter(attribute -> attribute.scaleType == scaleType)
                .toList();
    }
}
