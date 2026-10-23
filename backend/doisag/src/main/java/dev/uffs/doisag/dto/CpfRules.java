package dev.uffs.doisag.dto;

// conta os dois digitos verificadores do cpf, a regra do modulo 11, sem biblioteca
// o navegador manda so os 11 numeros, mas a mascara 000.000.000-00 tbm passa, como antes
public final class CpfRules {

    private CpfRules() {
    }

    // vazio fica com o @NotBlank, igual a biblioteca fazia
    public static boolean isValid(String cpf) {
        if (cpf == null || cpf.isBlank()) {
            return true;
        }
        String digits = digitsOf(cpf);
        if (digits == null || isSameDigitRepeated(digits)) {
            return false;
        }
        return digits.equals(withCheckDigits(digits.substring(0, 9)));
    }

    // monta um cpf valido a partir dos 9 primeiros numeros: os dois ultimos sao a conta
    public static String withCheckDigits(String firstNineDigits) {
        String digits = firstNineDigits;
        for (int round = 0; round < 2; round++) {
            digits = digits + checkDigit(digits);
        }
        return digits;
    }

    // peso 10..2 sobre os 9 primeiros (ou 11..2 sobre os 10), resto da divisao por 11
    private static int checkDigit(String digits) {
        int sum = 0;
        int weight = digits.length() + 1;
        for (int index = 0; index < digits.length(); index++) {
            sum = sum + Character.getNumericValue(digits.charAt(index)) * (weight - index);
        }
        int rest = sum % 11;
        return rest < 2 ? 0 : 11 - rest;
    }

    // so os 11 numeros ou a mascara inteira; qualquer outra forma n vale
    private static String digitsOf(String cpf) {
        if (cpf.matches("[0-9]{11}")) {
            return cpf;
        }
        if (cpf.matches("[0-9]{3}\\.[0-9]{3}\\.[0-9]{3}-[0-9]{2}")) {
            return cpf.replace(".", "").replace("-", "");
        }
        return null;
    }

    // 111.111.111-11 fecha a conta mas n eh cpf de ninguem
    private static boolean isSameDigitRepeated(String digits) {
        return digits.equals(String.valueOf(digits.charAt(0)).repeat(digits.length()));
    }
}
