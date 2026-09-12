package dev.uffs.doisag.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import dev.uffs.doisag.enums.AssignmentStatus;
import dev.uffs.doisag.enums.ScaleType;

@Entity
public class AssignedScale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // fk para o paciente que recebeu a tarefa
    @ManyToOne
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    // fk para o prescritor que mandou a tarefa
    @ManyToOne
    @JoinColumn(name = "prescriber_id", nullable = false)
    private Prescriber prescriber;

    // aqui a gente guarda o nome da escala, tipo 'ESCALA_HAMILTON' ou 'REGISTRO_DOR'
    // usei um enum para garantir que só valores válidos entrem aqui
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScaleType scaleType;

    // o status atual da tarefa, se tá pendente, preenchida, etc
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentStatus status;

    private LocalDate assignedDate; // o dia que a tarefa foi criada
    private LocalDate completedDate; // o dia que o paciente respondeu

    public AssignedScale() {
    }

    public LocalDate getAssignedDate() {
        return assignedDate;
    }

    public void setAssignedDate(LocalDate assignedDate) {
        this.assignedDate = assignedDate;
    }

    public AssignmentStatus getStatus() {
        return status;
    }

    public void setStatus(AssignmentStatus status) {
        this.status = status;
    }

    public ScaleType getScaleType() {
        return scaleType;
    }

    public void setScaleType(ScaleType scaleType) {
        this.scaleType = scaleType;
    }

    public Prescriber getPrescriber() {
        return prescriber;
    }

    public void setPrescriber(Prescriber prescriber) {
        this.prescriber = prescriber;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getCompletedDate() {
        return completedDate;
    }

    public void setCompletedDate(LocalDate completedDate) {
        this.completedDate = completedDate;
    }
}