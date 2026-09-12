package dev.uffs.doisag.model;

import jakarta.persistence.Entity;

import java.time.LocalDate;

@Entity
public class HamiltonScale extends BaseAssessment {
    private Integer anxiousMood;
    private Integer tension;
    private Integer fears;
    private Integer insomnia;
    private Integer cognition;
    private Integer depressedMood;
    private Integer somaticMotor;
    private Integer somaticSensory;
    private Integer cardiovascularSymptoms;
    private Integer respiratorySymptoms;
    private Integer gastrointestinalSymptoms;
    private Integer genitourinarySymptoms;
    private Integer autonomicSymptoms;
    private Integer hamScore;

    public HamiltonScale(Long id, LocalDate assessmentDate, Patient patient, Integer anxiousMood, Integer tension, Integer fears, Integer insomnia, Integer cognition, Integer depressedMood, Integer somaticMotor, Integer somaticSensory, Integer cardiovascularSymptoms, Integer respiratorySymptoms, Integer gastrointestinalSymptoms, Integer genitourinarySymptoms, Integer autonomicSymptoms, Integer hamScore) {
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

    public Integer getAnxiousMood() {
        return anxiousMood;
    }

    public void setAnxiousMood(Integer anxiousMood) {
        this.anxiousMood = anxiousMood;
    }

    public Integer getTension() {
        return tension;
    }

    public void setTension(Integer tension) {
        this.tension = tension;
    }

    public Integer getFears() {
        return fears;
    }

    public void setFears(Integer fears) {
        this.fears = fears;
    }

    public Integer getInsomnia() {
        return insomnia;
    }

    public void setInsomnia(Integer insomnia) {
        this.insomnia = insomnia;
    }

    public Integer getCognition() {
        return cognition;
    }

    public void setCognition(Integer cognition) {
        this.cognition = cognition;
    }

    public Integer getDepressedMood() {
        return depressedMood;
    }

    public void setDepressedMood(Integer depressedMood) {
        this.depressedMood = depressedMood;
    }

    public Integer getSomaticMotor() {
        return somaticMotor;
    }

    public void setSomaticMotor(Integer somaticMotor) {
        this.somaticMotor = somaticMotor;
    }

    public Integer getSomaticSensory() {
        return somaticSensory;
    }

    public void setSomaticSensory(Integer somaticSensory) {
        this.somaticSensory = somaticSensory;
    }

    public Integer getCardiovascularSymptoms() {
        return cardiovascularSymptoms;
    }

    public void setCardiovascularSymptoms(Integer cardiovascularSymptoms) {
        this.cardiovascularSymptoms = cardiovascularSymptoms;
    }

    public Integer getRespiratorySymptoms() {
        return respiratorySymptoms;
    }

    public void setRespiratorySymptoms(Integer respiratorySymptoms) {
        this.respiratorySymptoms = respiratorySymptoms;
    }

    public Integer getGastrointestinalSymptoms() {
        return gastrointestinalSymptoms;
    }

    public void setGastrointestinalSymptoms(Integer gastrointestinalSymptoms) {
        this.gastrointestinalSymptoms = gastrointestinalSymptoms;
    }

    public Integer getGenitourinarySymptoms() {
        return genitourinarySymptoms;
    }

    public void setGenitourinarySymptoms(Integer genitourinarySymptoms) {
        this.genitourinarySymptoms = genitourinarySymptoms;
    }

    public Integer getAutonomicSymptoms() {
        return autonomicSymptoms;
    }

    public void setAutonomicSymptoms(Integer autonomicSymptoms) {
        this.autonomicSymptoms = autonomicSymptoms;
    }

    public Integer getHamScore() {
        return hamScore;
    }

    public void setHamScore(Integer hamScore) {
        this.hamScore = hamScore;
    }
}
