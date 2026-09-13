package dev.uffs.doisag.dto;

// regra de senha forte de todo lugar q cria ou troca senha (RF02.1)
// a tela mostra a mesma regra enquanto a pessoa digita
public final class PasswordRules {

    // de 8 a 64 caracteres com pelo menos uma letra maiuscula um numero e um simbolo
    // simbolo eh tudo q n eh letra nem numero e letra com acento conta como letra
    public static final String PATTERN = "^(?=.*\\p{Lu})(?=.*\\d)(?=.*[^\\p{L}\\p{N}]).{8,64}$";

    public static final String MESSAGE =
            "A senha precisa ter de 8 a 64 caracteres, com pelo menos uma letra maiúscula, um número e um símbolo";

    private PasswordRules() {
    }
}
