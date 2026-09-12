package dev.uffs.doisag.controller;

import dev.uffs.doisag.model.PainLog;
import dev.uffs.doisag.service.PainLogService;
import dev.uffs.doisag.model.Patient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/registro-dor")
public class PainLogController {
    private final PainLogService painLogService;

    public PainLogController(PainLogService painLogService) {
        this.painLogService = painLogService;
    }

    // endpoint para CRIAR um novo registro de dor
    // POST /registro-dor
    @PreAuthorize("hasRole('PATIENT')")
    @PostMapping
    public PainLog create(@RequestBody PainLog painLog, @AuthenticationPrincipal Patient loggedPatient) {
        // o dono do registro eh quem preencheu, n o id que veio no
        // corpo. antes dava pra atribuir escala a outro paciente
        painLog.setId(null);
        painLog.setPatient(loggedPatient);
        return painLogService.create(painLog);
    }

    // endpoint para LER todos os registros de dor
    // GET /registro-dor
    @PreAuthorize("hasRole('PRESCRIBER')")
    @GetMapping
    public List<PainLog> getAll() {
        return painLogService.getAll();
    }

    // endpoint para LER um registro de dor por ID
    // GET /registro-dor/{id}
    @PreAuthorize("@assessmentAccess.canAccess('REGISTRO_DOR', #id, authentication)")
    @GetMapping("/{id}")
    public ResponseEntity<PainLog> getById(@PathVariable Long id) {
        PainLog painLog = painLogService.getById(id);
        return ResponseEntity.ok(painLog);
    }

    // endpoint para ATUALIZAR um registro de dor
    // PUT /registro-dor/{id}
    @PreAuthorize("@assessmentAccess.canAccess('REGISTRO_DOR', #id, authentication)")
    @PutMapping("/{id}")
    public ResponseEntity<PainLog> update(@PathVariable Long id, @RequestBody PainLog logDetails) {
            PainLog updatedLog = painLogService.update(id, logDetails);
            return ResponseEntity.ok(updatedLog);
    }

    // endpoint para DELETAR um registro de dor
    // DELETE /registro-dor/{id}
    @PreAuthorize("@assessmentAccess.canAccess('REGISTRO_DOR', #id, authentication)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
            painLogService.delete(id);
            return ResponseEntity.noContent().build();
    }
}