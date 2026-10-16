package dev.uffs.doisag.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.LocalDate;

// um dia em q o prescritor n atende, tipo um feriado ou uma folga (RF11)
// o dia some dos horarios livres mas o q ja estava marcado nele continua
@Entity
public class PrescriberDayOff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescriber_id", nullable = false)
    private Prescriber prescriber;

    @Column(nullable = false)
    private LocalDate offDate;

    public Long getId() {
        return id;
    }

    public Prescriber getPrescriber() {
        return prescriber;
    }

    public void setPrescriber(Prescriber prescriber) {
        this.prescriber = prescriber;
    }

    public LocalDate getOffDate() {
        return offDate;
    }

    public void setOffDate(LocalDate offDate) {
        this.offDate = offDate;
    }
}
