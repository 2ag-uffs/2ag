package dev.uffs.doisag.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import dev.uffs.doisag.enums.UserRole;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.ArrayList;
import java.util.List;

@Entity
// evita dois cadastros com o mesmo registro profissional no mesmo conselho
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"registry_type", "registry_number"})
})
public class Prescriber extends Users {
    private String profession;

    // conselho profissional e numero do registro
    private String registryType;
    private String registryNumber;

    // quanto tempo cada consulta ocupa na agenda (RF11)
    // divide os periodos de atendimento nos horarios q o paciente pode pedir
    private Integer appointmentDurationMinutes = 60;

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

    @Override
    public UserRole getRole() {
        return UserRole.PRESCRIBER;
    }
}
