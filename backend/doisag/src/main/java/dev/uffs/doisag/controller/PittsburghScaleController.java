package dev.uffs.doisag.controller;

import dev.uffs.doisag.model.PittsburghScale;
import dev.uffs.doisag.service.PittsburghScaleService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/escala-pittsburgh")
public class PittsburghScaleController {
    private final PittsburghScaleService pittsburghScaleService;

    public PittsburghScaleController(PittsburghScaleService pittsburghScaleService) {
        this.pittsburghScaleService = pittsburghScaleService;
    }

    // endpoint para CRIAR uma nova escala pittsburgh
    // POST /escala-pittsburgh
    @PreAuthorize("hasRole('PATIENT')")
    @PostMapping
    public PittsburghScale create(@RequestBody PittsburghScale pittsburghScale) {
        return pittsburghScaleService.create(pittsburghScale);
    }

    // endpoint para LER todas as escalas pittsburgh
    // GET /escala-pittsburgh
    @PreAuthorize("hasRole('PRESCRIBER')")
    @GetMapping
    public List<PittsburghScale> getAll() {
        return pittsburghScaleService.getAll();
    }

    // endpoint para LER uma escala pittsburgh por ID
    // GET /escala-pittsburgh/{id}
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<PittsburghScale> getById(@PathVariable Long id) {
        PittsburghScale pittsburghScale = pittsburghScaleService.getById(id);
        return ResponseEntity.ok(pittsburghScale);
    }

    // endpoint para ATUALIZAR uma escala pittsburgh
    // PUT /escala-pittsburgh/{id}
    @PreAuthorize("hasRole('PATIENT')")
    @PutMapping("/{id}")
    public ResponseEntity<PittsburghScale> update(@PathVariable Long id, @RequestBody PittsburghScale scaleDetails) {
            PittsburghScale updatedScale = pittsburghScaleService.update(id, scaleDetails);
            return ResponseEntity.ok(updatedScale);
    }

    // endpoint para DELETAR uma escala pittsburgh
    // DELETE /escala-pittsburgh/{id}
    @PreAuthorize("hasRole('PRESCRIBER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
            pittsburghScaleService.delete(id);
            return ResponseEntity.noContent().build();
    }
}