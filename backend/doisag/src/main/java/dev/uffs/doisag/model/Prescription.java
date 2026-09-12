package dev.uffs.doisag.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity
@EntityListeners(AuditingEntityListener.class)
public class Prescription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(columnDefinition = "TEXT")
    private String productDescription;
    @Column(columnDefinition = "TEXT")
    private String posology;
    private String brand;
    private String concentration;
    private String spectrum;
    @Column(columnDefinition = "TEXT")
    private String observation;

    // muitas prescrições podem pertencer a uma consulta
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false) // cria a coluna fk, não pode ser nula
    private Appointment appointment;

    public Prescription() {
    }

    public Prescription(Appointment appointment, String brand, String concentration, Long id, String observation, String posology, String productDescription, String spectrum) {
        this.appointment = appointment;
        this.brand = brand;
        this.concentration = concentration;
        this.id = id;
        this.observation = observation;
        this.posology = posology;
        this.productDescription = productDescription;
        this.spectrum = spectrum;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getConcentration() {
        return concentration;
    }

    public void setConcentration(String concentration) {
        this.concentration = concentration;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getObservation() {
        return observation;
    }

    public void setObservation(String observation) {
        this.observation = observation;
    }

    public String getPosology() {
        return posology;
    }

    public void setPosology(String posology) {
        this.posology = posology;
    }

    public String getProductDescription() {
        return productDescription;
    }

    public void setProductDescription(String productDescription) {
        this.productDescription = productDescription;
    }

    public String getSpectrum() {
        return spectrum;
    }

    public void setSpectrum(String spectrum) {
        this.spectrum = spectrum;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public void setAppointment(Appointment appointment) {
        this.appointment = appointment;
    }

    // quando o registro nasceu e quando foi mexido pela ultima vez.
    // o spring preenche sozinho, ninguem seta na mao (RF31)
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
