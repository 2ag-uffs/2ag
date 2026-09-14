package dev.uffs.doisag.scale;

import dev.uffs.doisag.enums.ScaleType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// o calculo de cada escala validada (RN13 e Anexo A)
//
// n sobe o spring de proposito: escore eh conta pura e precisa falhar
// rapido quando alguem mexer no algoritmo
class ScaleScorerTest {

    private final ScaleCatalog catalog = new ScaleCatalog();
    private final ScaleScorer scorer = new ScaleScorer(catalog);

    // HAM-A soma simples dos 14 itens, de 0 a 56
    @Test
    void hamiltonSomaOsQuatorzeItens() {
        Map<String, Object> answers = allHamiltonItemsWith(1);

        ScaleScorer.ScoreResult result = scorer.score(ScaleType.ESCALA_HAMILTON, answers);

        assertThat(result.score()).isEqualTo(14);
        assertThat(result.band()).isEqualTo("Ansiedade temporária");
    }

    @Test
    void hamiltonMostraAFaixaDoFormularioDaClinica() {
        assertThat(hamiltonBandOf(0)).isEqualTo("Sem ansiedade");
        assertThat(hamiltonBandOf(2)).isEqualTo("Ansiedade grave");

        // 14 itens em 1 dao 14, e mais 2 num item so chegam em 16
        Map<String, Object> moderate = allHamiltonItemsWith(1);
        moderate.put("humorAnsioso", 3);

        assertThat(scorer.score(ScaleType.ESCALA_HAMILTON, moderate).band()).isEqualTo("Ansiedade moderada");
    }

    // RN10 item em branco n vale zero, entao formulario incompleto n tem escore
    @Test
    void hamiltonSemUmItemNaoTemEscore() {
        Map<String, Object> answers = allHamiltonItemsWith(2);
        answers.remove("medos");

        ScaleScorer.ScoreResult result = scorer.score(ScaleType.ESCALA_HAMILTON, answers);

        assertThat(result.score()).isNull();
        assertThat(result.band()).isNull();
    }

    // PSQI soma de 7 componentes derivados, de 0 a 21
    @Test
    void pittsburghCalculaOsSeteComponentes() {
        Map<String, Object> answers = sleeperWhoSleepsWell();

        ScaleScorer.ScoreResult result = scorer.score(ScaleType.ESCALA_PITTSBURGH, answers);

        // C1 1 + C2 1 + C3 1 + C4 0 + C5 1 + C6 0 + C7 1
        assertThat(result.score()).isEqualTo(5);
        assertThat(result.band()).isEqualTo("Boa qualidade de sono");
    }

    @Test
    void pittsburghNuncaPassaDeVinteEUm() {
        Map<String, Object> answers = sleeperWhoSleepsBadly();

        ScaleScorer.ScoreResult result = scorer.score(ScaleType.ESCALA_PITTSBURGH, answers);

        assertThat(result.score()).isEqualTo(21);
        assertThat(result.band()).isEqualTo("Qualidade de sono ruim");
    }

    // a eficiencia habitual eh calculada, n perguntada, e sem os horarios
    // n da pra fechar o indice
    @Test
    void pittsburghSemOsHorariosNaoTemIndice() {
        Map<String, Object> answers = sleeperWhoSleepsWell();
        answers.remove("horaLevantar");

        assertThat(scorer.score(ScaleType.ESCALA_PITTSBURGH, answers).score()).isNull();
    }

    // o parceiro de quarto eh contexto e n entra no indice
    @Test
    void pittsburghNaoMudaComOParceiroDeQuarto() {
        Map<String, Object> answers = sleeperWhoSleepsWell();
        Integer withoutPartner = scorer.score(ScaleType.ESCALA_PITTSBURGH, answers).score();

        answers.put("parceiroDeQuarto", 3);

        assertThat(scorer.score(ScaleType.ESCALA_PITTSBURGH, answers).score()).isEqualTo(withoutPartner);
    }

    // MEEM de 0 a 30 com o ponto de corte por escolaridade (Brucki 2003)
    @Test
    void miniExameSomaAsOnzeSecoes() {
        ScaleScorer.ScoreResult result = scorer.score(ScaleType.MINI_EXAME_ESTADO_MENTAL, fullMentalStateExam(1));

        assertThat(result.score()).isEqualTo(30);
        assertThat(result.band()).isEqualTo("Dentro do esperado para a escolaridade (corte 25)");
    }

    @Test
    void miniExameUsaOCorteDaEscolaridade() {
        Map<String, Object> answers = fullMentalStateExam(4);
        // 30 pontos menos 2 da atencao e 2 da evocacao
        answers.put("atencaoECalculo", 3);
        answers.put("memoriaEvocacao", 1);

        ScaleScorer.ScoreResult result = scorer.score(ScaleType.MINI_EXAME_ESTADO_MENTAL, answers);

        assertThat(result.score()).isEqualTo(26);
        assertThat(result.band()).isEqualTo("Abaixo do esperado para a escolaridade (corte 29)");
    }

    // o corte de 5 a 8 anos de estudo eh 26,5, entao 26 fica abaixo e 27 dentro
    @Test
    void miniExameMostraOCorteQuebradoComVirgula() {
        Map<String, Object> answers = fullMentalStateExam(2);
        answers.put("atencaoECalculo", 1);

        assertThat(scorer.score(ScaleType.MINI_EXAME_ESTADO_MENTAL, answers).band())
                .isEqualTo("Abaixo do esperado para a escolaridade (corte 26,5)");

        answers.put("atencaoECalculo", 2);

        assertThat(scorer.score(ScaleType.MINI_EXAME_ESTADO_MENTAL, answers).band())
                .isEqualTo("Dentro do esperado para a escolaridade (corte 26,5)");
    }

    @Test
    void miniExameSemEscolaridadeNaoTemFaixa() {
        Map<String, Object> answers = fullMentalStateExam(1);
        answers.remove("escolaridade");

        ScaleScorer.ScoreResult result = scorer.score(ScaleType.MINI_EXAME_ESTADO_MENTAL, answers);

        assertThat(result.score()).isEqualTo(30);
        assertThat(result.band()).isNull();
    }

    // o registro de dor usa as faixas da escala visual do formulario
    @Test
    void dorUsaAsFaixasDaEscalaVisual() {
        assertThat(painBandOf(1)).isEqualTo("Dor leve");
        assertThat(painBandOf(5)).isEqualTo("Dor moderada");
        assertThat(painBandOf(9)).isEqualTo("Dor intensa");
    }

    // o diario do sono calcula tempo na cama, total acordado e tempo de sono
    @Test
    void diarioDoSonoCalculaOsTemposDerivados() {
        Map<String, Object> answers = new LinkedHashMap<>();
        answers.put("horarioDormir", "23:00");
        answers.put("horarioLevantar", "07:00");
        answers.put("tempoAteDormir", 20);
        answers.put("tempoAcordadoNoite", 25);

        Map<String, Object> completed = scorer.withComputedAnswers(ScaleType.REGISTRO_SONO, answers);

        assertThat(completed.get("tempoNaCama")).isEqualTo(480);
        assertThat(completed.get("totalAcordado")).isEqualTo(45);
        assertThat(completed.get("tempoTotalSono")).isEqualTo(435);
    }

    // sem os dois horarios n da pra calcular, e a conta n vira zero
    @Test
    void diarioDoSonoSemHorarioNaoCalcula() {
        Map<String, Object> answers = new LinkedHashMap<>();
        answers.put("horarioDormir", "23:00");
        answers.put("tempoAteDormir", 20);

        Map<String, Object> completed = scorer.withComputedAnswers(ScaleType.REGISTRO_SONO, answers);

        assertThat(completed).doesNotContainKey("tempoNaCama");
        assertThat(completed).doesNotContainKey("tempoTotalSono");
    }

    private String hamiltonBandOf(int valuePerItem) {
        return scorer.score(ScaleType.ESCALA_HAMILTON, allHamiltonItemsWith(valuePerItem)).band();
    }

    private String painBandOf(int intensity) {
        return scorer.score(ScaleType.REGISTRO_DOR, Map.of("intensidadeDor", intensity)).band();
    }

    private Map<String, Object> allHamiltonItemsWith(int value) {
        Map<String, Object> answers = new HashMap<>();
        catalog.definitionOf(ScaleType.ESCALA_HAMILTON).items()
                .forEach(item -> answers.put(item.key(), value));
        return answers;
    }

    private Map<String, Object> fullMentalStateExam(int schooling) {
        Map<String, Object> answers = new HashMap<>();
        catalog.definitionOf(ScaleType.MINI_EXAME_ESTADO_MENTAL).items()
                .forEach(item -> answers.put(item.key(), item.maxValue()));
        answers.put("escolaridade", schooling);
        return answers;
    }

    // dorme 7 horas em 8 na cama, com pouca queixa
    private Map<String, Object> sleeperWhoSleepsWell() {
        Map<String, Object> answers = new HashMap<>();
        catalog.definitionOf(ScaleType.ESCALA_PITTSBURGH).items().stream()
                .filter(item -> item.key().startsWith("freq"))
                .forEach(item -> answers.put(item.key(), 0));
        answers.put("horaDeitar", "23:00");
        answers.put("horaLevantar", "07:00");
        answers.put("horasDeSono", 7.0);
        answers.put("minutosParaDormir", 20);
        answers.put("freqNaoAdormeceu", 1);
        answers.put("freqDor", 1);
        answers.put("qualidadeGeral", 1);
        answers.put("freqDificuldadeAcordado", 1);
        answers.put("dificuldadeEntusiasmo", 1);
        return answers;
    }

    // dorme 4 horas em 7 na cama, com queixa em tudo
    private Map<String, Object> sleeperWhoSleepsBadly() {
        Map<String, Object> answers = new HashMap<>();
        catalog.definitionOf(ScaleType.ESCALA_PITTSBURGH).items().stream()
                .filter(item -> item.key().startsWith("freq"))
                .forEach(item -> answers.put(item.key(), 3));
        answers.put("horaDeitar", "23:00");
        answers.put("horaLevantar", "06:00");
        answers.put("horasDeSono", 4.0);
        answers.put("minutosParaDormir", 90);
        answers.put("qualidadeGeral", 3);
        answers.put("dificuldadeEntusiasmo", 3);
        return answers;
    }
}
