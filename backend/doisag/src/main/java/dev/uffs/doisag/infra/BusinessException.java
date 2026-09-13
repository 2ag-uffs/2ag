package dev.uffs.doisag.infra;

// regra de negocio violada e a api responde 400 com a mensagem pro usuario
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
