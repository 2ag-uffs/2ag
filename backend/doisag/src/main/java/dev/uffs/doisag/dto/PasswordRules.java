package dev.uffs.doisag.dto;

import java.nio.charset.StandardCharsets;

// regra de senha forte de todo lugar q cria ou troca senha (RF02.1)
// a tela mostra a mesma regra enquanto a pessoa digita
public final class PasswordRules {

    // de 8 a 64 caracteres com pelo menos uma letra maiuscula um numero e um simbolo
    // simbolo eh tudo q n eh letra nem numero e letra com acento conta como letra
    public static final String PATTERN = "^(?=.*\\p{Lu})(?=.*\\d)(?=.*[^\\p{L}\\p{N}]).{8,64}$";

    public static final String MESSAGE =
            "A senha precisa ter de 8 a 64 caracteres, com pelo menos uma letra maiúscula, um número e um símbolo";

    // o bcrypt recusa senha acima de 72 bytes, e letra com acento ou emoji ocupa mais de um
    private static final int MAX_BYTES = 72;

    public static final String TOO_LONG_MESSAGE =
            "A senha ficou longa demais. Letra com acento e emoji ocupam mais espaço, use uma senha mais curta";

    private PasswordRules() {
    }

    public static boolean fitsInBcrypt(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }
}
