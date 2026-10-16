package dev.uffs.doisag.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import dev.uffs.doisag.enums.UserRole;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.DynamicUpdate;

import java.util.ArrayList;
import java.util.List;

@Entity
// evita dois cadastros com o mesmo registro profissional no mesmo conselho
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"registry_type", "registry_number"})
})
// o lote do Users n vale pro proxy de Prescriber: quem anulou, analisou ou arquivou numa lista
// vem em lote tbm, em vez de uma consulta por linha
@BatchSize(size = 50)
// msm motivo do Users
@DynamicUpdate
public class Prescriber extends Users {
    private String profession;

    // conselho profissional e numero do registro
    private String registryType;
    private String registryNumber;

    // quanto tempo o retorno ocupa na agenda (RF11)
    // o nome ficou o antigo de qnd era uma duracao so pra toda consulta
    private Integer appointmentDurationMinutes = 60;

    // quanto tempo a primeira consulta ocupa na agenda
    private Integer firstAppointmentDurationMinutes = 90;

    // anotacao pra serializar a lista de pacientes normalmente
    @JsonManagedReference
    @OneToMany(mappedBy = "prescriber")
    private List<Patient> patients = new ArrayList<>();

    public Prescriber() {
    }

    public List<Patient> getPatients() {
        return patients;
    }

    public void setPatients(List<Patient> patients) {
        this.patients = patients;
    }

    public String getProfession() {
        return profession;
    }

    public void setProfession(String profession) {
        this.profession = profession;
    }

    public String getRegistryNumber() {
        return registryNumber;
    }

    public void setRegistryNumber(String registryNumber) {
        this.registryNumber = registryNumber;
    }

    public String getRegistryType() {
        return registryType;
    }

    public void setRegistryType(String registryType) {
        this.registryType = registryType;
    }

    public Integer getAppointmentDurationMinutes() {
        return appointmentDurationMinutes;
    }

    public void setAppointmentDurationMinutes(Integer appointmentDurationMinutes) {
        this.appointmentDurationMinutes = appointmentDurationMinutes;
    }

    public Integer getFirstAppointmentDurationMinutes() {
        return firstAppointmentDurationMinutes;
    }

    public void setFirstAppointmentDurationMinutes(Integer firstAppointmentDurationMinutes) {
        this.firstAppointmentDurationMinutes = firstAppointmentDurationMinutes;
    }

    @Override
    public UserRole getRole() {
        return UserRole.PRESCRIBER;
    }
}
