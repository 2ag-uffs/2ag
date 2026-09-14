package dev.uffs.doisag.infra;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// o csv abre no excel entao texto escrito pelo paciente n pode virar formula (RF33)
class CsvBuilderTest {

    @Test
    void textStartingLikeAFormulaGetsAnApostrophe() {
        String csv = new CsvBuilder("Comentario")
                .line("=HYPERLINK(\"http://site-estranho\")")
                .line("+1")
                .line("-2")
                .line("@SOMA(A1)")
                .build();

        assertThat(csv).contains("\"'=HYPERLINK(\"\"http://site-estranho\"\")\"");
        assertThat(csv).contains("\"'+1\"");
        assertThat(csv).contains("\"'-2\"");
        assertThat(csv).contains("\"'@SOMA(A1)\"");
    }

    @Test
    void negativeNumbersAndPlainTextStayTheSame() {
        String csv = new CsvBuilder("Valor", "Texto").line(-3, "Dor menor hoje").build();

        assertThat(csv).contains("\"-3\";\"Dor menor hoje\"");
    }
}
