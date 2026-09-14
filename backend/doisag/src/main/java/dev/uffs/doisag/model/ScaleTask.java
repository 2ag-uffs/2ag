package dev.uffs.doisag.model;

import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.enums.ScaleTaskStatus;
import jakarta.persistence.Column;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

// a escala q o prescritor mandou o paciente responder, com o periodo
// q ela cobre
//
// o periodo eh o q faz a pendencia vencer: passou do fim sem resposta,
// a tarefa fica como n respondida e o proximo periodo eh enviado, em
// vez de a mesma pendencia arrastar pra sempre (RF32)
@Entity
@EntityListeners(AuditingEntityListener.class)
public class ScaleTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescriber_id", nullable = false)
    private Prescriber prescriber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScaleType scaleType;

    @Column(nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScaleTaskStatus status = ScaleTaskStatus.PENDENTE;

    private LocalDate answeredAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public Prescriber getPrescriber() {
        return prescriber;
    }

    public void setPrescriber(Prescriber prescriber) {
        this.prescriber = prescriber;
    }

    public ScaleType getScaleType() {
        return scaleType;
    }

    public void setScaleType(ScaleType scaleType) {
        this.scaleType = scaleType;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public ScaleTaskStatus getStatus() {
        return status;
    }

    public void setStatus(ScaleTaskStatus status) {
        this.status = status;
    }

    public LocalDate getAnsweredAt() {
        return answeredAt;
    }

    public void setAnsweredAt(LocalDate answeredAt) {
        this.answeredAt = answeredAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // a tarefa aceita resposta enquanto o periodo dela n acabou
    public boolean coversDay(LocalDate day) {
        return !day.isBefore(periodStart) && !day.isAfter(periodEnd);
    }
}
