package dev.uffs.doisag.model;

import jakarta.persistence.*;

// degrau do escalonamento de dose pq a clinica comeca baixo e sobe devagar (RN02)
// fica estruturado pra montar o historico de ajustes de dose q a prescritora pediu
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
