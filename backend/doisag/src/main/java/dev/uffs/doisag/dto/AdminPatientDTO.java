package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.Patient;

// o q o administrador ve da conta de um paciente
// so o q precisa pra conferir q eh a pessoa certa, sem cpf nem nada do prontuario
public record AdminPatientDTO(Long id, String name, String email, boolean active) {

    public AdminPatientDTO(Patient patient) {
        this(patient.getId(), patient.getName(), patient.getEmail(), patient.isActive());
    }
}
