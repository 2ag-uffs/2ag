package dev.uffs.doisag.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.uffs.doisag.enums.PrescriptionStatus;
import dev.uffs.doisag.enums.Spectrum;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// prescricao emitida dentro de uma consulta (RF05)
// a nova substitui a vigente e a anterior continua no historico como substituida
@Entity
@EntityListeners(AuditingEntityListener.class)
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrescriptionStatus status = PrescriptionStatus.VIGENTE;

    // nome do produto do jeito q a prescritora escreve
    @Column(columnDefinition = "TEXT")
    private String productDescription;

    private String brand;

    // lote do produto (RN07)
    private String batch;

    @Enumerated(EnumType.STRING)
    private Spectrum spectrum;

    // cada canabinoide do oleo com a propria concentracao (RN03)
    @OneToMany(mappedBy = "prescription", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PrescriptionComponent> components = new ArrayList<>();

    private String volume;

    @Column(columnDefinition = "TEXT")
    private String posology;

    private String administrationRoute;

    // o plano de subida de dose semana a semana (RN02)
    @OneToMany(mappedBy = "prescription", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DoseEscalationStep> escalationSteps = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Column(columnDefinition = "TEXT")
    private String precautions;

    @Column(columnDefinition = "TEXT")
    private String expectedEffects;

    @Column(columnDefinition = "TEXT")
    private String observation;

    private Integer treatmentDurationDays;

    private LocalDate nextConsultationDate;

    // preenchido so quando a prescricao foi anulada
    @Embedded
    private Annulment annulment;

    // quando o registro nasceu e quando foi mexido pela ultima vez
    // o spring preenche sozinho e ninguem seta na mao (RF31)
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Prescription() {
    }

    // vale agora pro paciente quando eh a vigente e n foi anulada
    @JsonIgnore
    public boolean isCurrent() {
        return status == PrescriptionStatus.VIGENTE && !isAnnulled();
    }

    @JsonIgnore
    public boolean isAnnulled() {
        return annulment != null;
    }

    // adiciona um canabinoide ja amarrando os dois lados da relacao
    public void addComponent(PrescriptionComponent component) {
        component.setPrescription(this);
        this.components.add(component);
    }

    // adiciona um degrau ja amarrando os dois lados da relacao
    public void addEscalationStep(DoseEscalationStep step) {
        step.setPrescription(this);
        this.escalationSteps.add(step);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public void setAppointment(Appointment appointment) {
        this.appointment = appointment;
    }

    public PrescriptionStatus getStatus() {
        return status;
    }

    public void setStatus(PrescriptionStatus status) {
        this.status = status;
    }

    public String getProductDescription() {
        return productDescription;
    }

    public void setProductDescription(String productDescription) {
        this.productDescription = productDescription;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getBatch() {
        return batch;
    }

    public void setBatch(String batch) {
        this.batch = batch;
    }

    public Spectrum getSpectrum() {
        return spectrum;
    }

    public void setSpectrum(Spectrum spectrum) {
        this.spectrum = spectrum;
    }

    public List<PrescriptionComponent> getComponents() {
        return components;
    }

    public String getVolume() {
        return volume;
    }

    public void setVolume(String volume) {
        this.volume = volume;
    }

    public String getPosology() {
        return posology;
    }

    public void setPosology(String posology) {
        this.posology = posology;
    }

    public String getAdministrationRoute() {
        return administrationRoute;
    }

    public void setAdministrationRoute(String administrationRoute) {
        this.administrationRoute = administrationRoute;
    }

    public List<DoseEscalationStep> getEscalationSteps() {
        return escalationSteps;
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

    public String getObservation() {
        return observation;
    }

    public void setObservation(String observation) {
        this.observation = observation;
    }

    public Integer getTreatmentDurationDays() {
        return treatmentDurationDays;
    }

    public void setTreatmentDurationDays(Integer treatmentDurationDays) {
        this.treatmentDurationDays = treatmentDurationDays;
    }

    public LocalDate getNextConsultationDate() {
        return nextConsultationDate;
    }

    public void setNextConsultationDate(LocalDate nextConsultationDate) {
        this.nextConsultationDate = nextConsultationDate;
    }

    // a anulacao sai na resposta pelos dtos e n pela entidade crua
    @JsonIgnore
    public Annulment getAnnulment() {
        return annulment;
    }

    public void setAnnulment(Annulment annulment) {
        this.annulment = annulment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
