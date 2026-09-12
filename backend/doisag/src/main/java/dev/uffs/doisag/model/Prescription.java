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
    // campos que a tela ja coletava e iam todos amontoados dentro de
    // observation, perdendo a estrutura
    private String volume;
    private String administrationRoute;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Column(columnDefinition = "TEXT")
    private String precautions;

    @Column(columnDefinition = "TEXT")
    private String expectedEffects;

    private Integer treatmentDurationDays;
    private java.time.LocalDate nextConsultationDate;

    // o plano de subida de dose, semana a semana
    @OneToMany(mappedBy = "prescription", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<DoseEscalationStep> escalationSteps = new java.util.ArrayList<>();

    public String getVolume() {
        return volume;
    }

    public void setVolume(String volume) {
        this.volume = volume;
    }

    public String getAdministrationRoute() {
        return administrationRoute;
    }

    public void setAdministrationRoute(String administrationRoute) {
        this.administrationRoute = administrationRoute;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public String getPrecautions() {
        return precautions;
    }

    public void setPrecautions(String precautions) {
        this.precautions = precautions;
    }

    public String getExpectedEffects() {
        return expectedEffects;
    }

    public void setExpectedEffects(String expectedEffects) {
        this.expectedEffects = expectedEffects;
    }

    public Integer getTreatmentDurationDays() {
        return treatmentDurationDays;
    }

    public void setTreatmentDurationDays(Integer treatmentDurationDays) {
        this.treatmentDurationDays = treatmentDurationDays;
    }

    public java.time.LocalDate getNextConsultationDate() {
        return nextConsultationDate;
    }

    public void setNextConsultationDate(java.time.LocalDate nextConsultationDate) {
        this.nextConsultationDate = nextConsultationDate;
    }

    public java.util.List<DoseEscalationStep> getEscalationSteps() {
        return escalationSteps;
    }

    // adiciona um degrau ja amarrando os dois lados da relacao
    public void addEscalationStep(DoseEscalationStep step) {
        step.setPrescription(this);
        this.escalationSteps.add(step);
    }

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
