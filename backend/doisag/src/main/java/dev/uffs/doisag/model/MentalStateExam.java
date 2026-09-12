package dev.uffs.doisag.model;

import jakarta.persistence.*;

@Entity
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
}
