package dev.uffs.doisag.dto;

// regra de e-mail de todo lugar q cria ou troca o e-mail de uma conta
// o @Email aceita dominio sem ponto (maria@gmail), q no celular eh erro de digitacao
// e nesse endereco o link de senha nova e os lembretes nunca chegam
public final class EmailRules {

    // sem @ quem reclama eh o @Email, entao aqui so conta o ponto depois do ultimo @
    // entre o @ e o primeiro ponto n entra ponto, senao a regra volta atras e demora
    // c/ e-mail cheio de ponto, e o cadastro eh aberto, qualquer um chama
    public static final String DOMAIN_WITH_DOT = "[^@]*|.*@[^@.]*\\.[^@]*";

    public static final String DOMAIN_WITH_DOT_MESSAGE =
            "Confira o e-mail: falta o ponto depois do @, como em nome@gmail.com";

    private EmailRules() {
    }
}
