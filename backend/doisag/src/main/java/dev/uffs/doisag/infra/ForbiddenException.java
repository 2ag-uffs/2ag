package dev.uffs.doisag.infra;

// quem esta logado n pode mexer nesse registro e a api responde 403
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
