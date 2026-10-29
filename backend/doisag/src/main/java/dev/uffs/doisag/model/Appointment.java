package dev.uffs.doisag.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.AppointmentType;
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

    // o q a prescritora registra no exame do paciente
    // peso e altura ficam numericos de proposito pq sao os unicos da consulta q da pra acompanhar no tratamento
    @Column(columnDefinition = "TEXT")
    private String physicalExam;

    @Column(columnDefinition = "TEXT")
    private String complementaryExams;

    private String bloodPressure;
    private Float weight;
    private Integer height;

    private Integer durationMinutes = 60;

    // primeira consulta ou retorno. a consulta de antes disso fica sem tipo
    @Enumerated(EnumType.STRING)
    private AppointmentType appointmentType;

    // o q o paciente escreveu ao pedir o horario e nunca vai pros campos clinicos
    @Column(columnDefinition = "TEXT")
    private String patientNote;

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

    public AppointmentType getAppointmentType() {
        return appointmentType;
    }

    public void setAppointmentType(AppointmentType appointmentType) {
        this.appointmentType = appointmentType;
    }

    public String getPatientNote() {
        return patientNote;
    }

    public void setPatientNote(String patientNote) {
        this.patientNote = patientNote;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescriber_id", nullable = false)
    private Prescriber prescriber;

    // sem cascade de remocao de proposito pq prescricao eh dado clinico e so sai por anulacao c/ motivo e autor
    // apagar junto c/ a consulta ou por tirar da lista contraria a guarda de prontuario da lei 13.787/2018
    @OneToMany(
            mappedBy = "appointment",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE}
    )
    private List<Prescription> prescriptions = new ArrayList<>();

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

    // as prescricoes saem pelos dtos e n penduradas na consulta
    @JsonIgnore
    public List<Prescription> getPrescriptions() {
        return prescriptions;
    }

    // qnd o registro nasceu e qnd foi mexido pela ultima vez
    // o spring preenche sozinho e ninguem seta na mao (RF31)
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

    // quando o aviso de lembrete desta consulta foi criado, pra ele n sair duas vezes (RF34)
    private LocalDateTime reminderSentAt;

    public LocalDateTime getReminderSentAt() {
        return reminderSentAt;
    }

    public void setReminderSentAt(LocalDateTime reminderSentAt) {
        this.reminderSentAt = reminderSentAt;
    }
}
