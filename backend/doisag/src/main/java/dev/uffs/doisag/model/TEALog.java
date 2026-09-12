package dev.uffs.doisag.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import java.time.LocalDate;

@Entity
public class TEALog extends BaseAssessment {
    private Integer freqAggressiveness;
    private Integer freqAgitation;
    private Integer freqSleepIssues;
    private Integer freqSocialInteraction;
    private Integer freqStereotypy;
    private Integer freqAppetiteIssues;
    @Column(columnDefinition = "TEXT")
    private String observation;
    private Integer teaScore;

    // Construtor completo
    public TEALog(Long id, LocalDate assessmentDate, Patient patient, Integer freqAgitation, Integer freqSleepIssues, Integer freqAggressiveness, Integer freqSocialInteraction, Integer freqStereotypy, Integer freqAppetiteIssues, String observation, Integer teaScore) {
        super(id, assessmentDate, patient);
        this.freqAgitation = freqAgitation;
        this.freqSleepIssues = freqSleepIssues;
        this.freqAggressiveness = freqAggressiveness;
        this.freqSocialInteraction = freqSocialInteraction;
        this.freqStereotypy = freqStereotypy;
        this.freqAppetiteIssues = freqAppetiteIssues;
        this.observation = observation;
        this.teaScore = teaScore;
    }

    // Construtor com observação opcional
    public TEALog(Long id, LocalDate assessmentDate, Patient patient, Integer freqAggressiveness, Integer teaScore, Integer freqAppetiteIssues, Integer freqStereotypy, Integer freqSocialInteraction, Integer freqSleepIssues, Integer freqAgitation) {
        super(id, assessmentDate, patient);
        this.freqAggressiveness = freqAggressiveness;
        this.teaScore = teaScore;
        this.freqAppetiteIssues = freqAppetiteIssues;
        this.freqStereotypy = freqStereotypy;
        this.freqSocialInteraction = freqSocialInteraction;
        this.freqSleepIssues = freqSleepIssues;
        this.freqAgitation = freqAgitation;
    }

    // Construtor vazio
    public TEALog() {
    }

    // Getters e Setters
    public Integer getFreqAggressiveness() { return freqAggressiveness; }
    public void setFreqAggressiveness(Integer freqAggressiveness) { this.freqAggressiveness = freqAggressiveness; }

    public Integer getFreqAgitation() { return freqAgitation; }
    public void setFreqAgitation(Integer freqAgitation) { this.freqAgitation = freqAgitation; }

    public Integer getFreqSleepIssues() { return freqSleepIssues; }
    public void setFreqSleepIssues(Integer freqSleepIssues) { this.freqSleepIssues = freqSleepIssues; }

    public Integer getFreqSocialInteraction() { return freqSocialInteraction; }
    public void setFreqSocialInteraction(Integer freqSocialInteraction) { this.freqSocialInteraction = freqSocialInteraction; }

    public Integer getFreqStereotypy() { return freqStereotypy; }
    public void setFreqStereotypy(Integer freqStereotypy) { this.freqStereotypy = freqStereotypy; }

    public Integer getFreqAppetiteIssues() { return freqAppetiteIssues; }
    public void setFreqAppetiteIssues(Integer freqAppetiteIssues) { this.freqAppetiteIssues = freqAppetiteIssues; }

    public String getObservation() { return observation; }
    public void setObservation(String observation) { this.observation = observation; }

    public Integer getTeaScore() { return teaScore; }
    public void setTeaScore(Integer teaScore) { this.teaScore = teaScore; }
}