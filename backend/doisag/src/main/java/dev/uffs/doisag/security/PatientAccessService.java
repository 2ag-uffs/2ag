package dev.uffs.doisag.security;

import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PatientRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

// lugar unico que decide quem pode ver dado de qual paciente (RF30).
// os controllers chamam isso pelo @PreAuthorize, tipo:
//   @PreAuthorize("@patientAccess.canAccess(#patientId, authentication)")
//
// a regra e simples:
//   paciente    -> so os proprios dados
//   prescritor  -> so os pacientes vinculados a ele
@Service("patientAccess")
public class PatientAccessService {

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;

    public PatientAccessService(PatientRepository patientRepository, AppointmentRepository appointmentRepository) {
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
    }

    public boolean canAccess(Long patientId, Authentication authentication) {
        Users loggedUser = loggedUserOf(authentication);
        if (loggedUser == null || patientId == null) {
            return false;
        }

        // paciente olhando o proprio prontuario
        if (loggedUser instanceof Patient) {
            return patientId.equals(loggedUser.getId());
        }

        // prescritor: vale so se aquele paciente for da carteira dele.
        // antes qualquer prescritor via o prontuario de qualquer paciente
        if (loggedUser instanceof Prescriber) {
            return patientRepository.existsByIdAndPrescriberId(patientId, loggedUser.getId());
        }

        return false;
    }

    // versao pro caso em que o proprio usuario logado eh o alvo, tipo
    // /prescritor/{id}: o prescritor mexe na ficha dele e mais nada
    public boolean isSelf(Long userId, Authentication authentication) {
        Users loggedUser = loggedUserOf(authentication);
        return loggedUser != null && userId != null && userId.equals(loggedUser.getId());
    }

    // devolve o paciente logado, ou null se quem esta logado n eh paciente.
    // usado no POST das escalas pra forcar o dono a ser quem preencheu,
    // em vez de aceitar o id que veio no corpo da requisicao
    public Patient loggedPatient(Authentication authentication) {
        Users loggedUser = loggedUserOf(authentication);
        return loggedUser instanceof Patient patient ? patient : null;
    }

    // consulta eh do prescritor que a conduziu, ou do paciente atendido.
    // usado pra prescricao, que sempre nasce dentro de uma consulta
    public boolean canAccessAppointment(Long appointmentId, Authentication authentication) {
        Users loggedUser = loggedUserOf(authentication);
        if (loggedUser == null || appointmentId == null) {
            return false;
        }
        return appointmentRepository.findById(appointmentId)
                .map(consulta -> {
                    if (loggedUser instanceof Prescriber) {
                        return consulta.getPrescriber().getId().equals(loggedUser.getId());
                    }
                    return consulta.getPatient().getId().equals(loggedUser.getId());
                })
                .orElse(false);
    }

    // quem pode ver a ficha de um prescritor: ele mesmo, ou um paciente
    // que esta vinculado a ele
    public boolean canViewPrescriber(Long prescriberId, Authentication authentication) {
        Users loggedUser = loggedUserOf(authentication);
        if (loggedUser == null || prescriberId == null) {
            return false;
        }
        if (prescriberId.equals(loggedUser.getId())) {
            return true;
        }
        // aqui o paciente eh o usuario logado e o prescritor eh o alvo
        return loggedUser instanceof Patient
                && patientRepository.existsByIdAndPrescriberId(loggedUser.getId(), prescriberId);
    }

    // o principal eh o nosso Users pq o SecurityFilter coloca a entidade
    // ali. em teste com @WithMockUser vem um User do spring, ai n da
    // pra resolver vinculo e a resposta eh negar
    private Users loggedUserOf(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Users users)) {
            return null;
        }
        return users;
    }
}
