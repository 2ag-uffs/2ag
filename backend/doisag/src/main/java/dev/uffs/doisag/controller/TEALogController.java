package dev.uffs.doisag.controller;

import dev.uffs.doisag.model.TEALog;
import dev.uffs.doisag.service.TEALogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/registro-tea")
public class TEALogController {
    private final TEALogService teaLogService;

    public TEALogController(TEALogService teaLogService) {
        this.teaLogService = teaLogService;
    }

    // endpoint para CRIAR um novo registro de tea
    // POST /registro-tea
    @PostMapping
    public TEALog create(@RequestBody TEALog teaLog) {
        return teaLogService.create(teaLog);
    }

    // endpoint para LER todos os registros de tea
    // GET /registro-tea
    @GetMapping
    public List<TEALog> getAll() {
        return teaLogService.getAll();
    }

    // endpoint para LER um registro de tea por ID
    // GET /registro-tea/{id}
    @GetMapping("/{id}")
    public ResponseEntity<TEALog> getById(@PathVariable Long id) {
        TEALog teaLog = teaLogService.getById(id);
        return ResponseEntity.ok(teaLog);
    }

    // endpoint para ATUALIZAR um registro de tea
    // PUT /registro-tea/{id}
    @PutMapping("/{id}")
    public ResponseEntity<TEALog> update(@PathVariable Long id, @RequestBody TEALog logDetails) {
            TEALog updatedLog = teaLogService.update(id, logDetails);
            return ResponseEntity.ok(updatedLog);
    }

    // endpoint para DELETAR um registro de tea
    // DELETE /registro-tea/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
            teaLogService.delete(id);
            return ResponseEntity.noContent().build();
    }
}