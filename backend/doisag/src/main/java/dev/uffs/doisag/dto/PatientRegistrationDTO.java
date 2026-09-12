package dev.uffs.doisag.dto;
// so carrega os dados do paciente que o prescritor vai preencher no form
import java.time.LocalDate;
import dev.uffs.doisag.model.Address;

public record PatientRegistrationDTO(
        String name,
        String email,
        String cpf,
        String phone,
        LocalDate birthDate,
        Address address,
        String senha
) {}