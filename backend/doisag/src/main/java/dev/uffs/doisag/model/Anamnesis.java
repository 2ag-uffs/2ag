package dev.uffs.doisag.model;

import jakarta.persistence.Entity;

import java.time.LocalDate;

@Entity
public class Anamnesis extends BaseAssessment {
    private String reasonForVisit;
    private String profession;
    private String diet;
    private String anxiety;
    private String observation;
    private String pain;
    private String adverseReaction;
    private String previousDiagnosis;
    private String smokingHabits;
    private String expectations;
    private String currentMedication;
    private String treatmentAwareness;
    private String geneticCondition;
    private String previousTreatment;
    private String familyHistory;
    private String height;
    private String weight;
    private String alcoholConsumption;
    private String sleepHabits;
    private String substanceUse;
    private String physicalActivity;

    public Anamnesis(Long id, LocalDate assessmentDate, Patient patient, String reasonForVisit, String profession, String diet, String anxiety, String observation, String adverseReaction, String expectations, String currentMedication, String geneticCondition, String previousTreatment, String height, String weight, String sleepHabits, String alcoholConsumption, String familyHistory, String treatmentAwareness, String smokingHabits, String previousDiagnosis, String pain, String substanceUse, String physicalActivity) {
        super(id, assessmentDate, patient);
        this.reasonForVisit = reasonForVisit;
        this.profession = profession;
        this.diet = diet;
        this.anxiety = anxiety;
        this.observation = observation;
        this.adverseReaction = adverseReaction;
        this.expectations = expectations;
        this.currentMedication = currentMedication;
        this.geneticCondition = geneticCondition;
        this.previousTreatment = previousTreatment;
        this.height = height;
        this.weight = weight;
        this.sleepHabits = sleepHabits;
        this.alcoholConsumption = alcoholConsumption;
        this.familyHistory = familyHistory;
        this.treatmentAwareness = treatmentAwareness;
        this.smokingHabits = smokingHabits;
        this.previousDiagnosis = previousDiagnosis;
        this.pain = pain;
        this.substanceUse = substanceUse;
        this.physicalActivity = physicalActivity;
    }
    // torna opcional alguns atributos, posso pedir que deixe o campo em branco caso não se aplique
    public Anamnesis(Long id, LocalDate assessmentDate, Patient patient, String diet, String profession, String reasonForVisit, String treatmentAwareness, String height, String weight, String sleepHabits, String physicalActivity) {
        super(id, assessmentDate, patient);
        this.diet = diet;
        this.profession = profession;
        this.reasonForVisit = reasonForVisit;
        this.treatmentAwareness = treatmentAwareness;
        this.height = height;
        this.weight = weight;
        this.sleepHabits = sleepHabits;
        this.physicalActivity = physicalActivity;
    }
    public Anamnesis() {
    }

    public String getReasonForVisit() {
        return reasonForVisit;
    }

    public void setReasonForVisit(String reasonForVisit) {
        this.reasonForVisit = reasonForVisit;
    }

    public String getProfession() {
        return profession;
    }

    public void setProfession(String profession) {
        this.profession = profession;
    }

    public String getDiet() {
        return diet;
    }

    public void setDiet(String diet) {
        this.diet = diet;
    }

    public String getAnxiety() {
        return anxiety;
    }

    public void setAnxiety(String anxiety) {
        this.anxiety = anxiety;
    }

    public String getObservation() {
        return observation;
    }

    public void setObservation(String observation) {
        this.observation = observation;
    }

    public String getPain() {
        return pain;
    }

    public void setPain(String pain) {
        this.pain = pain;
    }

    public String getAdverseReaction() {
        return adverseReaction;
    }

    public void setAdverseReaction(String adverseReaction) {
        this.adverseReaction = adverseReaction;
    }

    public String getPreviousDiagnosis() {
        return previousDiagnosis;
    }

    public void setPreviousDiagnosis(String previousDiagnosis) {
        this.previousDiagnosis = previousDiagnosis;
    }

    public String getSmokingHabits() {
        return smokingHabits;
    }

    public void setSmokingHabits(String smokingHabits) {
        this.smokingHabits = smokingHabits;
    }

    public String getExpectations() {
        return expectations;
    }

    public void setExpectations(String expectations) {
        this.expectations = expectations;
    }

    public String getCurrentMedication() {
        return currentMedication;
    }

    public void setCurrentMedication(String currentMedication) {
        this.currentMedication = currentMedication;
    }

    public String getTreatmentAwareness() {
        return treatmentAwareness;
    }

    public void setTreatmentAwareness(String treatmentAwareness) {
        this.treatmentAwareness = treatmentAwareness;
    }

    public String getGeneticCondition() {
        return geneticCondition;
    }

    public void setGeneticCondition(String geneticCondition) {
        this.geneticCondition = geneticCondition;
    }

    public String getPreviousTreatment() {
        return previousTreatment;
    }

    public void setPreviousTreatment(String previousTreatment) {
        this.previousTreatment = previousTreatment;
    }

    public String getFamilyHistory() {
        return familyHistory;
    }

    public void setFamilyHistory(String familyHistory) {
        this.familyHistory = familyHistory;
    }

    public String getHeight() {
        return height;
    }

    public void setHeight(String height) {
        this.height = height;
    }

    public String getWeight() {
        return weight;
    }

    public void setWeight(String weight) {
        this.weight = weight;
    }

    public String getAlcoholConsumption() {
        return alcoholConsumption;
    }

    public void setAlcoholConsumption(String alcoholConsumption) {
        this.alcoholConsumption = alcoholConsumption;
    }

    public String getSleepHabits() {
        return sleepHabits;
    }

    public void setSleepHabits(String sleepHabits) {
        this.sleepHabits = sleepHabits;
    }

    public String getSubstanceUse() {
        return substanceUse;
    }

    public void setSubstanceUse(String substanceUse) {
        this.substanceUse = substanceUse;
    }

    public String getPhysicalActivity() {
        return physicalActivity;
    }

    public void setPhysicalActivity(String physicalActivity) {
        this.physicalActivity = physicalActivity;
    }
}
