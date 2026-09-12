package dev.uffs.doisag.controller;

import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.service.PrescriberService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/prescritor")
public class PrescribersController {
    private final PrescriberService prescriberService;

    public PrescribersController(PrescriberService prescriberService) {
        this.prescriberService = prescriberService;
    }

    // endpoint para CRIAR um novo prescritor
    // post /prescritor
    @PostMapping
    public Prescriber create(@RequestBody Prescriber prescriber) {
        return prescriberService.create(prescriber);
    }

    // endpoint para LER todos os prescritores
    // get /prescritor
    @GetMapping
    public List<Prescriber> getAll() {
        return prescriberService.getAll();
    }

    // endpoint para LER um prescritor por ID
    // get /prescritor/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Prescriber> getById(@PathVariable Long id) {
        Prescriber prescriber = prescriberService.getById(id);
        return ResponseEntity.ok(prescriber);
    }

    // endpoint para ATUALIZAR um prescritor
    // put /prescritor/{id}
    @PutMapping("/{id}")
    public ResponseEntity<Prescriber> update(@PathVariable Long id, @RequestBody Prescriber prescriberDetails) {
        Prescriber updatedPrescriber = prescriberService.update(id, prescriberDetails);
        return ResponseEntity.ok(updatedPrescriber);
    }

    // endpoint para DELETAR um prescritor
    // delete /prescritor/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
            prescriberService.delete(id);
            return ResponseEntity.noContent().build();
    }
}