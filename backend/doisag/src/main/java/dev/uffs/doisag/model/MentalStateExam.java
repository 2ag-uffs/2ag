package dev.uffs.doisag.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity
@EntityListeners(AuditingEntityListener.class)
public class MentalStateExam {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false) // fk para a consulta
    private Appointment appointment;
    private Integer temporalOrientation;
    private Integer spatialOrientation;
    private Integer registration;
    private Integer attentionAndCalculation;
    private Integer recall;
    private Integer naming;
    private Integer repetition;
    private Integer command;

    // as tres secoes que faltavam. sem elas o teto era 27 e n 30, o que
    // desloca todas as faixas de interpretacao do instrumento (RF26)
    private Integer reading;
    private Integer writing;
    private Integer copying;
    private Integer score;

    public MentalStateExam(Appointment appointment, Integer temporalOrientation, Integer spatialOrientation, Integer registration, Integer attentionAndCalculation, Integer recall, Integer naming, Integer repetition, Integer command, Integer score) {
        this.appointment = appointment;
        this.temporalOrientation = temporalOrientation;
        this.spatialOrientation = spatialOrientation;
        this.registration = registration;
        this.attentionAndCalculation = attentionAndCalculation;
        this.recall = recall;
        this.naming = naming;
        this.repetition = repetition;
        this.command = command;
        this.score = score;
    }
    public MentalStateExam() {
    }

    // sem esse getter o exame saia na resposta da api sem o id
    public Long getId() {
        return id;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public void setAppointment(Appointment appointment) {
        this.appointment = appointment;
    }

    public Integer getTemporalOrientation() {
        return temporalOrientation;
    }

    public void setTemporalOrientation(Integer temporalOrientation) {
        this.temporalOrientation = temporalOrientation;
    }

    public Integer getSpatialOrientation() {
        return spatialOrientation;
    }

    public void setSpatialOrientation(Integer spatialOrientation) {
        this.spatialOrientation = spatialOrientation;
    }

    public Integer getRegistration() {
        return registration;
    }

    public void setRegistration(Integer registration) {
        this.registration = registration;
    }

    public Integer getAttentionAndCalculation() {
        return attentionAndCalculation;
    }

    public void setAttentionAndCalculation(Integer attentionAndCalculation) {
        this.attentionAndCalculation = attentionAndCalculation;
    }

    public Integer getRecall() {
        return recall;
    }

    public void setRecall(Integer recall) {
        this.recall = recall;
    }

    public Integer getNaming() {
        return naming;
    }

    public void setNaming(Integer naming) {
        this.naming = naming;
    }

    public Integer getRepetition() {
        return repetition;
    }

    public void setRepetition(Integer repetition) {
        this.repetition = repetition;
    }

    public Integer getCommand() {
        return command;
    }

    public void setCommand(Integer command) {
        this.command = command;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
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

    public Integer getReading() {
        return reading;
    }

    public void setReading(Integer reading) {
        this.reading = reading;
    }

    public Integer getWriting() {
        return writing;
    }

    public void setWriting(Integer writing) {
        this.writing = writing;
    }

    public Integer getCopying() {
        return copying;
    }

    public void setCopying(Integer copying) {
        this.copying = copying;
    }
}
