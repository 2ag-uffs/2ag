package dev.uffs.doisag.enums;

// o q aconteceu com o registro na trilha de auditoria
public enum AuditOperation {
    CRIACAO("Criação"),
    ALTERACAO("Alteração"),
    VISUALIZACAO("Acesso"),
    ANULACAO("Anulação"),
    ARQUIVAMENTO("Arquivamento"),
    REATIVACAO("Reativação"),
    REDEFINICAO_DE_SENHA("Redefinição de senha");

    private final String label;

    AuditOperation(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
