package dev.uffs.doisag.service;

// soma os itens de uma escala do jeito certo: se algum n foi
// respondido, o formulario esta incompleto e n existe escore valido.
// somar so o que veio daria um numero menor e a prescritora leria isso
// como sintoma mais leve, que eh justamente o contrario do que aconteceu
public final class ScoreHelper {

    private ScoreHelper() {
    }

    public static Integer sumOrNull(Integer... items) {
        int total = 0;
        for (Integer item : items) {
            if (item == null) {
                return null;
            }
            total += item;
        }
        return total;
    }

    // media que ignora quem n respondeu, em vez de contar como zero
    public static double averageIgnoringNulls(java.util.List<Integer> values) {
        return values.stream()
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);
    }
}
