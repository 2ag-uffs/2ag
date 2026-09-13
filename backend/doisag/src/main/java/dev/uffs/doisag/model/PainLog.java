package dev.uffs.doisag.model;

import dev.uffs.doisag.enums.TrackableAttribute;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;

import java.time.LocalDate;

@Entity
public class PainLog extends BaseAssessment {
    // a escala visual do formulario: leve 0 a 2, moderada 3 a 7,
    // intensa 8 a 10. eh a medida principal do registro de dor e n
    // existia no modelo, entao a tela n tinha onde guardar (RF25)
    private Integer painIntensity;

    private Integer basicActivityInterference;
    private Integer socialActivityInterference;
    private Integer sleepInterference;
    private Integer productivityInterference;
    private Integer extraMedication;
    @Column(columnDefinition = "TEXT")
    private String observation;

    public PainLog(Long id, LocalDate assessmentDate, Patient patient, Integer socialActivityInterference, Integer basicActivityInterference, Integer sleepInterference, Integer productivityInterference, Integer extraMedication, String observation) {
        super(id, assessmentDate, patient);
        this.socialActivityInterference = socialActivityInterference;
        this.basicActivityInterference = basicActivityInterference;
        this.sleepInterference = sleepInterference;
        this.productivityInterference = productivityInterference;
        this.extraMedication = extraMedication;
        this.observation = observation;
    }
        // observacao eh opcional
    public PainLog(Long id, LocalDate assessmentDate, Patient patient, Integer basicActivityInterference, Integer extraMedication, Integer productivityInterference, Integer sleepInterference, Integer socialActivityInterference) {
        super(id, assessmentDate, patient);
        this.basicActivityInterference = basicActivityInterference;
        this.extraMedication = extraMedication;
        this.productivityInterference = productivityInterference;
        this.sleepInterference = sleepInterference;
        this.socialActivityInterference = socialActivityInterference;
    }

    public PainLog() {
    }

    public Integer getSocialActivityInterference() {
        return socialActivityInterference;
    }

    public void setSocialActivityInterference(Integer socialActivityInterference) {
        this.socialActivityInterference = socialActivityInterference;
    }

    public Integer getBasicActivityInterference() {
        return basicActivityInterference;
    }

    public void setBasicActivityInterference(Integer basicActivityInterference) {
        this.basicActivityInterference = basicActivityInterference;
    }

    public Integer getSleepInterference() {
        return sleepInterference;
    }

    public void setSleepInterference(Integer sleepInterference) {
        this.sleepInterference = sleepInterference;
    }

    public Integer getProductivityInterference() {
        return productivityInterference;
    }

    public void setProductivityInterference(Integer productivityInterference) {
        this.productivityInterference = productivityInterference;
    }

    public Integer getExtraMedication() {
        return extraMedication;
    }

    public void setExtraMedication(Integer extraMedication) {
        this.extraMedication = extraMedication;
    }

    public String getObservation() {
        return observation;
    }

    public void setObservation(String observation) {
        this.observation = observation;
    }

    public Integer getPainIntensity() {
        return painIntensity;
    }

    public void setPainIntensity(Integer painIntensity) {
        this.painIntensity = painIntensity;
    }

    @Override
    public Integer trackedValue(TrackableAttribute attribute) {
        return attribute == TrackableAttribute.INTENSIDADE_DOR ? painIntensity : null;
    }
}
