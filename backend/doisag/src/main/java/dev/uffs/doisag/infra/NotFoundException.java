package dev.uffs.doisag.infra;

// o registro pedido n existe e a api responde 404
//
// a mensagem normal eh tecnica ("paciente nao encontrado com o id: 42") e serve
// pro log, n pra tela: ela mostra id interno pra quem esta usando o sistema
//
// qnd a mensagem foi escrita pra pessoa ler, como a do convite vencido, use o
// forUser e ela passa direto pra resposta
public class NotFoundException extends RuntimeException {

    private final boolean messageForUser;

    public NotFoundException(String message) {
        this(message, false);
    }

    private NotFoundException(String message, boolean messageForUser) {
        super(message);
        this.messageForUser = messageForUser;
    }

    public static NotFoundException forUser(String message) {
        return new NotFoundException(message, true);
    }

    public boolean hasMessageForUser() {
        return messageForUser;
    }
}
