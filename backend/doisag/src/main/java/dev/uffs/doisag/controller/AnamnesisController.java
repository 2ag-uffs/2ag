package dev.uffs.doisag.controller;

import dev.uffs.doisag.model.Anamnesis;
import dev.uffs.doisag.service.AnamnesisService;
import dev.uffs.doisag.model.Patient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/anamnese")
public class AnamnesisController {
    private final AnamnesisService anamnesisService;

    public AnamnesisController(AnamnesisService anamnesisService) {
        this.anamnesisService = anamnesisService;
    }

    // endpoint para CRIAR uma nova anamnese
    // POST /anamnese
    @PreAuthorize("hasRole('PATIENT')")
    @PostMapping
    public Anamnesis create(@RequestBody Anamnesis anamnesis, @AuthenticationPrincipal Patient loggedPatient) {
        // o dono do registro eh quem preencheu, n o id que veio no
        // corpo. antes dava pra atribuir escala a outro paciente
        anamnesis.setId(null);
        anamnesis.setPatient(loggedPatient);
        return anamnesisService.create(anamnesis);
    }

    // endpoint para LER todas as anamneses
    // GET /anamnese
    @PreAuthorize("hasRole('PRESCRIBER')")
    @GetMapping
    public List<Anamnesis> getAll() {
        return anamnesisService.getAll();
    }

    // endpoint para LER uma anamnese por ID
    // GET /anamnese/{id}
    @PreAuthorize("@assessmentAccess.canAccess('ANAMNESE', #id, authentication)")
    @GetMapping("/{id}")
    public ResponseEntity<Anamnesis> getById(@PathVariable Long id) {
        Anamnesis anamnesis = anamnesisService.getById(id);
        return ResponseEntity.ok(anamnesis);
    }

    // endpoint para ATUALIZAR uma anamnese
    // PUT /anamnese/{id}
    @PreAuthorize("@assessmentAccess.canAccess('ANAMNESE', #id, authentication)")
    @PutMapping("/{id}")
    public ResponseEntity<Anamnesis> update(@PathVariable Long id, @RequestBody Anamnesis anamnesisDetails) {
            Anamnesis updatedAnamnesis = anamnesisService.update(id, anamnesisDetails);
            return ResponseEntity.ok(updatedAnamnesis);
    }

    // endpoint para DELETAR uma anamnese
    // DELETE /anamnese/{id}
    @PreAuthorize("@assessmentAccess.canAccess('ANAMNESE', #id, authentication)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
            anamnesisService.delete(id);
            return ResponseEntity.noContent().build();
    }
}