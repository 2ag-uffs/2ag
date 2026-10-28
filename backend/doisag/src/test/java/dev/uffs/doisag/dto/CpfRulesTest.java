package dev.uffs.doisag.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// a conta dos dois digitos verificadores do cpf, q ficou no lugar do @CPF da biblioteca
class CpfRulesTest {

    @Test
    void cpfWithTheRightCheckDigitsPasses() {
        assertThat(CpfRules.isValid("52998224725")).isTrue();
        assertThat(CpfRules.isValid("16899535009")).isTrue();
        assertThat(CpfRules.isValid("11144477735")).isTrue();
        assertThat(CpfRules.isValid(CpfRules.withCheckDigits("286415937"))).isTrue();
    }

    // peso 10..2 e 11..2, resto por 11; resto 0 ou 1 vira digito 0
    @Test
    void theCheckDigitsFollowTheModulo11Rule() {
        assertThat(CpfRules.withCheckDigits("529982247")).isEqualTo("52998224725");
        assertThat(CpfRules.withCheckDigits("168995350")).isEqualTo("16899535009");
        assertThat(CpfRules.withCheckDigits("111444777")).isEqualTo("11144477735");
    }

    @Test
    void wrongFirstCheckDigitIsRefused() {
        assertThat(CpfRules.isValid("52998224735")).isFalse();
        assertThat(CpfRules.isValid("12345678900")).isFalse();
    }

    @Test
    void wrongSecondCheckDigitIsRefused() {
        assertThat(CpfRules.isValid("52998224726")).isFalse();
        assertThat(CpfRules.isValid("16899535008")).isFalse();
    }

    // 111.111.111-11 fecha a conta mas n eh cpf de ninguem
    @Test
    void theSameDigitElevenTimesIsRefused() {
        for (char digit = '0'; digit <= '9'; digit++) {
            assertThat(CpfRules.isValid(String.valueOf(digit).repeat(11))).isFalse();
        }
    }

    // com a mascara a repeticao tbm n passa
    @Test
    void theSameDigitElevenTimesWithTheMaskIsRefused() {
        for (char digit = '0'; digit <= '9'; digit++) {
            String three = String.valueOf(digit).repeat(3);
            String masked = three + "." + three + "." + three + "-" + digit + digit;
            assertThat(CpfRules.isValid(masked)).isFalse();
        }
    }

    @Test
    void tenOrTwelveDigitsAreRefused() {
        assertThat(CpfRules.isValid("5299822472")).isFalse();
        assertThat(CpfRules.isValid("529982247255")).isFalse();
        assertThat(CpfRules.isValid("052998224725")).isFalse();
    }

    @Test
    void lettersAreRefused() {
        assertThat(CpfRules.isValid("5299822472a")).isFalse();
        assertThat(CpfRules.isValid("abcdefghijk")).isFalse();
        assertThat(CpfRules.isValid("529.982.247-2x")).isFalse();
    }

    // o navegador manda so os numeros, mas a mascara inteira passa como antes; pela metade n
    @Test
    void theFullMaskPassesAndHalfMasksAreRefused() {
        assertThat(CpfRules.isValid("529.982.247-25")).isTrue();
        assertThat(CpfRules.isValid("529982247-25")).isFalse();
        assertThat(CpfRules.isValid("529.982.24725")).isFalse();
        assertThat(CpfRules.isValid(" 52998224725")).isFalse();
        assertThat(CpfRules.isValid("529 982 247 25")).isFalse();
    }

    // vazio eh problema do @NotBlank, igual a biblioteca fazia
    @Test
    void nullAndBlankAreLeftToNotBlank() {
        assertThat(CpfRules.isValid(null)).isTrue();
        assertThat(CpfRules.isValid("")).isTrue();
        assertThat(CpfRules.isValid("   ")).isTrue();
    }

    @Test
    void aSingleCharIsRefused() {
        assertThat(CpfRules.isValid("5")).isFalse();
        assertThat(CpfRules.isValid("a")).isFalse();
    }
}
