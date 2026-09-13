package dev.uffs.doisag.infra;

// muitas senhas erradas seguidas deixam o login bloqueado por um tempo
// a api responde 429
public class LoginBlockedException extends RuntimeException {

    public LoginBlockedException(String message) {
        super(message);
    }
}
