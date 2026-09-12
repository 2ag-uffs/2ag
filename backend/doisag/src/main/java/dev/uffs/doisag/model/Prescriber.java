package dev.uffs.doisag.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonManagedReference;


import java.time.LocalDate;

@Entity
// adiconei esse para evitar regsitro profsisonais iguais do mesmo órgão
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"registry_type", "registry_number"})
})
public class Prescriber extends Users {
    private String profession;

    // conselho profisisnal e numero unico da profissão
    private String registryType;
    private String registryNumber;


    // codigo unico de vinculo para a api
    @Column(unique = true)
    private String professionalCode;

    // anotacao para serialize lista de filhos normalmente
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

    public String getProfessionalCode() {
        return professionalCode;
    }

    public void setProfessionalCode(String professionalCode) {
        this.professionalCode = professionalCode;
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
}