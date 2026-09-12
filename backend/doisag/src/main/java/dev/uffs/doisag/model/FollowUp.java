package dev.uffs.doisag.model;

import jakarta.persistence.Entity;
import java.time.LocalDate;

@Entity
public class FollowUp extends BaseAssessment {
    private int morningDrops;
    private int afternoonDrops;
    private String comment;
    private int tremor;
    private int rigiditySpasticity;
    private int nausea;
    private int concentration;
    private int appetite;
    private int socialInteraction;
    private int disposition;
    private int intestinalFunction;
    private int anxiety;
    private int substanceReduction;
    private int pain;
    private int sportsPerformance;
    private int sleep;
    private int dermatologicalDisease;
    private int mood;

    public FollowUp(Long id, LocalDate assessmentDate, Patient patient, int morningDrops, int afternoonDrops, String comment, int tremor, int rigiditySpasticity, int nausea, int concentration, int appetite, int socialInteraction, int disposition, int anxiety, int intestinalFunction, int substanceReduction, int pain, int sportsPerformance, int sleep, int dermatologicalDisease, int mood) {
        super(id, assessmentDate, patient);
        this.morningDrops = morningDrops;
        this.afternoonDrops = afternoonDrops;
        this.comment = comment;
        this.tremor = tremor;
        this.rigiditySpasticity = rigiditySpasticity;
        this.nausea = nausea;
        this.concentration = concentration;
        this.appetite = appetite;
        this.socialInteraction = socialInteraction;
        this.disposition = disposition;
        this.anxiety = anxiety;
        this.intestinalFunction = intestinalFunction;
        this.substanceReduction = substanceReduction;
        this.pain = pain;
        this.sportsPerformance = sportsPerformance;
        this.sleep = sleep;
        this.dermatologicalDisease = dermatologicalDisease;
        this.mood = mood;
    }
    // comentario opcional
    public FollowUp(Long id, LocalDate assessmentDate, Patient patient, int morningDrops, int afternoonDrops, int tremor, int nausea, int rigiditySpasticity, int concentration, int appetite, int socialInteraction, int disposition, int intestinalFunction, int anxiety, int pain, int substanceReduction, int sleep, int sportsPerformance, int dermatologicalDisease, int mood) {
        super(id, assessmentDate, patient);
        this.morningDrops = morningDrops;
        this.afternoonDrops = afternoonDrops;
        this.tremor = tremor;
        this.nausea = nausea;
        this.rigiditySpasticity = rigiditySpasticity;
        this.concentration = concentration;
        this.appetite = appetite;
        this.socialInteraction = socialInteraction;
        this.disposition = disposition;
        this.intestinalFunction = intestinalFunction;
        this.anxiety = anxiety;
        this.pain = pain;
        this.substanceReduction = substanceReduction;
        this.sleep = sleep;
        this.sportsPerformance = sportsPerformance;
        this.dermatologicalDisease = dermatologicalDisease;
        this.mood = mood;
    }
    public FollowUp() {
    }

    public int getMorningDrops() {
        return morningDrops;
    }

    public void setMorningDrops(int morningDrops) {
        this.morningDrops = morningDrops;
    }

    public int getAfternoonDrops() {
        return afternoonDrops;
    }

    public void setAfternoonDrops(int afternoonDrops) {
        this.afternoonDrops = afternoonDrops;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public int getTremor() {
        return tremor;
    }

    public void setTremor(int tremor) {
        this.tremor = tremor;
    }

    public int getRigiditySpasticity() {
        return rigiditySpasticity;
    }

    public void setRigiditySpasticity(int rigiditySpasticity) {
        this.rigiditySpasticity = rigiditySpasticity;
    }

    public int getNausea() {
        return nausea;
    }

    public void setNausea(int nausea) {
        this.nausea = nausea;
    }

    public int getConcentration() {
        return concentration;
    }

    public void setConcentration(int concentration) {
        this.concentration = concentration;
    }

    public int getAppetite() {
        return appetite;
    }

    public void setAppetite(int appetite) {
        this.appetite = appetite;
    }

    public int getDisposition() {
        return disposition;
    }

    public void setDisposition(int disposition) {
        this.disposition = disposition;
    }

    public int getSocialInteraction() {
        return socialInteraction;
    }

    public void setSocialInteraction(int socialInteraction) {
        this.socialInteraction = socialInteraction;
    }

    public int getIntestinalFunction() {
        return intestinalFunction;
    }

    public void setIntestinalFunction(int intestinalFunction) {
        this.intestinalFunction = intestinalFunction;
    }

    public int getAnxiety() {
        return anxiety;
    }

    public void setAnxiety(int anxiety) {
        this.anxiety = anxiety;
    }

    public int getSubstanceReduction() {
        return substanceReduction;
    }

    public void setSubstanceReduction(int substanceReduction) {
        this.substanceReduction = substanceReduction;
    }

    public int getPain() {
        return pain;
    }

    public void setPain(int pain) {
        this.pain = pain;
    }

    public int getSportsPerformance() {
        return sportsPerformance;
    }

    public void setSportsPerformance(int sportsPerformance) {
        this.sportsPerformance = sportsPerformance;
    }

    public int getSleep() {
        return sleep;
    }

    public void setSleep(int sleep) {
        this.sleep = sleep;
    }

    public int getDermatologicalDisease() {
        return dermatologicalDisease;
    }

    public void setDermatologicalDisease(int dermatologicalDisease) {
        this.dermatologicalDisease = dermatologicalDisease;
    }

    public int getMood() {
        return mood;
    }

    public void setMood(int mood) {
        this.mood = mood;
    }
}
