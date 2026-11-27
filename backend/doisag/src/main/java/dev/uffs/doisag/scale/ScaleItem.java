package dev.uffs.doisag.scale;

import java.util.List;

// um item do formulario, do jeito q ele esta no papel da clinica
//
// as ancoras e as opcoes ficam aqui pq o texto de cada ponta muda o
// sentido da resposta: em dor o 10 eh ruim e em sono o 10 eh bom.
// quem le o grafico precisa saber disso, entao o higherIsBetter anda
// junto com o item (RF20)
//
// o section eh a pergunta q o papel usa pra juntar varios itens, tipo a 5 do
// pittsburgh. so a tela usa, pra mostrar ela uma vez em cima dos itens, e o
// escore n olha pra ela. nulo no item q fica sozinho
//
// o question eh a pergunta inteira do jeito q o papel faz. a tela mostra ela no lugar do label, e o
// label fica de nome curto pro grafico, pro csv, pra lista do q falta e pra leitura da resposta. nulo
// no item q o papel ja pergunta com o proprio label
//
// o intro eh a frase q o papel poe antes do item, tipo o "para cada uma das questoes restantes" do
// pittsburgh. tbm so a tela usa
//
// o period eh o lembrete do periodo da escala, tipo "Na última semana". eh frase do sistema e n do papel:
// a tela mostra numa linha so dela em cima da pergunta, e a pergunta do instrumento continua como eh
// (decisao da dona do projeto, novembro de 2026)
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
        boolean trackable,
        String section,
        String question,
        String intro,
        String period) {

    // nota numa regua, tipo os itens de 0 a 10 da ficha de acompanhamento
    public static ScaleItem nota(String key, String label, int minValue, int maxValue,
                                 String lowAnchor, String highAnchor, boolean higherIsBetter) {
        return new ScaleItem(key, label, null, ScaleItemType.NOTA, minValue, maxValue,
                lowAnchor, highAnchor, higherIsBetter, List.of(), true, null, null, null, null);
    }

    // nota com um texto de apoio embaixo, tipo os itens do hamilton
    public static ScaleItem notaComAjuda(String key, String label, String help, int minValue, int maxValue,
                                         String lowAnchor, String highAnchor, boolean higherIsBetter) {
        return new ScaleItem(key, label, help, ScaleItemType.NOTA, minValue, maxValue,
                lowAnchor, highAnchor, higherIsBetter, List.of(), true, null, null, null, null);
    }

    public static ScaleItem escolha(String key, String label, String help, List<ScaleOption> options,
                                    boolean higherIsBetter) {
        int maxValue = options.stream().mapToInt(ScaleOption::value).max().orElse(0);
        int minValue = options.stream().mapToInt(ScaleOption::value).min().orElse(0);
        return new ScaleItem(key, label, help, ScaleItemType.ESCOLHA, minValue, maxValue,
                null, null, higherIsBetter, options, true, null, null, null, null);
    }

    public static ScaleItem simples(String key, String label, String help, ScaleItemType type) {
        return new ScaleItem(key, label, help, type, null, null, null, null, true, List.of(),
                type == ScaleItemType.NUMERO || type == ScaleItemType.MINUTOS || type == ScaleItemType.HORAS,
                null, null, null, null);
    }

    public static ScaleItem texto(String key, String label, String help) {
        return new ScaleItem(key, label, help, ScaleItemType.TEXTO, null, null, null, null, true,
                List.of(), false, null, null, null, null);
    }

    // valor q sai de conta, como o tempo total de sono do diario
    public static ScaleItem calculado(String key, String label, String help) {
        return new ScaleItem(key, label, help, ScaleItemType.CALCULADO, null, null, null, null, true,
                List.of(), true, null, null, null, null);
    }

    // o msm item embaixo da pergunta q o papel usa pra juntar os itens dela
    public ScaleItem inSection(String newSection) {
        return new ScaleItem(key, label, help, type, minValue, maxValue, lowAnchor, highAnchor, higherIsBetter,
                options, trackable, newSection, question, intro, period);
    }

    // o msm item com a pergunta inteira do papel
    public ScaleItem withQuestion(String paperQuestion) {
        return new ScaleItem(key, label, help, type, minValue, maxValue, lowAnchor, highAnchor, higherIsBetter,
                options, trackable, section, paperQuestion, intro, period);
    }

    // o msm item com a frase q o papel poe antes dele
    public ScaleItem withIntro(String paperIntro) {
        return new ScaleItem(key, label, help, type, minValue, maxValue, lowAnchor, highAnchor, higherIsBetter,
                options, trackable, section, question, paperIntro, period);
    }

    // o msm item com o lembrete do periodo em cima da pergunta
    public ScaleItem withPeriod(String periodReminder) {
        return new ScaleItem(key, label, help, type, minValue, maxValue, lowAnchor, highAnchor, higherIsBetter,
                options, trackable, section, question, intro, periodReminder);
    }

    public boolean isNumeric() {
        return type != ScaleItemType.TEXTO && type != ScaleItemType.HORA && type != ScaleItemType.SIM_NAO;
    }
}
