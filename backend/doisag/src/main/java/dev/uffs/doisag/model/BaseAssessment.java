package dev.uffs.doisag.model;

import jakarta.persistence.*;

import java.time.LocalDate;

@MappedSuperclass
// dados que se repetem em todos os formulários
public abstract class BaseAssessment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate assessmentDate;

    // relacionamento (1:n)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    public BaseAssessment(Long id, LocalDate assessmentDate, Patient patient) {
        this.id = id;
        this.assessmentDate = assessmentDate;
        this.patient = patient;
    }

    public BaseAssessment() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getAssessmentDate() {
        return assessmentDate;
    }

    public void setAssessmentDate(LocalDate assessmentDate) {
        this.assessmentDate = assessmentDate;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }
}
