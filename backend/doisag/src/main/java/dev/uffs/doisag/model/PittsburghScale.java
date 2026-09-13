package dev.uffs.doisag.model;

import dev.uffs.doisag.enums.TrackableAttribute;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
public class PittsburghScale extends BaseAssessment {
    private LocalTime usualBedTime;
    private Integer minutesToFallAsleep;
    private LocalTime usualWakeUpTime;
    private Float actualSleepHours;
    private Integer freqCannotFallAsleep;
    private Integer freqWakesUpMiddleNight;
    private Integer freqWakeUpForBathroom;
    private Integer freqCannotBreathe;
    private Integer freqCoughOrSnore;
    private Integer freqFeelCold;
    private Integer freqFeelHot;
    private Integer freqHaveBadDreams;
    private Integer freqHavePain;
    @Column(columnDefinition = "TEXT")
    private String otherReasonToTroubleSleep;

    // com que frequencia esse outro motivo atrapalhou. eh o item 5J do
    // psqi: o campo de texto existia, mas sem a frequencia o componente
    // de disturbios do sono fica com 8 itens em vez de 9 (RF23)
    private Integer freqOtherReason;
    private Integer sleepQualityRating;
    private Integer freqUseSleepMedication;
    private Integer freqTroubleStayingAwake;
    private Integer troubleWithEnthusiasm;
    private Integer roomPartner;
    private Integer psqiScore;

    public PittsburghScale(Long id, LocalDate assessmentDate, Patient patient, LocalTime usualBedTime, Integer minutesToFallAsleep, LocalTime usualWakeUpTime, Float actualSleepHours, Integer freqWakesUpMiddleNight, Integer freqCannotFallAsleep, Integer freqWakeUpForBathroom, Integer freqCannotBreathe, Integer freqCoughOrSnore, Integer freqFeelCold, Integer freqFeelHot, Integer freqHaveBadDreams, Integer freqHavePain, String otherReasonToTroubleSleep, Integer sleepQualityRating, Integer freqUseSleepMedication, Integer troubleWithEnthusiasm, Integer freqTroubleStayingAwake, Integer roomPartner, Integer psqiScore) {
        super(id, assessmentDate, patient);
        this.usualBedTime = usualBedTime;
        this.minutesToFallAsleep = minutesToFallAsleep;
        this.usualWakeUpTime = usualWakeUpTime;
        this.actualSleepHours = actualSleepHours;
        this.freqWakesUpMiddleNight = freqWakesUpMiddleNight;
        this.freqCannotFallAsleep = freqCannotFallAsleep;
        this.freqWakeUpForBathroom = freqWakeUpForBathroom;
        this.freqCannotBreathe = freqCannotBreathe;
        this.freqCoughOrSnore = freqCoughOrSnore;
        this.freqFeelCold = freqFeelCold;
        this.freqFeelHot = freqFeelHot;
        this.freqHaveBadDreams = freqHaveBadDreams;
        this.freqHavePain = freqHavePain;
        this.otherReasonToTroubleSleep = otherReasonToTroubleSleep;
        this.sleepQualityRating = sleepQualityRating;
        this.freqUseSleepMedication = freqUseSleepMedication;
        this.troubleWithEnthusiasm = troubleWithEnthusiasm;
        this.freqTroubleStayingAwake = freqTroubleStayingAwake;
        this.roomPartner = roomPartner;
        this.psqiScore = psqiScore;
    }

    public PittsburghScale() {
    }

    public LocalTime getUsualBedTime() {
        return usualBedTime;
    }

    public void setUsualBedTime(LocalTime usualBedTime) {
        this.usualBedTime = usualBedTime;
    }

    public Integer getMinutesToFallAsleep() {
        return minutesToFallAsleep;
    }

    public void setMinutesToFallAsleep(Integer minutesToFallAsleep) {
        this.minutesToFallAsleep = minutesToFallAsleep;
    }

    public LocalTime getUsualWakeUpTime() {
        return usualWakeUpTime;
    }

    public void setUsualWakeUpTime(LocalTime usualWakeUpTime) {
        this.usualWakeUpTime = usualWakeUpTime;
    }

    public Float getActualSleepHours() {
        return actualSleepHours;
    }

    public void setActualSleepHours(Float actualSleepHours) {
        this.actualSleepHours = actualSleepHours;
    }

    public Integer getFreqCannotFallAsleep() {
        return freqCannotFallAsleep;
    }

    public void setFreqCannotFallAsleep(Integer freqCannotFallAsleep) {
        this.freqCannotFallAsleep = freqCannotFallAsleep;
    }

    public Integer getFreqWakesUpMiddleNight() {
        return freqWakesUpMiddleNight;
    }

    public void setFreqWakesUpMiddleNight(Integer freqWakesUpMiddleNight) {
        this.freqWakesUpMiddleNight = freqWakesUpMiddleNight;
    }

    public Integer getFreqWakeUpForBathroom() {
        return freqWakeUpForBathroom;
    }

    public void setFreqWakeUpForBathroom(Integer freqWakeUpForBathroom) {
        this.freqWakeUpForBathroom = freqWakeUpForBathroom;
    }

    public Integer getFreqCannotBreathe() {
        return freqCannotBreathe;
    }

    public void setFreqCannotBreathe(Integer freqCannotBreathe) {
        this.freqCannotBreathe = freqCannotBreathe;
    }

    public Integer getFreqCoughOrSnore() {
        return freqCoughOrSnore;
    }

    public void setFreqCoughOrSnore(Integer freqCoughOrSnore) {
        this.freqCoughOrSnore = freqCoughOrSnore;
    }

    public Integer getFreqFeelCold() {
        return freqFeelCold;
    }

    public void setFreqFeelCold(Integer freqFeelCold) {
        this.freqFeelCold = freqFeelCold;
    }

    public Integer getFreqFeelHot() {
        return freqFeelHot;
    }

    public void setFreqFeelHot(Integer freqFeelHot) {
        this.freqFeelHot = freqFeelHot;
    }

    public Integer getFreqHavePain() {
        return freqHavePain;
    }

    public void setFreqHavePain(Integer freqHavePain) {
        this.freqHavePain = freqHavePain;
    }

    public Integer getFreqHaveBadDreams() {
        return freqHaveBadDreams;
    }

    public void setFreqHaveBadDreams(Integer freqHaveBadDreams) {
        this.freqHaveBadDreams = freqHaveBadDreams;
    }

    public String getOtherReasonToTroubleSleep() {
        return otherReasonToTroubleSleep;
    }

    public void setOtherReasonToTroubleSleep(String otherReasonToTroubleSleep) {
        this.otherReasonToTroubleSleep = otherReasonToTroubleSleep;
    }

    public Integer getSleepQualityRating() {
        return sleepQualityRating;
    }

    public void setSleepQualityRating(Integer sleepQualityRating) {
        this.sleepQualityRating = sleepQualityRating;
    }

    public Integer getFreqUseSleepMedication() {
        return freqUseSleepMedication;
    }

    public void setFreqUseSleepMedication(Integer freqUseSleepMedication) {
        this.freqUseSleepMedication = freqUseSleepMedication;
    }

    public Integer getFreqTroubleStayingAwake() {
        return freqTroubleStayingAwake;
    }

    public void setFreqTroubleStayingAwake(Integer freqTroubleStayingAwake) {
        this.freqTroubleStayingAwake = freqTroubleStayingAwake;
    }

    public Integer getTroubleWithEnthusiasm() {
        return troubleWithEnthusiasm;
    }

    public void setTroubleWithEnthusiasm(Integer troubleWithEnthusiasm) {
        this.troubleWithEnthusiasm = troubleWithEnthusiasm;
    }

    public Integer getRoomPartner() {
        return roomPartner;
    }

    public void setRoomPartner(Integer roomPartner) {
        this.roomPartner = roomPartner;
    }

    public Integer getPsqiScore() {
        return psqiScore;
    }

    public void setPsqiScore(Integer psqiScore) {
        this.psqiScore = psqiScore;
    }


    @Override
    public Integer trackedValue(TrackableAttribute attribute) {
        return attribute == TrackableAttribute.ESCORE_PITTSBURGH ? psqiScore : null;
    }

    public Integer getFreqOtherReason() {
        return freqOtherReason;
    }

    public void setFreqOtherReason(Integer freqOtherReason) {
        this.freqOtherReason = freqOtherReason;
    }
}
