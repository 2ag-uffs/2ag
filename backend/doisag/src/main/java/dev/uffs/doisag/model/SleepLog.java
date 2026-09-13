package dev.uffs.doisag.model;

import dev.uffs.doisag.enums.TrackableAttribute;
import jakarta.persistence.Entity;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
public class SleepLog extends BaseAssessment {
    private LocalTime bedTime;
    private LocalTime wakeUpTime;
    private Float timeInBed;
    private Integer timeToFallAsleep;
    private Integer timesWokenUp;
    private Integer totalTimeAwake;
    private Float totalSleepTime;
    private Float totalAwakeSleep;
    private Boolean isCommonDay;
    private Integer fatigue;
    private Integer stress;
    private Integer daytimeSleepiness;
    private Integer inattention;
    private Integer irritability;
    private Integer pain;
    private Integer healthPerception;
    private Float physicalActivityTime;
    private Float timeAwayFromHome;
    private Boolean usedSleepMedication;
    private Integer alcoholConsumption;
    private Integer napsTime;
    private Integer coffeeConsumption;
    private Integer nighttimeSmoking;
    private Integer totalTimeAwakeDuringNight;


    public SleepLog(Integer alcoholConsumption, LocalTime bedTime, Integer coffeeConsumption, Integer daytimeSleepiness, Integer fatigue, Integer healthPerception, Integer inattention, Integer irritability, Boolean isCommonDay, Integer napsTime, Integer nighttimeSmoking, Integer pain, Float physicalActivityTime, Integer stress, Float timeAwayFromHome, Float timeInBed, Integer timesWokenUp, Integer timeToFallAsleep, Float totalAwakeSleep, Float totalSleepTime, Integer totalTimeAwake, Integer totalTimeAwakeDuringNight, Boolean usedSleepMedication, LocalTime wakeUpTime) {
        this.alcoholConsumption = alcoholConsumption;
        this.bedTime = bedTime;
        this.coffeeConsumption = coffeeConsumption;
        this.daytimeSleepiness = daytimeSleepiness;
        this.fatigue = fatigue;
        this.healthPerception = healthPerception;
        this.inattention = inattention;
        this.irritability = irritability;
        this.isCommonDay = isCommonDay;
        this.napsTime = napsTime;
        this.nighttimeSmoking = nighttimeSmoking;
        this.pain = pain;
        this.physicalActivityTime = physicalActivityTime;
        this.stress = stress;
        this.timeAwayFromHome = timeAwayFromHome;
        this.timeInBed = timeInBed;
        this.timesWokenUp = timesWokenUp;
        this.timeToFallAsleep = timeToFallAsleep;
        this.totalAwakeSleep = totalAwakeSleep;
        this.totalSleepTime = totalSleepTime;
        this.totalTimeAwake = totalTimeAwake;
        this.totalTimeAwakeDuringNight = totalTimeAwakeDuringNight;
        this.usedSleepMedication = usedSleepMedication;
        this.wakeUpTime = wakeUpTime;
    }


    public SleepLog(){

 }

    public Integer getTotalTimeAwake() {
        return totalTimeAwake;
    }

    public void setTotalTimeAwake(Integer totalTimeAwake) {
        this.totalTimeAwake = totalTimeAwake;
    }

    public LocalTime getBedTime() {
        return bedTime;
    }

    public void setBedTime(LocalTime bedTime) {
        this.bedTime = bedTime;
    }

    public LocalTime getWakeUpTime() {
        return wakeUpTime;
    }

    public void setWakeUpTime(LocalTime wakeUpTime) {
        this.wakeUpTime = wakeUpTime;
    }

    public Float getTimeInBed() {
        return timeInBed;
    }

    public void setTimeInBed(Float timeInBed) {
        this.timeInBed = timeInBed;
    }

    public Integer getTimeToFallAsleep() {
        return timeToFallAsleep;
    }

    public void setTimeToFallAsleep(Integer timeToFallAsleep) {
        this.timeToFallAsleep = timeToFallAsleep;
    }

    public Integer getTimesWokenUp() {
        return timesWokenUp;
    }

    public void setTimesWokenUp(Integer timesWokenUp) {
        this.timesWokenUp = timesWokenUp;
    }

    public Float getTotalSleepTime() {
        return totalSleepTime;
    }

    public void setTotalSleepTime(Float totalSleepTime) {
        this.totalSleepTime = totalSleepTime;
    }

    public Boolean getCommonDay() {
        return isCommonDay;
    }

    public void setCommonDay(Boolean commonDay) {
        isCommonDay = commonDay;
    }

    public Integer getFatigue() {
        return fatigue;
    }

    public void setFatigue(Integer fatigue) {
        this.fatigue = fatigue;
    }

    public Integer getStress() {
        return stress;
    }

    public void setStress(Integer stress) {
        this.stress = stress;
    }

    public Integer getDaytimeSleepiness() {
        return daytimeSleepiness;
    }

    public void setDaytimeSleepiness(Integer daytimeSleepiness) {
        this.daytimeSleepiness = daytimeSleepiness;
    }

    public Integer getInattention() {
        return inattention;
    }

    public void setInattention(Integer inattention) {
        this.inattention = inattention;
    }

    public Integer getIrritability() {
        return irritability;
    }

    public void setIrritability(Integer irritability) {
        this.irritability = irritability;
    }

    public Integer getPain() {
        return pain;
    }

    public void setPain(Integer pain) {
        this.pain = pain;
    }

    public Integer getHealthPerception() {
        return healthPerception;
    }

    public void setHealthPerception(Integer healthPerception) {
        this.healthPerception = healthPerception;
    }

    public Float getPhysicalActivityTime() {
        return physicalActivityTime;
    }

    public void setPhysicalActivityTime(Float physicalActivityTime) {
        this.physicalActivityTime = physicalActivityTime;
    }

    public Float getTimeAwayFromHome() {
        return timeAwayFromHome;
    }

    public void setTimeAwayFromHome(Float timeAwayFromHome) {
        this.timeAwayFromHome = timeAwayFromHome;
    }

    public Boolean getUsedSleepMedication() {
        return usedSleepMedication;
    }

    public void setUsedSleepMedication(Boolean usedSleepMedication) {
        this.usedSleepMedication = usedSleepMedication;
    }

    public Integer getAlcoholConsumption() {
        return alcoholConsumption;
    }

    public void setAlcoholConsumption(Integer alcoholConsumption) {
        this.alcoholConsumption = alcoholConsumption;
    }

    public Integer getNapsTime() {
        return napsTime;
    }

    public void setNapsTime(Integer napsTime) {
        this.napsTime = napsTime;
    }

    public Integer getCoffeeConsumption() {
        return coffeeConsumption;
    }

    public void setCoffeeConsumption(Integer coffeeConsumption) {
        this.coffeeConsumption = coffeeConsumption;
    }

    public Integer getNighttimeSmoking() {
        return nighttimeSmoking;
    }

    public void setNighttimeSmoking(Integer nighttimeSmoking) {
        this.nighttimeSmoking = nighttimeSmoking;
    }

    public Integer getTotalTimeAwakeDuringNight() {
        return totalTimeAwakeDuringNight;
    }

    public Float getTotalAwakeSleep() {
        return totalAwakeSleep;
    }

    public void setTotalAwakeSleep(Float totalAwakeSleep) {
        this.totalAwakeSleep = totalAwakeSleep;
    }

    public void setTotalTimeAwakeDuringNight(Integer totalTimeAwakeDuringNight) {
        this.totalTimeAwakeDuringNight = totalTimeAwakeDuringNight;
    }

    @Override
    public Integer trackedValue(TrackableAttribute attribute) {
        return switch (attribute) {
            case CANSACO -> fatigue;
            case ESTRESSE -> stress;
            case SONOLENCIA_DIURNA -> daytimeSleepiness;
            case IRRITABILIDADE -> irritability;
            case DESPERTARES -> timesWokenUp;
            case TEMPO_ATE_DORMIR -> timeToFallAsleep;
            default -> null;
        };
    }
}
