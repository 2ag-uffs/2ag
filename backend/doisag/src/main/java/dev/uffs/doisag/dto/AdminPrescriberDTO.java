package dev.uffs.doisag.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.uffs.doisag.model.Prescriber;

// o q o administrador ve de cada prescritor
// so dado cadastral e nada de paciente
public record AdminPrescriberDTO(
        Long id,
        String name,
        String email,
        String profession,
        String registryType,
        String registryNumber,
        boolean active,
        // enquanto a pessoa n cria a senha pelo link, o administrador ainda pode corrigir o e-mail
        boolean passwordCreated,
        // so na resposta da desativacao: quantas escalas esperando resposta e quantos pedidos e consultas
        // por vir ficaram com os pacientes dele (#148). eh so numero, sem nome nem prontuario
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Long openTasks,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Long futureAppointments
) {
    public AdminPrescriberDTO(Prescriber prescriber) {
        this(
                prescriber.getId(),
                prescriber.getName(),
                prescriber.getEmail(),
                prescriber.getProfession(),
                prescriber.getRegistryType(),
                prescriber.getRegistryNumber(),
                prescriber.isActive(),
                prescriber.getPasswordChangedAt() != null,
                null,
                null
        );
    }

    public AdminPrescriberDTO withPending(long openTasks, long futureAppointments) {
        return new AdminPrescriberDTO(id, name, email, profession, registryType, registryNumber, active,
                passwordCreated, openTasks, futureAppointments);
    }
}
