package dev.uffs.doisag.enums;

// situacao da tarefa de escala q o paciente recebe
public enum ScaleTaskStatus {

    // ainda da tempo de responder
    PENDENTE,
    // o paciente respondeu dentro do periodo
    RESPONDIDA,
    // o periodo acabou sem resposta e vira lacuna no grafico (RN10)
    NAO_RESPONDIDA;

    public boolean isOpen() {
        return this == PENDENTE;
    }
}
