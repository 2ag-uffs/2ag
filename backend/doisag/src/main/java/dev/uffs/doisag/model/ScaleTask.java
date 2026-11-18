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
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// a escala q o prescritor mandou o paciente responder, com o periodo
// q ela cobre
//
// o periodo eh o q faz a pendencia vencer: passou do fim sem resposta,
// a tarefa fica como n respondida e o proximo periodo eh enviado, em
// vez de a mesma pendencia arrastar pra sempre (RF32)
//
// grava so as colunas q mudaram: o lembrete do job e a resposta do paciente mexem em colunas diferentes
// da mesma tarefa, e com a linha inteira o q gravou por ultimo desfazia o outro
@Entity
@DynamicUpdate
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

    // veio do acompanhamento automatico e n de um envio avulso
    @Column(nullable = false)
    private boolean fromProtocol = false;

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

    // o primeiro e o ultimo dia contam
    public long totalDays() {
        return ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
    }

    // a tarefa ja existia qnd o paciente foi arquivado, entao o arquivado n eh mais cobrado dela
    // a enviada a mao dps do arquivamento eh mais nova q ele e segue a regra de sempre
    // so vale enquanto ele segue arquivado: a q o arquivamento fechou fica marcada em stoppedOn
    public boolean existedBeforeArchive() {
        LocalDateTime archivedAt = patient.getArchivedAt();
        return archivedAt != null && !createdAt.isAfter(archivedAt);
    }

    // quando o aviso de lembrete desta tarefa foi criado, pra ele n sair duas vezes (RF34)
    private LocalDateTime reminderSentAt;

    public boolean isFromProtocol() {
        return fromProtocol;
    }

    public void setFromProtocol(boolean fromProtocol) {
        this.fromProtocol = fromProtocol;
    }

    public LocalDateTime getReminderSentAt() {
        return reminderSentAt;
    }

    public void setReminderSentAt(LocalDateTime reminderSentAt) {
        this.reminderSentAt = reminderSentAt;
    }

    // no diario, o ultimo dia em branco da sequencia q ja foi cobrada, pra mesma sequencia n cobrar de novo
    // (decisao de 14/11/2026, revisao das regras R6)
    private LocalDate blankDaysRemindedUntil;

    public LocalDate getBlankDaysRemindedUntil() {
        return blankDaysRemindedUntil;
    }

    public void setBlankDaysRemindedUntil(LocalDate blankDaysRemindedUntil) {
        this.blankDaysRemindedUntil = blankDaysRemindedUntil;
    }

    // o dia em q o prescritor interrompeu a tarefa, arquivando o paciente ou tirando a escala do acompanhamento
    // fica na tarefa pq reativar o paciente, ou encerrar o acompanhamento novo, n desfaz a interrupcao
    private LocalDate stoppedOn;

    public LocalDate getStoppedOn() {
        return stoppedOn;
    }

    public void setStoppedOn(LocalDate stoppedOn) {
        this.stoppedOn = stoppedOn;
    }

    public boolean isStoppedByPrescriber() {
        return stoppedOn != null;
    }
}
