package dev.uffs.doisag.model;

import jakarta.persistence.Entity;

import java.time.LocalDate;

@Entity
public class HamiltonScale extends BaseAssessment {
    private int anxiousMood;
    private int tension;
    private int fears;
    private int insomnia;
    private int cognition;
    private int depressedMood;
    private int somaticMotor;
    private int somaticSensory;
    private int cardiovascularSymptoms;
    private int respiratorySymptoms;
    private int gastrointestinalSymptoms;
    private int genitourinarySymptoms;
    private int autonomicSymptoms;
    private int hamScore;

    public HamiltonScale(Long id, LocalDate assessmentDate, Patient patient, int anxiousMood, int tension, int fears, int insomnia, int cognition, int depressedMood, int somaticMotor, int somaticSensory, int cardiovascularSymptoms, int respiratorySymptoms, int gastrointestinalSymptoms, int genitourinarySymptoms, int autonomicSymptoms, int hamScore) {
        super(id, assessmentDate, patient);
        this.anxiousMood = anxiousMood;
        this.tension = tension;
        this.fears = fears;
        this.insomnia = insomnia;
        this.cognition = cognition;
        this.depressedMood = depressedMood;
        this.somaticMotor = somaticMotor;
        this.somaticSensory = somaticSensory;
        this.cardiovascularSymptoms = cardiovascularSymptoms;
        this.respiratorySymptoms = respiratorySymptoms;
        this.gastrointestinalSymptoms = gastrointestinalSymptoms;
        this.genitourinarySymptoms = genitourinarySymptoms;
        this.autonomicSymptoms = autonomicSymptoms;
        this.hamScore = hamScore;
    }
    public HamiltonScale() {
    }

    public int getAnxiousMood() {
        return anxiousMood;
    }

    public void setAnxiousMood(int anxiousMood) {
        this.anxiousMood = anxiousMood;
    }

    public int getTension() {
        return tension;
    }

    public void setTension(int tension) {
        this.tension = tension;
    }

    public int getFears() {
        return fears;
    }

    public void setFears(int fears) {
        this.fears = fears;
    }

    public int getInsomnia() {
        return insomnia;
    }

    public void setInsomnia(int insomnia) {
        this.insomnia = insomnia;
    }

    public int getCognition() {
        return cognition;
    }

    public void setCognition(int cognition) {
        this.cognition = cognition;
    }

    public int getDepressedMood() {
        return depressedMood;
    }

    public void setDepressedMood(int depressedMood) {
        this.depressedMood = depressedMood;
    }

    public int getSomaticMotor() {
        return somaticMotor;
    }

    public void setSomaticMotor(int somaticMotor) {
        this.somaticMotor = somaticMotor;
    }

    public int getSomaticSensory() {
        return somaticSensory;
    }

    public void setSomaticSensory(int somaticSensory) {
        this.somaticSensory = somaticSensory;
    }

    public int getCardiovascularSymptoms() {
        return cardiovascularSymptoms;
    }

    public void setCardiovascularSymptoms(int cardiovascularSymptoms) {
        this.cardiovascularSymptoms = cardiovascularSymptoms;
    }

    public int getRespiratorySymptoms() {
        return respiratorySymptoms;
    }

    public void setRespiratorySymptoms(int respiratorySymptoms) {
        this.respiratorySymptoms = respiratorySymptoms;
    }

    public int getGastrointestinalSymptoms() {
        return gastrointestinalSymptoms;
    }

    public void setGastrointestinalSymptoms(int gastrointestinalSymptoms) {
        this.gastrointestinalSymptoms = gastrointestinalSymptoms;
    }

    public int getGenitourinarySymptoms() {
        return genitourinarySymptoms;
    }

    public void setGenitourinarySymptoms(int genitourinarySymptoms) {
        this.genitourinarySymptoms = genitourinarySymptoms;
    }

    public int getAutonomicSymptoms() {
        return autonomicSymptoms;
    }

    public void setAutonomicSymptoms(int autonomicSymptoms) {
        this.autonomicSymptoms = autonomicSymptoms;
    }

    public int getHamScore() {
        return hamScore;
    }

    public void setHamScore(int hamScore) {
        this.hamScore = hamScore;
    }
}
