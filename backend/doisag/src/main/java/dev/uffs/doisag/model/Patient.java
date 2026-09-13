package dev.uffs.doisag.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.uffs.doisag.enums.UserRole;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
public class Patient extends Users {

    // adicionei essa anotacao desse lado gerenciado para evitar a referencia infinita
    @JsonBackReference
    // muitos pacientes pertencem a um prescritor
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescriber_id") // nome da coluna da chave estrangeira no banco
    private Prescriber prescriber;

    // preenchidos so enquanto o prescritor deixar o paciente no arquivo
    private LocalDateTime archivedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "archived_by_id")
    private Users archivedBy;

    public Patient() {
    }

    public Patient(Address address, LocalDate birthDate, String cpf, String email, Long id, String name, String password, String phone) {
        super(address, birthDate, cpf, email, id, name, password, phone);
    }

    public Prescriber getPrescriber() {
        return prescriber;
    }

    public void setPrescriber(Prescriber prescriber) {
        this.prescriber = prescriber;
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(LocalDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }

    // o arquivamento sai na resposta pelos dtos e n pela entidade crua
    @JsonIgnore
    public Users getArchivedBy() {
        return archivedBy;
    }

    public void setArchivedBy(Users archivedBy) {
        this.archivedBy = archivedBy;
    }

    @JsonIgnore
    public boolean isArchived() {
        return archivedAt != null;
    }

    @Override
    public UserRole getRole() {
        return UserRole.PATIENT;
    }
}
