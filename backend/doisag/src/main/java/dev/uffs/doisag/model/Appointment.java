package dev.uffs.doisag.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDateTime dateTime;
    // guardado como texto no banco (EnumType.STRING) e n como numero,
    // senao inserir um valor novo no meio do enum embaralha o historico
    @Enumerated(EnumType.STRING)
    private AppointmentModality modality;

    @Enumerated(EnumType.STRING)
    private AppointmentStatus status;
    @Column(columnDefinition = "TEXT")
    private String diagnosis;
    @Column(columnDefinition = "TEXT")
    private String clinicalObservation;
    @Column(columnDefinition = "TEXT")
    private String therapeuticPlan;
    @Column(columnDefinition = "TEXT")
    private String evolution;

    // o que a prescritora registra durante o exame do paciente.
    // peso e altura ficam numericos de proposito: sao os unicos dados da
    // consulta que fazem sentido acompanhar ao longo do tratamento
    @Column(columnDefinition = "TEXT")
    private String physicalExam;

    @Column(columnDefinition = "TEXT")
    private String complementaryExams;

    private String bloodPressure;
    private Float weight;
    private Integer height;

    // quanto tempo a consulta ocupa na agenda, em minutos. a tela ja
    // perguntava isso e a resposta era descartada
    private Integer durationMinutes = 60;

    // preenchido so quando o registro da consulta foi anulado
    @Embedded
    private Annulment annulment;

    // a anulacao sai na resposta pelos dtos e n pela entidade crua
    @JsonIgnore
    public Annulment getAnnulment() {
        return annulment;
    }

    public void setAnnulment(Annulment annulment) {
        this.annulment = annulment;
    }

    @JsonIgnore
    public boolean isAnnulled() {
        return annulment != null;
    }

    public String getPhysicalExam() {
        return physicalExam;
    }

    public void setPhysicalExam(String physicalExam) {
        this.physicalExam = physicalExam;
    }

    public String getComplementaryExams() {
        return complementaryExams;
    }

    public void setComplementaryExams(String complementaryExams) {
        this.complementaryExams = complementaryExams;
    }

    public String getBloodPressure() {
        return bloodPressure;
    }

    public void setBloodPressure(String bloodPressure) {
        this.bloodPressure = bloodPressure;
    }

    public Float getWeight() {
        return weight;
    }

    public void setWeight(Float weight) {
        this.weight = weight;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }
    // relacionamento n:1, muitas consultas podem ser de um paciente
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false) // fk pro paciente
    private Patient patient;
    // relacionamento n:1, muitas consultas podem ser de um prescritor
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescriber_id", nullable = false) // fk pro prescritor
    private Prescriber prescriber;

    // uma consulta pode ter várias prescrições
    @OneToMany(
            mappedBy = "appointment", // o lado Prescription gerencia a relação
            cascade = CascadeType.ALL, // se salvar/deletar a consulta, faz o mesmo com as prescrições
            orphanRemoval = true // remove prescrições que não estão mais na lista
    )
    private List<Prescription> prescriptions = new ArrayList<>();

    public Appointment(String clinicalObservation, LocalDateTime dateTime, String diagnosis, String evolution, Long id, AppointmentModality modality, Patient patient, Prescriber prescriber, List<Prescription> prescriptions, AppointmentStatus status, String therapeuticPlan) {
        this.clinicalObservation = clinicalObservation;
        this.dateTime = dateTime;
        this.diagnosis = diagnosis;
        this.evolution = evolution;
        this.id = id;
        this.modality = modality;
        this.patient = patient;
        this.prescriber = prescriber;
        this.prescriptions = prescriptions;
        this.status = status;
        this.therapeuticPlan = therapeuticPlan;
    }

    // para o agendamento, torna os outros atributos opcionais;
    public Appointment(Patient patient, Prescriber prescriber, AppointmentModality modality, AppointmentStatus status, Long id, LocalDateTime dateTime) {
        this.patient = patient;
        this.prescriber = prescriber;
        this.modality = modality;
        this.status = status;
        this.id = id;
        this.dateTime = dateTime;
    }

    public Appointment() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }

    public AppointmentModality getModality() {
        return modality;
    }

    public void setModality(AppointmentModality modality) {
        this.modality = modality;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public void setStatus(AppointmentStatus status) {
        this.status = status;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public void setDiagnosis(String diagnosis) {
        this.diagnosis = diagnosis;
    }

    public String getClinicalObservation() {
        return clinicalObservation;
    }

    public void setClinicalObservation(String clinicalObservation) {
        this.clinicalObservation = clinicalObservation;
    }

    public String getTherapeuticPlan() {
        return therapeuticPlan;
    }

    public void setTherapeuticPlan(String therapeuticPlan) {
        this.therapeuticPlan = therapeuticPlan;
    }

    public String getEvolution() {
        return evolution;
    }

    public void setEvolution(String evolution) {
        this.evolution = evolution;
    }

    public Prescriber getPrescriber() {
        return prescriber;
    }

    public void setPrescriber(Prescriber prescriber) {
        this.prescriber = prescriber;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public List<Prescription> getPrescriptions() {
        return prescriptions;
    }

    public void setPrescriptions(List<Prescription> prescriptions) {
        this.prescriptions = prescriptions;
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
