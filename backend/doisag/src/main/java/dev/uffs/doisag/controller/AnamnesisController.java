package dev.uffs.doisag.controller;

import dev.uffs.doisag.model.Anamnesis;
import dev.uffs.doisag.service.AnamnesisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/anamnese")
public class AnamnesisController {
    private final AnamnesisService anamnesisService;

    public AnamnesisController(AnamnesisService anamnesisService) {
        this.anamnesisService = anamnesisService;
    }

    // endpoint para CRIAR uma nova anamnese
    // POST /anamnese
    @PostMapping
    public Anamnesis create(@RequestBody Anamnesis anamnesis) {
        return anamnesisService.create(anamnesis);
    }

    // endpoint para LER todas as anamneses
    // GET /anamnese
    @GetMapping
    public List<Anamnesis> getAll() {
        return anamnesisService.getAll();
    }

    // endpoint para LER uma anamnese por ID
    // GET /anamnese/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Anamnesis> getById(@PathVariable Long id) {
        Anamnesis anamnesis = anamnesisService.getById(id);
        return ResponseEntity.ok(anamnesis);
    }

    // endpoint para ATUALIZAR uma anamnese
    // PUT /anamnese/{id}
    @PutMapping("/{id}")
    public ResponseEntity<Anamnesis> update(@PathVariable Long id, @RequestBody Anamnesis anamnesisDetails) {
            Anamnesis updatedAnamnesis = anamnesisService.update(id, anamnesisDetails);
            return ResponseEntity.ok(updatedAnamnesis);
    }

    // endpoint para DELETAR uma anamnese
    // DELETE /anamnese/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
            anamnesisService.delete(id);
            return ResponseEntity.noContent().build();
    }
}