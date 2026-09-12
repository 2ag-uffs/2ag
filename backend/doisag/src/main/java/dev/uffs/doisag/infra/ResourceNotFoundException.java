package dev.uffs.doisag.infra;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// anotação para garantir que a exceção retorne um 404
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    // construtor que recebe a mensagem de erro
    public ResourceNotFoundException(String message) {
        super(message);
    }
}