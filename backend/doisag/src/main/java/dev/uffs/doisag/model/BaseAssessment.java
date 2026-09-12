package dev.uffs.doisag.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;
import jakarta.persistence.*;

import java.time.LocalDate;

@MappedSuperclass
// dados que se repetem em todos os formulários
@EntityListeners(AuditingEntityListener.class)
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

    // quando o registro nasceu e quando foi mexido pela ultima vez.
    // o spring preenche sozinho, ninguem seta na mao (RF31)
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
