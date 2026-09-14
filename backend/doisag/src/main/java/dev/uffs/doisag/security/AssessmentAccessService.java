package dev.uffs.doisag.security;

import dev.uffs.doisag.repository.AnamnesisRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

// decide quem pode mexer numa anamnese ja salva (RF30)
//
// as escalas passaram pro scaleAccess quando viraram uma tabela so, e a
// anamnese seguiu com tela e tabela propria desde o modulo de atendimento
@Service("assessmentAccess")
public class AssessmentAccessService {

    private final AnamnesisRepository anamnesisRepository;
    private final PatientAccessService patientAccess;

    public AssessmentAccessService(AnamnesisRepository anamnesisRepository, PatientAccessService patientAccess) {
        this.anamnesisRepository = anamnesisRepository;
        this.patientAccess = patientAccess;
    }

    // o tipo vem como texto do PreAuthorize e so a anamnese eh aceita aqui
    public boolean canAccess(String scaleType, Long assessmentId, Authentication authentication) {
        if (assessmentId == null || !"ANAMNESE".equals(scaleType)) {
            return false;
        }
        // registro q n existe da 403 e n 404, de propósito: assim ninguem
        // descobre quais ids existem chutando
        return anamnesisRepository.findById(assessmentId)
                .map(anamnesis -> anamnesis.getPatient() != null
                        && patientAccess.canAccess(anamnesis.getPatient().getId(), authentication))
                .orElse(false);
    }
}
