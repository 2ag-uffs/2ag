package dev.uffs.doisag.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
public class PittsburghScale extends BaseAssessment {
    private LocalTime usualBedTime;
    private int minutesToFallAsleep;
    private LocalTime usualWakeUpTime;
    private float actualSleepHours;
    private int freqCannotFallAsleep;
    private int freqWakesUpMiddleNight;
    private int freqWakeUpForBathroom;
    private int freqCannotBreathe;
    private int freqCoughOrSnore;
    private int freqFeelCold;
    private int freqFeelHot;
    private int freqHaveBadDreams;
    private int freqHavePain;
    @Lob // campo grande para anotar
    @Column(columnDefinition = "TEXT")
    private String otherReasonToTroubleSleep;
    private int sleepQualityRating;
    private int freqUseSleepMedication;
    private int freqTroubleStayingAwake;
    private int troubleWithEnthusiasm;
    private int roomPartner;
    private int psqiScore;

    public PittsburghScale(Long id, LocalDate assessmentDate, Patient patient, LocalTime usualBedTime, int minutesToFallAsleep, LocalTime usualWakeUpTime, float actualSleepHours, int freqWakesUpMiddleNight, int freqCannotFallAsleep, int freqWakeUpForBathroom, int freqCannotBreathe, int freqCoughOrSnore, int freqFeelCold, int freqFeelHot, int freqHaveBadDreams, int freqHavePain, String otherReasonToTroubleSleep, int sleepQualityRating, int freqUseSleepMedication, int troubleWithEnthusiasm, int freqTroubleStayingAwake, int roomPartner, int psqiScore) {
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

    public int getMinutesToFallAsleep() {
        return minutesToFallAsleep;
    }

    public void setMinutesToFallAsleep(int minutesToFallAsleep) {
        this.minutesToFallAsleep = minutesToFallAsleep;
    }

    public LocalTime getUsualWakeUpTime() {
        return usualWakeUpTime;
    }

    public void setUsualWakeUpTime(LocalTime usualWakeUpTime) {
        this.usualWakeUpTime = usualWakeUpTime;
    }

    public float getActualSleepHours() {
        return actualSleepHours;
    }

    public void setActualSleepHours(float actualSleepHours) {
        this.actualSleepHours = actualSleepHours;
    }

    public int getFreqCannotFallAsleep() {
        return freqCannotFallAsleep;
    }

    public void setFreqCannotFallAsleep(int freqCannotFallAsleep) {
        this.freqCannotFallAsleep = freqCannotFallAsleep;
    }

    public int getFreqWakesUpMiddleNight() {
        return freqWakesUpMiddleNight;
    }

    public void setFreqWakesUpMiddleNight(int freqWakesUpMiddleNight) {
        this.freqWakesUpMiddleNight = freqWakesUpMiddleNight;
    }

    public int getFreqWakeUpForBathroom() {
        return freqWakeUpForBathroom;
    }

    public void setFreqWakeUpForBathroom(int freqWakeUpForBathroom) {
        this.freqWakeUpForBathroom = freqWakeUpForBathroom;
    }

    public int getFreqCannotBreathe() {
        return freqCannotBreathe;
    }

    public void setFreqCannotBreathe(int freqCannotBreathe) {
        this.freqCannotBreathe = freqCannotBreathe;
    }

    public int getFreqCoughOrSnore() {
        return freqCoughOrSnore;
    }

    public void setFreqCoughOrSnore(int freqCoughOrSnore) {
        this.freqCoughOrSnore = freqCoughOrSnore;
    }

    public int getFreqFeelCold() {
        return freqFeelCold;
    }

    public void setFreqFeelCold(int freqFeelCold) {
        this.freqFeelCold = freqFeelCold;
    }

    public int getFreqFeelHot() {
        return freqFeelHot;
    }

    public void setFreqFeelHot(int freqFeelHot) {
        this.freqFeelHot = freqFeelHot;
    }

    public int getFreqHavePain() {
        return freqHavePain;
    }

    public void setFreqHavePain(int freqHavePain) {
        this.freqHavePain = freqHavePain;
    }

    public int getFreqHaveBadDreams() {
        return freqHaveBadDreams;
    }

    public void setFreqHaveBadDreams(int freqHaveBadDreams) {
        this.freqHaveBadDreams = freqHaveBadDreams;
    }

    public String getOtherReasonToTroubleSleep() {
        return otherReasonToTroubleSleep;
    }

    public void setOtherReasonToTroubleSleep(String otherReasonToTroubleSleep) {
        this.otherReasonToTroubleSleep = otherReasonToTroubleSleep;
    }

    public int getSleepQualityRating() {
        return sleepQualityRating;
    }

    public void setSleepQualityRating(int sleepQualityRating) {
        this.sleepQualityRating = sleepQualityRating;
    }

    public int getFreqUseSleepMedication() {
        return freqUseSleepMedication;
    }

    public void setFreqUseSleepMedication(int freqUseSleepMedication) {
        this.freqUseSleepMedication = freqUseSleepMedication;
    }

    public int getFreqTroubleStayingAwake() {
        return freqTroubleStayingAwake;
    }

    public void setFreqTroubleStayingAwake(int freqTroubleStayingAwake) {
        this.freqTroubleStayingAwake = freqTroubleStayingAwake;
    }

    public int getTroubleWithEnthusiasm() {
        return troubleWithEnthusiasm;
    }

    public void setTroubleWithEnthusiasm(int troubleWithEnthusiasm) {
        this.troubleWithEnthusiasm = troubleWithEnthusiasm;
    }

    public int getRoomPartner() {
        return roomPartner;
    }

    public void setRoomPartner(int roomPartner) {
        this.roomPartner = roomPartner;
    }

    public int getPsqiScore() {
        return psqiScore;
    }

    public void setPsqiScore(int psqiScore) {
        this.psqiScore = psqiScore;
    }

}
