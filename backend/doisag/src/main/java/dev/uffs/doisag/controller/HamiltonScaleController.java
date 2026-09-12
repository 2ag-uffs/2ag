package dev.uffs.doisag.controller;

import dev.uffs.doisag.model.HamiltonScale;
import dev.uffs.doisag.service.HamiltonScaleService;
import dev.uffs.doisag.model.Patient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/escala-hamilton")
public class HamiltonScaleController {
    private final HamiltonScaleService hamiltonScaleService;

    public HamiltonScaleController(HamiltonScaleService hamiltonScaleService) {
        this.hamiltonScaleService = hamiltonScaleService;
    }

    // endpoint para CRIAR uma nova escala hamilton
    // POST /escala-hamilton
    @PreAuthorize("hasRole('PATIENT')")
    @PostMapping
    public HamiltonScale create(@RequestBody HamiltonScale hamiltonScale, @AuthenticationPrincipal Patient loggedPatient) {
        // o dono do registro eh quem preencheu, n o id que veio no
        // corpo. antes dava pra atribuir escala a outro paciente
        hamiltonScale.setId(null);
        hamiltonScale.setPatient(loggedPatient);
        return hamiltonScaleService.create(hamiltonScale);
    }

    // endpoint para LER todas as escalas hamilton
    // GET /escala-hamilton
    @PreAuthorize("hasRole('PRESCRIBER')")
    @GetMapping
    public List<HamiltonScale> getAll() {
        return hamiltonScaleService.getAll();
    }

    // endpoint para LER uma escala hamilton por ID
    // GET /escala-hamilton/{id}
    @PreAuthorize("@assessmentAccess.canAccess('ESCALA_HAMILTON', #id, authentication)")
    @GetMapping("/{id}")
    public ResponseEntity<HamiltonScale> getById(@PathVariable Long id) {
        HamiltonScale hamiltonScale = hamiltonScaleService.getById(id);
        return ResponseEntity.ok(hamiltonScale);
    }

    // endpoint para ATUALIZAR uma escala hamilton
    // PUT /escala-hamilton/{id}
    @PreAuthorize("@assessmentAccess.canAccess('ESCALA_HAMILTON', #id, authentication)")
    @PutMapping("/{id}")
    public ResponseEntity<HamiltonScale> update(@PathVariable Long id, @RequestBody HamiltonScale scaleDetails) {
            HamiltonScale updatedScale = hamiltonScaleService.update(id, scaleDetails);
            return ResponseEntity.ok(updatedScale);
    }

    // endpoint para DELETAR uma escala hamilton
    // DELETE /escala-hamilton/{id}
    @PreAuthorize("@assessmentAccess.canAccess('ESCALA_HAMILTON', #id, authentication)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
            hamiltonScaleService.delete(id);
            return ResponseEntity.noContent().build();
    }
}