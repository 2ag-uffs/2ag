package dev.uffs.doisag.infra;

// o registro pedido n existe e a api responde 404
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
