package dev.uffs.doisag.controller;

import dev.uffs.doisag.model.FollowUp;
import dev.uffs.doisag.service.FollowUpService;
import dev.uffs.doisag.model.Patient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/acompanhamento")
public class FollowUpController {
    private final FollowUpService followUpService;

    public FollowUpController(FollowUpService followUpService) {
        this.followUpService = followUpService;
    }

    // endpoint para CRIAR um novo acompanhamento
    // POST /acompanhamento
    @PreAuthorize("hasRole('PATIENT')")
    @PostMapping
    public FollowUp create(@RequestBody FollowUp followUp, @AuthenticationPrincipal Patient loggedPatient) {
        // o dono do registro eh quem preencheu, n o id que veio no
        // corpo. antes dava pra atribuir escala a outro paciente
        followUp.setId(null);
        followUp.setPatient(loggedPatient);
        return followUpService.create(followUp);
    }

    // endpoint para LER um followup por ID
    // GET /acompanhamento/{id}
    @PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER') and @assessmentAccess.canAccess('ACOMPANHAMENTO_SEMANAL', #id, authentication)")
    @GetMapping("/{id}")
    public ResponseEntity<FollowUp> getById(@PathVariable Long id) {
        FollowUp followUp = followUpService.getById(id);
        return ResponseEntity.ok(followUp);
    }

    // endpoint para ATUALIZAR um followup
    // PUT /acompanhamento/{id}
    // so o paciente corrige o q ele mesmo respondeu
    @PreAuthorize("hasRole('PATIENT') and @assessmentAccess.canAccess('ACOMPANHAMENTO_SEMANAL', #id, authentication)")
    @PutMapping("/{id}")
    public ResponseEntity<FollowUp> update(@PathVariable Long id, @RequestBody FollowUp followUpDetails) {
            FollowUp updatedFollowUp = followUpService.update(id, followUpDetails);
            return ResponseEntity.ok(updatedFollowUp);
    }
}