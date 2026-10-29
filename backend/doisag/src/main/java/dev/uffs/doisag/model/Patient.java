package dev.uffs.doisag.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.uffs.doisag.enums.UserRole;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

@Entity
// msm motivo do Users
@DynamicUpdate
public class Patient extends Users {

    // abaixo disso o responsavel legal participa do aceite do termo (LGPD art. 14 e Codigo Civil)
    // menor de 16 eh representado, ele aceita em nome do paciente. com 16 e 17 o paciente aceita junto com ele
    public static final int ADULT_AGE = 18;

    // lado de volta da lista do Prescriber pq sem isso o json entra em loop
    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescriber_id")
    private Prescriber prescriber;

    // preenchidos so enquanto o prescritor deixar o paciente no arquivo
    private LocalDateTime archivedAt;

    // tipo concreto pelo mesmo motivo do Annulment.annulledBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "archived_by_id")
    private Prescriber archivedBy;

    // responsavel ou cuidador (RF38)
    private String guardianName;
    private String guardianRelationship;
    private String guardianPhone;

    // nome social, opcional (RN15)
    private String socialName;

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
    public Prescriber getArchivedBy() {
        return archivedBy;
    }

    public void setArchivedBy(Prescriber archivedBy) {
        this.archivedBy = archivedBy;
    }

    public String getGuardianName() {
        return guardianName;
    }

    public void setGuardianName(String guardianName) {
        this.guardianName = guardianName;
    }

    public String getGuardianRelationship() {
        return guardianRelationship;
    }

    public void setGuardianRelationship(String guardianRelationship) {
        this.guardianRelationship = guardianRelationship;
    }

    public String getGuardianPhone() {
        return guardianPhone;
    }

    public void setGuardianPhone(String guardianPhone) {
        this.guardianPhone = guardianPhone;
    }

    public String getSocialName() {
        return socialName;
    }

    public void setSocialName(String socialName) {
        this.socialName = socialName;
    }

    // qnd tem nome social eh ele q aparece, o civil fica so onde identifica a pessoa
    @JsonIgnore
    @Override
    public String getDisplayName() {
        if (socialName != null) {
            return socialName;
        }
        return getName();
    }

    @JsonIgnore
    public boolean isArchived() {
        return archivedAt != null;
    }

    // a idade sai sempre da data de nascimento no servidor, o front n manda se eh menor
    // o LocalDate.now() ja eh o horario de brasilia (DoisagApplication)
    public static boolean isMinor(LocalDate birthDate) {
        return isMinor(birthDate, LocalDate.now());
    }

    // anos completos, como no front: qm nasceu em 29/02 faz 18 em 01/03 (Codigo Civil art. 132 § 3)
    public static boolean isMinor(LocalDate birthDate, LocalDate today) {
        return birthDate != null && Period.between(birthDate, today).getYears() < ADULT_AGE;
    }

    @JsonIgnore
    public boolean isMinor() {
        return isMinor(getBirthDate());
    }

    @Override
    public UserRole getRole() {
        return UserRole.PATIENT;
    }
}
