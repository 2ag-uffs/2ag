package dev.uffs.doisag.infra;

// valor q precisa ser unico e ja pertence a outra conta como e-mail ou cpf
// a api responde 409 apontando o campo pro formulario destacar
public class DuplicateValueException extends RuntimeException {

    private final String field;

    public DuplicateValueException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
