package dev.uffs.doisag.infra;

// campo recusado por uma regra q so o servico confere como a senha atual errada
// a api responde 400 apontando o campo pro formulario destacar
public class InvalidFieldException extends RuntimeException {

    private final String field;

    public InvalidFieldException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
