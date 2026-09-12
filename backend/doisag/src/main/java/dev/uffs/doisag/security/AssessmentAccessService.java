package dev.uffs.doisag.security;

import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.model.BaseAssessment;
import dev.uffs.doisag.repository.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

// decide se alguem pode mexer num registro de escala ja salvo.
// as 7 escalas herdam de BaseAssessment, que sabe de qual paciente ela
// eh, entao da pra resolver todas do mesmo jeito: acha o registro,
// pega o paciente dono, e pergunta pro PatientAccessService
@Service("assessmentAccess")
public class AssessmentAccessService {

    private final PatientAccessService patientAccess;
    private final Map<ScaleType, JpaRepository<? extends BaseAssessment, Long>> repositories =
            new EnumMap<>(ScaleType.class);

    public AssessmentAccessService(PatientAccessService patientAccess,
                                   AnamnesisRepository anamnesisRepository,
                                   FollowUpRepository followUpRepository,
                                   HamiltonScaleRepository hamiltonScaleRepository,
                                   PittsburghScaleRepository pittsburghScaleRepository,
                                   PainLogRepository painLogRepository,
                                   SleepLogRepository sleepLogRepository,
                                   TEALogRepository teaLogRepository) {
        this.patientAccess = patientAccess;
        repositories.put(ScaleType.ANAMNESE, anamnesisRepository);
        repositories.put(ScaleType.ACOMPANHAMENTO_SEMANAL, followUpRepository);
        repositories.put(ScaleType.ESCALA_HAMILTON, hamiltonScaleRepository);
        repositories.put(ScaleType.ESCALA_PITTSBURGH, pittsburghScaleRepository);
        repositories.put(ScaleType.REGISTRO_DOR, painLogRepository);
        repositories.put(ScaleType.REGISTRO_SONO, sleepLogRepository);
        repositories.put(ScaleType.REGISTRO_TEA, teaLogRepository);
    }

    // o tipo vem como texto do @PreAuthorize. se alguem escrever errado,
    // o valueOf explode na hora em vez de liberar acesso por engano
    public boolean canAccess(String scaleType, Long assessmentId, Authentication authentication) {
        if (assessmentId == null) {
            return false;
        }
        JpaRepository<? extends BaseAssessment, Long> repository =
                repositories.get(ScaleType.valueOf(scaleType));
        if (repository == null) {
            return false;
        }

        Optional<? extends BaseAssessment> found = repository.findById(assessmentId);
        if (found.isEmpty()) {
            // registro q n existe da 403 e n 404. de propósito: assim
            // ninguem descobre quais ids existem chutando
            return false;
        }

        var patient = found.get().getPatient();
        return patient != null && patientAccess.canAccess(patient.getId(), authentication);
    }
}
