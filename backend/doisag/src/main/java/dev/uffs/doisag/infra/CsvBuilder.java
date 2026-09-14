package dev.uffs.doisag.infra;

// monta o texto de um csv linha a linha
//
// separa por ponto e virgula e comeca com a marca de utf-8 pq eh assim
// q o excel em portugues abre o arquivo com acento certo (RF33)
public class CsvBuilder {

    private static final String SEPARATOR = ";";
    private static final String UTF8_MARK = "﻿";

    private final StringBuilder content = new StringBuilder();

    public CsvBuilder(String... headers) {
        line((Object[]) headers);
    }

    public CsvBuilder line(Object... values) {
        for (int index = 0; index < values.length; index = index + 1) {
            if (index > 0) {
                content.append(SEPARATOR);
            }
            content.append(cell(values[index]));
        }
        content.append("\n");
        return this;
    }

    public String build() {
        return UTF8_MARK + content;
    }

    // tudo entre aspas: assim texto clinico com ponto e virgula ou com
    // quebra de linha n estraga as colunas
    private String cell(Object value) {
        if (value == null) {
            return "\"\"";
        }
        String text = String.valueOf(value);
        // texto q comeca como formula rodaria no excel de quem abre o arquivo
        // o apostrofo na frente faz o excel mostrar como texto e numero fica como esta
        if (value instanceof CharSequence && startsLikeFormula(text)) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private boolean startsLikeFormula(String text) {
        if (text.isEmpty()) {
            return false;
        }
        char first = text.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r';
    }
}
