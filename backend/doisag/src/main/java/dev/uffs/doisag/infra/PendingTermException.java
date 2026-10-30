package dev.uffs.doisag.infra;

// paciente com o termo pendente (versao nova ou aceite de outra faixa de idade) tentou gravar algo antes de aceitar
// a api responde 403 com a mensagem pra tela mostrar, q serve pros dois casos
public class PendingTermException extends RuntimeException {

    public static final String MESSAGE = "Leia e aceite o termo de consentimento para continuar";
    public static final String HEADER = "X-Term-Pending";

    public PendingTermException() {
        super(MESSAGE);
    }
}
