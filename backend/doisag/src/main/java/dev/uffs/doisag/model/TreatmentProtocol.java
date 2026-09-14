package dev.uffs.doisag.model;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// o plano de acompanhamento de um paciente: quais escalas ele responde
// e de quanto em quanto tempo, pelos 90 dias de tratamento.
//
// eh isso que tira da prescritora a tarefa de designar escala uma por
// uma, que era a sobrecarga relatada na entrevista de 2025 (RF32)
@Entity
@EntityListeners(AuditingEntityListener.class)
public class TreatmentProtocol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescriber_id", nullable = false)
    private Prescriber prescriber;

    @Column(nullable = false)
    private LocalDate startDate;

    // o acompanhamento dura 90 dias por padrao (RN01), mas fica gravado
    // aqui pra dar pra renovar ou encurtar sem mexer em codigo
    @Column(nullable = false)
    private LocalDate endDate;

    // protocolo encerrado n designa mais nada
    @Column(nullable = false)
    private boolean active = true;

    // a programacao de horarios q aparece no topo do diario do sono (RF22)
    private LocalTime sleepBedTime;

    private LocalTime sleepWakeTime;

    @OneToMany(mappedBy = "protocol", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProtocolItem> items = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public Prescriber getPrescriber() {
        return prescriber;
    }

    public void setPrescriber(Prescriber prescriber) {
        this.prescriber = prescriber;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalTime getSleepBedTime() {
        return sleepBedTime;
    }

    public void setSleepBedTime(LocalTime sleepBedTime) {
        this.sleepBedTime = sleepBedTime;
    }

    public LocalTime getSleepWakeTime() {
        return sleepWakeTime;
    }

    public void setSleepWakeTime(LocalTime sleepWakeTime) {
        this.sleepWakeTime = sleepWakeTime;
    }

    public List<ProtocolItem> getItems() {
        return items;
    }

    public void addItem(ProtocolItem item) {
        item.setProtocol(this);
        this.items.add(item);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
