package dev.uffs.doisag.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;

import java.time.LocalDate;

@Entity
public class PainLog extends BaseAssessment {
    private int basicActivityInterference;
    private int socialActivityInterference;
    private int sleepInterference;
    private int productivityInterference;
    private int extraMedication;
    @Lob
    @Column(columnDefinition = "TEXT")
    private String observation;

    public PainLog(Long id, LocalDate assessmentDate, Patient patient, int socialActivityInterference, int basicActivityInterference, int sleepInterference, int productivityInterference, int extraMedication, String observation) {
        super(id, assessmentDate, patient);
        this.socialActivityInterference = socialActivityInterference;
        this.basicActivityInterference = basicActivityInterference;
        this.sleepInterference = sleepInterference;
        this.productivityInterference = productivityInterference;
        this.extraMedication = extraMedication;
        this.observation = observation;
    }
        // observacao eh opcional
    public PainLog(Long id, LocalDate assessmentDate, Patient patient, int basicActivityInterference, int extraMedication, int productivityInterference, int sleepInterference, int socialActivityInterference) {
        super(id, assessmentDate, patient);
        this.basicActivityInterference = basicActivityInterference;
        this.extraMedication = extraMedication;
        this.productivityInterference = productivityInterference;
        this.sleepInterference = sleepInterference;
        this.socialActivityInterference = socialActivityInterference;
    }

    public PainLog() {
    }

    public int getSocialActivityInterference() {
        return socialActivityInterference;
    }

    public void setSocialActivityInterference(int socialActivityInterference) {
        this.socialActivityInterference = socialActivityInterference;
    }

    public int getBasicActivityInterference() {
        return basicActivityInterference;
    }

    public void setBasicActivityInterference(int basicActivityInterference) {
        this.basicActivityInterference = basicActivityInterference;
    }

    public int getSleepInterference() {
        return sleepInterference;
    }

    public void setSleepInterference(int sleepInterference) {
        this.sleepInterference = sleepInterference;
    }

    public int getProductivityInterference() {
        return productivityInterference;
    }

    public void setProductivityInterference(int productivityInterference) {
        this.productivityInterference = productivityInterference;
    }

    public int getExtraMedication() {
        return extraMedication;
    }

    public void setExtraMedication(int extraMedication) {
        this.extraMedication = extraMedication;
    }

    public String getObservation() {
        return observation;
    }

    public void setObservation(String observation) {
        this.observation = observation;
    }
}
