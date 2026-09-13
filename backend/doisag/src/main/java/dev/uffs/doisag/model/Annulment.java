package dev.uffs.doisag.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.LocalDateTime;

// anulacao de um registro clinico
// o registro errado n eh apagado e fica no historico com quem anulou quando e por que
@Embeddable
public class Annulment {

    private LocalDateTime annulledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "annulled_by_id")
    private Users annulledBy;

    @Column(columnDefinition = "TEXT")
    private String annulmentReason;

    // o jpa precisa de um construtor vazio
    protected Annulment() {
    }

    public Annulment(Users annulledBy, String annulmentReason) {
        this.annulledAt = LocalDateTime.now();
        this.annulledBy = annulledBy;
        this.annulmentReason = annulmentReason;
    }

    public LocalDateTime getAnnulledAt() {
        return annulledAt;
    }

    public Users getAnnulledBy() {
        return annulledBy;
    }

    public String getAnnulmentReason() {
        return annulmentReason;
    }
}
