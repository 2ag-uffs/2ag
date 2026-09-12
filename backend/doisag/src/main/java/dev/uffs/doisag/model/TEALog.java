package dev.uffs.doisag.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import java.time.LocalDate;

@Entity
public class TEALog extends BaseAssessment {
    private int freqAggressiveness;
    private int freqAgitation;
    private int freqSleepIssues;
    private int freqSocialInteraction;
    private int freqStereotypy;
    private int freqAppetiteIssues;
    @Lob
    @Column(columnDefinition = "TEXT")
    private String observation;
    private int teaScore;

    // Construtor completo
    public TEALog(Long id, LocalDate assessmentDate, Patient patient, int freqAgitation, int freqSleepIssues, int freqAggressiveness, int freqSocialInteraction, int freqStereotypy, int freqAppetiteIssues, String observation, int teaScore) {
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
    public TEALog(Long id, LocalDate assessmentDate, Patient patient, int freqAggressiveness, int teaScore, int freqAppetiteIssues, int freqStereotypy, int freqSocialInteraction, int freqSleepIssues, int freqAgitation) {
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
    public int getFreqAggressiveness() { return freqAggressiveness; }
    public void setFreqAggressiveness(int freqAggressiveness) { this.freqAggressiveness = freqAggressiveness; }

    public int getFreqAgitation() { return freqAgitation; }
    public void setFreqAgitation(int freqAgitation) { this.freqAgitation = freqAgitation; }

    public int getFreqSleepIssues() { return freqSleepIssues; }
    public void setFreqSleepIssues(int freqSleepIssues) { this.freqSleepIssues = freqSleepIssues; }

    public int getFreqSocialInteraction() { return freqSocialInteraction; }
    public void setFreqSocialInteraction(int freqSocialInteraction) { this.freqSocialInteraction = freqSocialInteraction; }

    public int getFreqStereotypy() { return freqStereotypy; }
    public void setFreqStereotypy(int freqStereotypy) { this.freqStereotypy = freqStereotypy; }

    public int getFreqAppetiteIssues() { return freqAppetiteIssues; }
    public void setFreqAppetiteIssues(int freqAppetiteIssues) { this.freqAppetiteIssues = freqAppetiteIssues; }

    public String getObservation() { return observation; }
    public void setObservation(String observation) { this.observation = observation; }

    public int getTeaScore() { return teaScore; }
    public void setTeaScore(int teaScore) { this.teaScore = teaScore; }
}