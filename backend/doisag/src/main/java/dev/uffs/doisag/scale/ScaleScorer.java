package dev.uffs.doisag.scale;

import dev.uffs.doisag.enums.ScaleType;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// o calculo de cada escala, um metodo por instrumento
//
// RN13 escore de escala validada sai do algoritmo oficial e nada mais.
// item em branco n vira zero: sem todos os itens n existe escore, pq um
// numero menor seria lido como sintoma mais leve (RN10)
@Component
public class ScaleScorer {

    // pontos de corte do MEEM por anos de estudo (Brucki 2003)
    private static final double[] MEEM_CUTOFFS = {20, 25, 26.5, 28, 29};

    private final ScaleCatalog catalog;

    public ScaleScorer(ScaleCatalog catalog) {
        this.catalog = catalog;
    }

    public record ScoreResult(Integer score, String band) {

        public static ScoreResult empty() {
            return new ScoreResult(null, null);
        }
    }

    public ScoreResult score(ScaleType type, Map<String, Object> answers) {
        return switch (type) {
            case ESCALA_HAMILTON -> banded(type, hamiltonScore(answers));
            case ESCALA_PITTSBURGH -> banded(type, pittsburghScore(answers));
            case REGISTRO_DOR -> banded(type, intOf(answers, "intensidadeDor"));
            case MINI_EXAME_ESTADO_MENTAL -> mentalStateExamResult(answers);
            default -> ScoreResult.empty();
        };
    }

    // as escalas q tem faixa publicada leem a faixa da propria definicao
    private ScoreResult banded(ScaleType type, Integer score) {
        return new ScoreResult(score, ScoreBand.labelOf(catalog.definitionOf(type).bands(), score));
    }

    // HAM-A soma simples dos 14 itens, de 0 a 56 (Anexo A.1)
    private Integer hamiltonScore(Map<String, Object> answers) {
        return sumOrNull(answers, catalog.definitionOf(ScaleType.ESCALA_HAMILTON).items().stream()
                .map(ScaleItem::key)
                .toList());
    }

    // PSQI soma de 7 componentes derivados, cada um de 0 a 3 (Anexo A.2)
    private Integer pittsburghScore(Map<String, Object> answers) {
        Integer qualidadeSubjetiva = intOf(answers, "qualidadeGeral");
        Integer latencia = pittsburghLatency(answers);
        Integer duracao = pittsburghDuration(answers);
        Integer eficiencia = pittsburghEfficiency(answers);
        Integer disturbios = pittsburghDisturbances(answers);
        Integer medicacao = intOf(answers, "freqMedicacao");
        Integer disfuncaoDiurna = pittsburghDaytime(answers);

        // Arrays.asList aceita nulo e o List.of n, e componente sem
        // resposta chega aqui como nulo
        return sumOrNull(Arrays.asList(qualidadeSubjetiva, latencia, duracao, eficiencia, disturbios,
                medicacao, disfuncaoDiurna));
    }

    // C2 minutos ate dormir junto com a frequencia de n adormecer em 30 min
    private Integer pittsburghLatency(Map<String, Object> answers) {
        Integer minutes = intOf(answers, "minutosParaDormir");
        Integer frequency = intOf(answers, "freqNaoAdormeceu");
        if (minutes == null || frequency == null) {
            return null;
        }
        int minutesScore;
        if (minutes <= 15) {
            minutesScore = 0;
        } else if (minutes <= 30) {
            minutesScore = 1;
        } else if (minutes <= 60) {
            minutesScore = 2;
        } else {
            minutesScore = 3;
        }
        return convertSum(minutesScore + frequency, 2, 4);
    }

    // C3 horas dormidas por noite
    private Integer pittsburghDuration(Map<String, Object> answers) {
        Double hours = doubleOf(answers, "horasDeSono");
        if (hours == null) {
            return null;
        }
        if (hours > 7) {
            return 0;
        }
        if (hours >= 6) {
            return 1;
        }
        if (hours >= 5) {
            return 2;
        }
        return 3;
    }

    // C4 eficiencia habitual, quanto do tempo na cama virou sono de fato
    private Integer pittsburghEfficiency(Map<String, Object> answers) {
        Double sleptHours = doubleOf(answers, "horasDeSono");
        LocalTime bedTime = timeOf(answers, "horaDeitar");
        LocalTime wakeUpTime = timeOf(answers, "horaLevantar");
        if (sleptHours == null || bedTime == null || wakeUpTime == null) {
            return null;
        }
        double hoursInBed = minutesBetween(bedTime, wakeUpTime) / 60.0;
        if (hoursInBed <= 0) {
            return null;
        }
        double efficiency = (sleptHours / hoursInBed) * 100;
        if (efficiency >= 85) {
            return 0;
        }
        if (efficiency >= 75) {
            return 1;
        }
        if (efficiency >= 65) {
            return 2;
        }
        return 3;
    }

    // C5 os 9 motivos q atrapalharam o sono, do item 5B ao 5J
    private Integer pittsburghDisturbances(Map<String, Object> answers) {
        Integer total = sumOrNull(answers, List.of("freqAcordouNoite", "freqBanheiro", "freqRespirar",
                "freqTosseRonco", "freqFrio", "freqCalor", "freqSonhosRuins", "freqDor", "freqOutrasRazoes"));
        return total == null ? null : convertSum(total, 9, 18);
    }

    // C7 dificuldade de ficar acordado junto com a de manter o entusiasmo
    private Integer pittsburghDaytime(Map<String, Object> answers) {
        Integer total = sumOrNull(answers, List.of("freqDificuldadeAcordado", "dificuldadeEntusiasmo"));
        return total == null ? null : convertSum(total, 2, 4);
    }

    // varios componentes convertem a soma numa nota de 0 a 3 e muda so
    // onde ficam os cortes
    private int convertSum(int total, int upToOne, int upToTwo) {
        if (total == 0) {
            return 0;
        }
        if (total <= upToOne) {
            return 1;
        }
        if (total <= upToTwo) {
            return 2;
        }
        return 3;
    }

    // MEEM soma das 11 secoes, de 0 a 30, com o corte por escolaridade
    private ScoreResult mentalStateExamResult(Map<String, Object> answers) {
        List<String> sections = catalog.definitionOf(ScaleType.MINI_EXAME_ESTADO_MENTAL).items().stream()
                .map(ScaleItem::key)
                .filter(key -> !key.equals("escolaridade"))
                .toList();
        Integer score = sumOrNull(answers, sections);
        Integer schooling = intOf(answers, "escolaridade");
        if (score == null || schooling == null || schooling < 0 || schooling >= MEEM_CUTOFFS.length) {
            return new ScoreResult(score, null);
        }
        double cutoff = MEEM_CUTOFFS[schooling];
        String cutoffText = String.valueOf(cutoff).replace(".0", "").replace('.', ',');
        String band = score >= cutoff
                ? "Dentro do esperado para a escolaridade (corte " + cutoffText + ")"
                : "Abaixo do esperado para a escolaridade (corte " + cutoffText + ")";
        return new ScoreResult(score, band);
    }

    // o diario do sono tem tres campos q saem de conta e a pessoa n
    // preenche: tempo na cama, total acordado e tempo total de sono
    public Map<String, Object> withComputedAnswers(ScaleType type, Map<String, Object> answers) {
        if (type != ScaleType.REGISTRO_SONO) {
            return answers;
        }
        Map<String, Object> completed = new LinkedHashMap<>(answers);
        completed.remove("tempoNaCama");
        completed.remove("totalAcordado");
        completed.remove("tempoTotalSono");

        LocalTime bedTime = timeOf(answers, "horarioDormir");
        LocalTime wakeUpTime = timeOf(answers, "horarioLevantar");
        Integer timeInBed = bedTime == null || wakeUpTime == null ? null : minutesBetween(bedTime, wakeUpTime);
        Integer totalAwake = sumOrNull(answers, List.of("tempoAteDormir", "tempoAcordadoNoite"));

        if (timeInBed != null) {
            completed.put("tempoNaCama", timeInBed);
        }
        if (totalAwake != null) {
            completed.put("totalAcordado", totalAwake);
        }
        if (timeInBed != null && totalAwake != null) {
            completed.put("tempoTotalSono", timeInBed - totalAwake);
        }
        return completed;
    }

    // minutos entre dois horarios, somando um dia quando vira a noite
    private int minutesBetween(LocalTime start, LocalTime end) {
        Duration duration = Duration.between(start, end);
        if (duration.isNegative() || duration.isZero()) {
            duration = duration.plusDays(1);
        }
        return (int) duration.toMinutes();
    }

    private Integer sumOrNull(Map<String, Object> answers, List<String> keys) {
        return sumOrNull(keys.stream().map(key -> intOf(answers, key)).toList());
    }

    private Integer sumOrNull(List<Integer> values) {
        int total = 0;
        for (Integer value : values) {
            if (value == null) {
                return null;
            }
            total += value;
        }
        return total;
    }

    public Integer intOf(Map<String, Object> answers, String key) {
        Object value = answers.get(key);
        return value instanceof Number number ? number.intValue() : null;
    }

    private Double doubleOf(Map<String, Object> answers, String key) {
        Object value = answers.get(key);
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private LocalTime timeOf(Map<String, Object> answers, String key) {
        Object value = answers.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        return LocalTime.parse(text.length() > 5 ? text.substring(0, 5) : text);
    }
}
