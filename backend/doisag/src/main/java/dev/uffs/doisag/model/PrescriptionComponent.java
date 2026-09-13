package dev.uffs.doisag.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.uffs.doisag.enums.Cannabinoid;
import dev.uffs.doisag.enums.ConcentrationUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

// um canabinoide do oleo prescrito com a propria concentracao (RN03)
@Entity
@Table(name = "prescription_component")
public class PrescriptionComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescription_id", nullable = false)
    private Prescription prescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Cannabinoid cannabinoid;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal concentration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConcentrationUnit unit;

    // o jpa precisa de um construtor vazio
    protected PrescriptionComponent() {
    }

    public PrescriptionComponent(Cannabinoid cannabinoid, BigDecimal concentration, ConcentrationUnit unit) {
        this.cannabinoid = cannabinoid;
        this.concentration = concentration;
        this.unit = unit;
    }

    public Long getId() {
        return id;
    }

    // a volta pra prescricao n entra no json senao ele entra em loop
    @JsonIgnore
    public Prescription getPrescription() {
        return prescription;
    }

    public void setPrescription(Prescription prescription) {
        this.prescription = prescription;
    }

    public Cannabinoid getCannabinoid() {
        return cannabinoid;
    }

    public BigDecimal getConcentration() {
        return concentration;
    }

    public ConcentrationUnit getUnit() {
        return unit;
    }
}
