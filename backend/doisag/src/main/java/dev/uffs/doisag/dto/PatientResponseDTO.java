package dev.uffs.doisag.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;

import java.time.LocalDate;
import java.time.LocalDateTime;

// o que a api devolve quando o assunto eh paciente
// so o q as telas usam: cpf e contato ficam no perfil da propria pessoa
public record PatientResponseDTO(
        Long id,
        // nome q as telas mostram, o social qnd tem (RN15)
        String name,
        LocalDate birthDate,
        Long prescriberId,
        String prescriberName,
        boolean archived,
        LocalDateTime archivedAt,
        // a conta desligada pela administracao, pro prescritor ver o selo na lista e no prontuario
        // n muda contagem nenhuma: so o arquivamento tira o paciente da lista
        boolean accountActive,
        // os dois so vem no prontuario de um paciente, a lista n leva (RF38 e RF37)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        GuardianDTO guardian,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Boolean researchConsent,
        // nome civil so no prontuario e so qnd o paciente tem nome social
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String civilName,
        // so na resposta do arquivamento: quantas consultas marcadas ficaram por vir na agenda
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Long futureAppointments
) {
    public PatientResponseDTO(Patient patient) {
        this(patient, null, null, null, null);
    }

    private PatientResponseDTO(Patient patient, GuardianDTO guardian, Boolean researchConsent, String civilName,
                               Long futureAppointments) {
        this(
                patient.getId(),
                patient.getDisplayName(),
                patient.getBirthDate(),
                prescriberIdOf(patient),
                prescriberNameOf(patient),
                patient.isArchived(),
                patient.getArchivedAt(),
                patient.isActive(),
                guardian,
                researchConsent,
                civilName,
                futureAppointments
        );
    }

    public static PatientResponseDTO forChart(Patient patient, boolean researchConsent) {
        String civilName = patient.civilNameWhenSocial();
        return new PatientResponseDTO(patient, GuardianDTO.from(patient), researchConsent, civilName, null);
    }

    public static PatientResponseDTO forArchive(Patient patient, long futureAppointments) {
        return new PatientResponseDTO(patient, null, null, null, futureAppointments);
    }

    // o prescritor pode ser nulo e o campo eh lazy entao separei
    // em metodos pra n poluir o construtor
    private static Long prescriberIdOf(Patient patient) {
        Prescriber prescriber = patient.getPrescriber();
        return prescriber == null ? null : prescriber.getId();
    }

    private static String prescriberNameOf(Patient patient) {
        Prescriber prescriber = patient.getPrescriber();
        return prescriber == null ? null : prescriber.getName();
    }
}
