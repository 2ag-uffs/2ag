package dev.uffs.doisag.controller;

import dev.uffs.doisag.model.FollowUp;
import dev.uffs.doisag.service.FollowUpService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
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
    public FollowUp create(@RequestBody FollowUp followUp) {
        return followUpService.create(followUp);
    }

    // endpoint para LER todos os followups
    // GET /acompanhamento
    @PreAuthorize("hasRole('PRESCRIBER')")
    @GetMapping
    public List<FollowUp> getAll() {
        return followUpService.getAll();
    }

    // endpoint para LER um followup por ID
    // GET /acompanhamento/{id}
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<FollowUp> getById(@PathVariable Long id) {
        FollowUp followUp = followUpService.getById(id);
        return ResponseEntity.ok(followUp);
    }

    // endpoint para ATUALIZAR um followup
    // PUT /acompanhamento/{id}
    @PreAuthorize("hasRole('PATIENT')")
    @PutMapping("/{id}")
    public ResponseEntity<FollowUp> update(@PathVariable Long id, @RequestBody FollowUp followUpDetails) {
            FollowUp updatedFollowUp = followUpService.update(id, followUpDetails);
            return ResponseEntity.ok(updatedFollowUp);
    }

    // endpoint para DELETAR um followup
    // DELETE /acompanhamento/{id}
    @PreAuthorize("hasRole('PRESCRIBER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        followUpService.delete(id);
        return ResponseEntity.noContent().build();
    }
}