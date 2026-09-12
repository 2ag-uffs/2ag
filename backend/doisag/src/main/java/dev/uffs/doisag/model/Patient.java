package dev.uffs.doisag.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import com.fasterxml.jackson.annotation.JsonBackReference;

import java.time.LocalDate;

@Entity
public class Patient extends Users {

    // adicionei essa anotacao desse lado gerenciado para evitar a referência infinita
    @JsonBackReference
    // muitos pacientes pertencem a um prescritor
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescriber_id") // nome da coluna da chave estrangeira no banco
    private Prescriber prescriber;

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
}
