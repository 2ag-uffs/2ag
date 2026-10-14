package dev.uffs.doisag.infra;

// paciente com o termo desatualizado tentou gravar algo antes de aceitar a versao nova
// a api responde 403 com a mensagem pra tela mostrar
public class PendingTermException extends RuntimeException {

    public static final String MESSAGE = "O termo de consentimento foi atualizado. Leia e aceite a versão nova para continuar";
    public static final String HEADER = "X-Term-Pending";

    public PendingTermException() {
        super(MESSAGE);
    }
}
