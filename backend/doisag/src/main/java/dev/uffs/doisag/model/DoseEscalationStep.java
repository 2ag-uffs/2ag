package dev.uffs.doisag.model;

import jakarta.persistence.*;

// um degrau do escalonamento de dose. a conduta da clinica eh comecar
// baixo e subir devagar (RN02), entao a prescricao n tem uma dose so:
// tem um plano de semana a semana.
// guardar isso estruturado eh o que permite montar o historico de
// ajustes de dose que a prescritora pediu
@Entity
public class DoseEscalationStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // semana 1, semana 2, e assim por diante
    private Integer week;

    // quanto tomar nessa semana, tipo "2 gotas pela manha"
    private String dosage;

    @Column(columnDefinition = "TEXT")
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescription_id", nullable = false)
    private Prescription prescription;

    public DoseEscalationStep() {
    }

    public Long getId() {
        return id;
    }

    public Integer getWeek() {
        return week;
    }

    public void setWeek(Integer week) {
        this.week = week;
    }

    public String getDosage() {
        return dosage;
    }

    public void setDosage(String dosage) {
        this.dosage = dosage;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Prescription getPrescription() {
        return prescription;
    }

    public void setPrescription(Prescription prescription) {
        this.prescription = prescription;
    }
}
