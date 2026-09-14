package dev.uffs.doisag.scale;

import java.util.List;

// a faixa interpretativa de um escore, q a RN14 manda mostrar sempre
// junto com o numero
public record ScoreBand(int minScore, int maxScore, String label) {

    public boolean contains(int score) {
        return score >= minScore && score <= maxScore;
    }

    public static String labelOf(List<ScoreBand> bands, Integer score) {
        if (score == null) {
            return null;
        }
        return bands.stream()
                .filter(band -> band.contains(score))
                .map(ScoreBand::label)
                .findFirst()
                .orElse(null);
    }
}
