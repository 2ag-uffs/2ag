package dev.uffs.doisag.controller;

import dev.uffs.doisag.model.MentalStateExam;
import dev.uffs.doisag.service.MentalStateExamService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/mini-exame")
public class MentalStateExamController {
    private final MentalStateExamService mentalStateExamService;

    public MentalStateExamController(MentalStateExamService mentalStateExamService) {
        this.mentalStateExamService = mentalStateExamService;
    }

    // endpoint para CRIAR um novo exame
    // POST /mini-exame
    @PostMapping
    public MentalStateExam create(@RequestBody MentalStateExam mentalStateExam) {
        return mentalStateExamService.create(mentalStateExam);
    }

    // endpoint para LER todos os exames
    // GET /mini-exame
    @GetMapping
    public List<MentalStateExam> getAll() {
        return mentalStateExamService.getAll();
    }

    // endpoint para LER um exame por ID
    // GET /mini-exame/{id}
    @GetMapping("/{id}")
    public ResponseEntity<MentalStateExam> getById(@PathVariable Long id) {
        MentalStateExam mentalStateExam = mentalStateExamService.getById(id);
        return ResponseEntity.ok(mentalStateExam);
    }

    // endpoint para ATUALIZAR um exame
    // PUT /mini-exame/{id}
    @PutMapping("/{id}")
    public ResponseEntity<MentalStateExam> update(@PathVariable Long id, @RequestBody MentalStateExam examDetails) {
            MentalStateExam updatedExam = mentalStateExamService.update(id, examDetails);
            return ResponseEntity.ok(updatedExam);
    }

    // endpoint para DELETAR um exame
    // DELETE /mini-exame/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
            mentalStateExamService.delete(id);
            return ResponseEntity.noContent().build();
    }
}