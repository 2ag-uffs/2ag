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

    // as buscas q se repetem nos servicos ficam c/ a msm mensagem
    public static NotFoundException patient(Long patientId) {
        return new NotFoundException("Paciente não encontrado com o id: " + patientId);
    }

    public static NotFoundException appointment(Long appointmentId) {
        return new NotFoundException("Consulta não encontrada com o id: " + appointmentId);
    }

    public static NotFoundException prescriber(Long prescriberId) {
        return new NotFoundException("Prescritor não encontrado com o id: " + prescriberId);
    }

    public static NotFoundException account() {
        return new NotFoundException("Conta não encontrada");
    }

    public boolean hasMessageForUser() {
        return messageForUser;
    }
}
