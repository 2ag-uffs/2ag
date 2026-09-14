package dev.uffs.doisag.security;

import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriptionRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

// lugar unico q decide quem pode ver dado de qual paciente (RF30)
// os controllers chamam isso no PreAuthorize junto com o perfil
//
// paciente ve so os proprios dados
// prescritor ve so os pacientes vinculados a ele
// administrador n ve dado clinico
//
// consulta prescricao e meem primeiro acham o paciente dono e dai seguem a mesma regra
@Service("patientAccess")
public class PatientAccessService {

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final PrescriptionRepository prescriptionRepository;

    public PatientAccessService(PatientRepository patientRepository, AppointmentRepository appointmentRepository,
                                PrescriptionRepository prescriptionRepository) {
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.prescriptionRepository = prescriptionRepository;
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

        // prescritor so ve paciente da carteira dele
        if (loggedUser instanceof Prescriber) {
            return patientRepository.existsByIdAndPrescriberId(patientId, loggedUser.getId());
        }

        return false;
    }

    // quando o alvo eh o proprio usuario logado tipo o painel do prescritor
    public boolean isSelf(Long userId, Authentication authentication) {
        Users loggedUser = loggedUserOf(authentication);
        return loggedUser != null && userId != null && userId.equals(loggedUser.getId());
    }

    // a consulta eh do paciente atendido
    // o prescritor entra se esse paciente estiver na carteira dele hoje
    public boolean canAccessAppointment(Long appointmentId, Authentication authentication) {
        if (appointmentId == null) {
            return false;
        }
        return appointmentRepository.findById(appointmentId)
                .map(appointment -> canAccess(appointment.getPatient().getId(), authentication))
                .orElse(false);
    }

    // a prescricao sempre nasce dentro de uma consulta
    public boolean canAccessPrescription(Long prescriptionId, Authentication authentication) {
        if (prescriptionId == null) {
            return false;
        }
        return prescriptionRepository.findById(prescriptionId)
                .map(prescription -> canAccessAppointment(prescription.getAppointment().getId(), authentication))
                .orElse(false);
    }

    // o SecurityFilter coloca a nossa entidade Users como principal
    // qualquer outra coisa ali eh negada
    private Users loggedUserOf(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Users users)) {
            return null;
        }
        return users;
    }
}
