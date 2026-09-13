package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.InviteCreatedDTO;
import dev.uffs.doisag.dto.InviteInfoDTO;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.service.PatientInviteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// convite pro paciente se cadastrar ja vinculado ao prescritor (RN06)
@RestController
@RequestMapping("/invites")
public class PatientInviteController {

    private final PatientInviteService patientInviteService;

    public PatientInviteController(PatientInviteService patientInviteService) {
        this.patientInviteService = patientInviteService;
    }

    // o prescritor gera um link novo pra mandar pro paciente
    @PreAuthorize("hasRole('PRESCRIBER')")
    @PostMapping
    public ResponseEntity<InviteCreatedDTO> createInvite(@AuthenticationPrincipal Users loggedUser) {
        InviteCreatedDTO createdInvite = patientInviteService.createInvite(loggedUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(createdInvite);
    }

    // rota publica q a tela de cadastro usa pra conferir o link antes de mostrar o formulario
    @GetMapping("/{token}")
    public InviteInfoDTO getInvite(@PathVariable String token) {
        return patientInviteService.getInviteInfo(token);
    }
}
