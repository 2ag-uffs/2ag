package dev.uffs.doisag.infra;

import java.util.Locale;

// limpa o q a pessoa digitou antes de gravar ou comparar
public class InputCleaner {

    private InputCleaner() {
    }

    // e-mail vale igual com letra maiuscula ou minuscula e sem espaco nas pontas
    public static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // cpf e telefone ficam guardados so com os numeros
    public static String keepOnlyDigits(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("\\D", "");
    }

    // tira o espaco das pontas e texto em branco vira nulo
    public static String textOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    // nome social igual ao civil n muda nada na tela, entao fica nulo tbm (RN15)
    public static String socialNameOrNull(String socialName, String civilName) {
        String cleanSocialName = textOrNull(socialName);
        if (cleanSocialName == null || civilName == null) {
            return cleanSocialName;
        }
        if (cleanSocialName.equalsIgnoreCase(civilName.trim())) {
            return null;
        }
        return cleanSocialName;
    }
}
