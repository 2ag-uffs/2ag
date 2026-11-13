package dev.uffs.doisag.infra;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// o front acha o HH:mm nos avisos entao o texto tem q sair sempre igual
class BrazilianFormatTest {

    @Test
    void dateAndTimeGetLeadingZeros() {
        LocalDateTime dateTime = LocalDateTime.of(2026, 3, 5, 9, 5);

        assertThat(BrazilianFormat.date(dateTime.toLocalDate())).isEqualTo("05/03/2026");
        assertThat(BrazilianFormat.time(dateTime)).isEqualTo("09:05");
        assertThat(BrazilianFormat.dateTime(dateTime)).isEqualTo("05/03/2026 às 09:05");
    }

    @Test
    void emptyDateStaysEmpty() {
        assertThat(BrazilianFormat.date(null)).isNull();
    }

    // no brasil o ponto separa o milhar, entao 72.5 a planilha em portugues le como texto
    @Test
    void decimalGetsACommaWithoutUselessZeros() {
        assertThat(BrazilianFormat.decimal(72.5f)).isEqualTo("72,5");
        assertThat(BrazilianFormat.decimal(72.3f)).isEqualTo("72,3");
        assertThat(BrazilianFormat.decimal(80f)).isEqualTo("80");
        assertThat(BrazilianFormat.decimal(new BigDecimal("0.300"))).isEqualTo("0,3");
        assertThat(BrazilianFormat.decimal(null)).isNull();
    }

    @Test
    void minutesTurnIntoHoursFromSixtyOn() {
        assertThat(BrazilianFormat.minutes(45)).isEqualTo("45 min");
        assertThat(BrazilianFormat.minutes(60)).isEqualTo("1h00");
        assertThat(BrazilianFormat.minutes(425)).isEqualTo("7h05");
    }
}
