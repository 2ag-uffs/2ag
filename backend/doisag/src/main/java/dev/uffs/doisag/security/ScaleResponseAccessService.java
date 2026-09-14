package dev.uffs.doisag.security;

import dev.uffs.doisag.repository.ScaleResponseRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

// decide quem pode mexer numa escala respondida (RF30)
//
// toda resposta sabe de qual paciente ela eh, entao a regra eh a mesma
// de qualquer dado clinico: acha o dono e pergunta pro PatientAccessService
@Service("scaleAccess")
public class ScaleResponseAccessService {

    private final ScaleResponseRepository responseRepository;
    private final PatientAccessService patientAccess;

    public ScaleResponseAccessService(ScaleResponseRepository responseRepository,
                                      PatientAccessService patientAccess) {
        this.responseRepository = responseRepository;
        this.patientAccess = patientAccess;
    }

    public boolean canAccess(Long responseId, Authentication authentication) {
        if (responseId == null) {
            return false;
        }
        // resposta q n existe da 403 e n 404, de proposito: assim ninguem
        // descobre quais ids existem chutando
        return responseRepository.findById(responseId)
                .map(response -> patientAccess.canAccess(response.getPatient().getId(), authentication))
                .orElse(false);
    }
}
