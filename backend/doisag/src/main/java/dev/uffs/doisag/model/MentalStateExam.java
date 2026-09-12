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
    private int temporalOrientation;
    private int spatialOrientation;
    private int registration;
    private int attentionAndCalculation;
    private int recall;
    private int naming;
    private int repetition;
    private int command;
    private int score;

    public MentalStateExam(Appointment appointment, int temporalOrientation, int spatialOrientation, int registration, int attentionAndCalculation, int recall, int naming, int repetition, int command, int score) {
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

    public int getTemporalOrientation() {
        return temporalOrientation;
    }

    public void setTemporalOrientation(int temporalOrientation) {
        this.temporalOrientation = temporalOrientation;
    }

    public int getSpatialOrientation() {
        return spatialOrientation;
    }

    public void setSpatialOrientation(int spatialOrientation) {
        this.spatialOrientation = spatialOrientation;
    }

    public int getRegistration() {
        return registration;
    }

    public void setRegistration(int registration) {
        this.registration = registration;
    }

    public int getAttentionAndCalculation() {
        return attentionAndCalculation;
    }

    public void setAttentionAndCalculation(int attentionAndCalculation) {
        this.attentionAndCalculation = attentionAndCalculation;
    }

    public int getRecall() {
        return recall;
    }

    public void setRecall(int recall) {
        this.recall = recall;
    }

    public int getNaming() {
        return naming;
    }

    public void setNaming(int naming) {
        this.naming = naming;
    }

    public int getRepetition() {
        return repetition;
    }

    public void setRepetition(int repetition) {
        this.repetition = repetition;
    }

    public int getCommand() {
        return command;
    }

    public void setCommand(int command) {
        this.command = command;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }
}
