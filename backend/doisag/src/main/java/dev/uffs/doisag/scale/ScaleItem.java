package dev.uffs.doisag.scale;

import java.util.List;

// um item do formulario, do jeito q ele esta no papel da clinica
//
// as ancoras e as opcoes ficam aqui pq o texto de cada ponta muda o
// sentido da resposta: em dor o 10 eh ruim e em sono o 10 eh bom.
// quem le o grafico precisa saber disso, entao o higherIsBetter anda
// junto com o item (RF20)
public record ScaleItem(
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
        boolean trackable) {

    // nota numa regua, tipo os itens de 0 a 10 da ficha de acompanhamento
    public static ScaleItem nota(String key, String label, int minValue, int maxValue,
                                 String lowAnchor, String highAnchor, boolean higherIsBetter) {
        return new ScaleItem(key, label, null, ScaleItemType.NOTA, minValue, maxValue,
                lowAnchor, highAnchor, higherIsBetter, List.of(), true);
    }

    // nota com um texto de apoio embaixo, tipo os itens do hamilton
    public static ScaleItem notaComAjuda(String key, String label, String help, int minValue, int maxValue,
                                         String lowAnchor, String highAnchor, boolean higherIsBetter) {
        return new ScaleItem(key, label, help, ScaleItemType.NOTA, minValue, maxValue,
                lowAnchor, highAnchor, higherIsBetter, List.of(), true);
    }

    public static ScaleItem escolha(String key, String label, String help, List<ScaleOption> options,
                                    boolean higherIsBetter) {
        int maxValue = options.stream().mapToInt(ScaleOption::value).max().orElse(0);
        int minValue = options.stream().mapToInt(ScaleOption::value).min().orElse(0);
        return new ScaleItem(key, label, help, ScaleItemType.ESCOLHA, minValue, maxValue,
                null, null, higherIsBetter, options, true);
    }

    public static ScaleItem simples(String key, String label, String help, ScaleItemType type) {
        return new ScaleItem(key, label, help, type, null, null, null, null, true, List.of(),
                type == ScaleItemType.NUMERO || type == ScaleItemType.MINUTOS || type == ScaleItemType.HORAS);
    }

    public static ScaleItem texto(String key, String label, String help) {
        return new ScaleItem(key, label, help, ScaleItemType.TEXTO, null, null, null, null, true,
                List.of(), false);
    }

    // valor q sai de conta, como o tempo total de sono do diario
    public static ScaleItem calculado(String key, String label, String help) {
        return new ScaleItem(key, label, help, ScaleItemType.CALCULADO, null, null, null, null, true,
                List.of(), true);
    }

    public boolean isNumeric() {
        return type != ScaleItemType.TEXTO && type != ScaleItemType.HORA && type != ScaleItemType.SIM_NAO;
    }
}
