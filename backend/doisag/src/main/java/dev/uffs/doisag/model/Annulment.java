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

    // tipo concreto de proposito: como Users o hibernate criava um proxy generico e avisava
    // "narrowing proxy" qnd o mesmo prescritor era carregado como Prescriber na mesma sessao
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "annulled_by_id")
    private Prescriber annulledBy;

    @Column(columnDefinition = "TEXT")
    private String annulmentReason;

    // o jpa precisa de um construtor vazio
    protected Annulment() {
    }

    public Annulment(Prescriber annulledBy, String annulmentReason) {
        this.annulledAt = LocalDateTime.now();
        this.annulledBy = annulledBy;
        this.annulmentReason = annulmentReason;
    }

    public LocalDateTime getAnnulledAt() {
        return annulledAt;
    }

    public Prescriber getAnnulledBy() {
        return annulledBy;
    }

    public String getAnnulmentReason() {
        return annulmentReason;
    }
}
