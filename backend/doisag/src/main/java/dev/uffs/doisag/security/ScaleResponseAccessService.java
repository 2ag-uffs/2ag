package dev.uffs.doisag.security;

import dev.uffs.doisag.model.Users;
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
        // so o id do paciente, sem carregar a resposta: o servico vai travar e ler ela depois
        boolean canAccessPatient = responseRepository.findPatientIdById(responseId)
                .map(patientId -> patientAccess.canAccess(patientId, authentication))
                .orElse(false);
        return canAccessPatient && !isHiddenFromLoggedUser(responseId, authentication);
    }

    // o paciente n abre o MEEM e recebe o mesmo 403 de uma resposta q n eh dele,
    // entao ele n descobre nem q o exame existe (decisao de 13/11/2026)
    private boolean isHiddenFromLoggedUser(Long responseId, Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof Users loggedUser)) {
            return true;
        }
        return responseRepository.findScaleTypeById(responseId)
                .map(scaleType -> scaleType.isHiddenFrom(loggedUser.getRole()))
                .orElse(true);
    }
}
