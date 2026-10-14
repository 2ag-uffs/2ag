package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.ConsentTermDTO;
import dev.uffs.doisag.dto.ResearchConsentDTO;
import dev.uffs.doisag.dto.SessionUserDTO;
import dev.uffs.doisag.dto.TermAcceptanceDTO;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.service.ConsentTermService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// termo de consentimento do cadastro (RF36) e o de pesquisa (RF37)
@RestController
@RequestMapping("/consent-term")
public class ConsentTermController {

    private final ConsentTermService consentTermService;

    public ConsentTermController(ConsentTermService consentTermService) {
        this.consentTermService = consentTermService;
    }

    // rota publica pq a pessoa le o termo antes de ter conta
    @GetMapping
    public ConsentTermDTO getCurrentTerm() {
        return consentTermService.getCurrentTerm();
    }

    // paciente q aceitou uma versao antiga aceita a atual (#58)
    @PreAuthorize("hasRole('PATIENT')")
    @PostMapping("/accept")
    public SessionUserDTO acceptNewVersion(@RequestBody @Valid TermAcceptanceDTO acceptanceData,
                                           @AuthenticationPrincipal Patient loggedPatient) {
        consentTermService.acceptNewVersion(loggedPatient, acceptanceData.termVersion());
        return new SessionUserDTO(loggedPatient);
    }

    // publica tbm, o cadastro mostra o texto antes da conta existir
    @GetMapping("/research")
    public ConsentTermDTO getResearchTerm() {
        return consentTermService.getResearchTerm();
    }

    @PreAuthorize("hasRole('PATIENT')")
    @PostMapping("/research/accept")
    public ResearchConsentDTO acceptResearch(@RequestBody @Valid TermAcceptanceDTO acceptanceData,
                                             @AuthenticationPrincipal Patient loggedPatient) {
        consentTermService.acceptResearch(loggedPatient, acceptanceData.termVersion());
        return consentTermService.researchConsentOf(loggedPatient.getId());
    }

    @PreAuthorize("hasRole('PATIENT')")
    @PostMapping("/research/revoke")
    public ResearchConsentDTO revokeResearch(@AuthenticationPrincipal Patient loggedPatient) {
        consentTermService.revokeResearch(loggedPatient);
        return consentTermService.researchConsentOf(loggedPatient.getId());
    }
}
