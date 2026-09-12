package dev.uffs.doisag.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import java.time.LocalDate;

@Entity
public class FollowUp extends BaseAssessment {
    private Integer morningDrops;
    private Integer afternoonDrops;
    @Column(columnDefinition = "TEXT")
    private String comment;
    private Integer tremor;
    private Integer rigiditySpasticity;
    private Integer nausea;
    private Integer concentration;
    private Integer appetite;
    private Integer socialInteraction;
    private Integer disposition;
    private Integer intestinalFunction;
    private Integer anxiety;
    private Integer substanceReduction;
    private Integer pain;
    private Integer sportsPerformance;
    private Integer sleep;
    private Integer dermatologicalDisease;
    private Integer mood;

    public FollowUp(Long id, LocalDate assessmentDate, Patient patient, Integer morningDrops, Integer afternoonDrops, String comment, Integer tremor, Integer rigiditySpasticity, Integer nausea, Integer concentration, Integer appetite, Integer socialInteraction, Integer disposition, Integer anxiety, Integer intestinalFunction, Integer substanceReduction, Integer pain, Integer sportsPerformance, Integer sleep, Integer dermatologicalDisease, Integer mood) {
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
    public FollowUp(Long id, LocalDate assessmentDate, Patient patient, Integer morningDrops, Integer afternoonDrops, Integer tremor, Integer nausea, Integer rigiditySpasticity, Integer concentration, Integer appetite, Integer socialInteraction, Integer disposition, Integer intestinalFunction, Integer anxiety, Integer pain, Integer substanceReduction, Integer sleep, Integer sportsPerformance, Integer dermatologicalDisease, Integer mood) {
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

    public Integer getMorningDrops() {
        return morningDrops;
    }

    public void setMorningDrops(Integer morningDrops) {
        this.morningDrops = morningDrops;
    }

    public Integer getAfternoonDrops() {
        return afternoonDrops;
    }

    public void setAfternoonDrops(Integer afternoonDrops) {
        this.afternoonDrops = afternoonDrops;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Integer getTremor() {
        return tremor;
    }

    public void setTremor(Integer tremor) {
        this.tremor = tremor;
    }

    public Integer getRigiditySpasticity() {
        return rigiditySpasticity;
    }

    public void setRigiditySpasticity(Integer rigiditySpasticity) {
        this.rigiditySpasticity = rigiditySpasticity;
    }

    public Integer getNausea() {
        return nausea;
    }

    public void setNausea(Integer nausea) {
        this.nausea = nausea;
    }

    public Integer getConcentration() {
        return concentration;
    }

    public void setConcentration(Integer concentration) {
        this.concentration = concentration;
    }

    public Integer getAppetite() {
        return appetite;
    }

    public void setAppetite(Integer appetite) {
        this.appetite = appetite;
    }

    public Integer getDisposition() {
        return disposition;
    }

    public void setDisposition(Integer disposition) {
        this.disposition = disposition;
    }

    public Integer getSocialInteraction() {
        return socialInteraction;
    }

    public void setSocialInteraction(Integer socialInteraction) {
        this.socialInteraction = socialInteraction;
    }

    public Integer getIntestinalFunction() {
        return intestinalFunction;
    }

    public void setIntestinalFunction(Integer intestinalFunction) {
        this.intestinalFunction = intestinalFunction;
    }

    public Integer getAnxiety() {
        return anxiety;
    }

    public void setAnxiety(Integer anxiety) {
        this.anxiety = anxiety;
    }

    public Integer getSubstanceReduction() {
        return substanceReduction;
    }

    public void setSubstanceReduction(Integer substanceReduction) {
        this.substanceReduction = substanceReduction;
    }

    public Integer getPain() {
        return pain;
    }

    public void setPain(Integer pain) {
        this.pain = pain;
    }

    public Integer getSportsPerformance() {
        return sportsPerformance;
    }

    public void setSportsPerformance(Integer sportsPerformance) {
        this.sportsPerformance = sportsPerformance;
    }

    public Integer getSleep() {
        return sleep;
    }

    public void setSleep(Integer sleep) {
        this.sleep = sleep;
    }

    public Integer getDermatologicalDisease() {
        return dermatologicalDisease;
    }

    public void setDermatologicalDisease(Integer dermatologicalDisease) {
        this.dermatologicalDisease = dermatologicalDisease;
    }

    public Integer getMood() {
        return mood;
    }

    public void setMood(Integer mood) {
        this.mood = mood;
    }
}
