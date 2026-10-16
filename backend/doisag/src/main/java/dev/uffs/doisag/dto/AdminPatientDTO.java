package dev.uffs.doisag.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.uffs.doisag.model.Patient;

// o q o administrador ve da conta de um paciente
// so o q precisa pra conferir q eh a pessoa certa, sem cpf nem nada do prontuario
// name eh o nome q as telas mostram e o civil vem junto so qnd o paciente tem nome social (RN15)
public record AdminPatientDTO(
        Long id,
        String name,
        String email,
        boolean active,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String civilName
) {

    public AdminPatientDTO(Patient patient) {
        this(patient.getId(), patient.getDisplayName(), patient.getEmail(), patient.isActive(),
                patient.getSocialName() == null ? null : patient.getName());
    }
}
