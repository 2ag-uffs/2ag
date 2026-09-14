package dev.uffs.doisag.model;

import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.infra.ScaleAnswersConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

// uma escala respondida, seja qual for a escala
//
// as respostas ficam como par de item e valor, e o escore e a faixa
// sao gravados junto pra lista n ter q recalcular tudo toda vez (RF08)
@Entity
@EntityListeners(AuditingEntityListener.class)
public class ScaleResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    // quem aplicou, quando quem responde n eh o paciente. eh o caso do
    // MEEM, q o prescritor aplica na consulta (RN09)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescriber_id")
    private Prescriber prescriber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private ScaleTask task;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScaleType scaleType;

    // o periodo q a resposta cobre. no diario os dois sao o mesmo dia
    @Column(nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false)
    private LocalDate periodEnd;

    @Convert(converter = ScaleAnswersConverter.class)
    @Column(columnDefinition = "TEXT", nullable = false)
    private Map<String, Object> answers = new LinkedHashMap<>();

    private Integer score;

    private String scoreBand;

    // depois q o prescritor confere, o paciente n corrige mais e a
    // correcao passa a ser anulacao com motivo
    private LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private Users reviewedBy;

    @Embedded
    private Annulment annulment;

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

    public Appointment getAppointment() {
        return appointment;
    }

    public void setAppointment(Appointment appointment) {
        this.appointment = appointment;
    }

    public ScaleTask getTask() {
        return task;
    }

    public void setTask(ScaleTask task) {
        this.task = task;
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

    public Map<String, Object> getAnswers() {
        return answers;
    }

    public void setAnswers(Map<String, Object> answers) {
        this.answers = answers;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public String getScoreBand() {
        return scoreBand;
    }

    public void setScoreBand(String scoreBand) {
        this.scoreBand = scoreBand;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public Users getReviewedBy() {
        return reviewedBy;
    }

    public boolean isReviewed() {
        return reviewedAt != null;
    }

    public void markReviewed(Users prescriberUser) {
        this.reviewedAt = LocalDateTime.now();
        this.reviewedBy = prescriberUser;
    }

    public Annulment getAnnulment() {
        return annulment;
    }

    public void setAnnulment(Annulment annulment) {
        this.annulment = annulment;
    }

    public boolean isAnnulled() {
        return annulment != null && annulment.getAnnulledAt() != null;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // quanto vale um item nesta resposta, pro grafico de evolucao
    // item em branco devolve null e vira lacuna, nunca zero (RN10)
    public Integer valueOf(String itemKey) {
        Object value = answers.get(itemKey);
        return value instanceof Number number ? number.intValue() : null;
    }
}
