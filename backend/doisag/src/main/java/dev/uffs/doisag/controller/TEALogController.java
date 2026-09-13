package dev.uffs.doisag.controller;

import dev.uffs.doisag.model.TEALog;
import dev.uffs.doisag.service.TEALogService;
import dev.uffs.doisag.model.Patient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/registro-tea")
public class TEALogController {
    private final TEALogService teaLogService;

    public TEALogController(TEALogService teaLogService) {
        this.teaLogService = teaLogService;
    }

    // endpoint para CRIAR um novo registro de tea
    // POST /registro-tea
    @PreAuthorize("hasRole('PATIENT')")
    @PostMapping
    public TEALog create(@RequestBody TEALog teaLog, @AuthenticationPrincipal Patient loggedPatient) {
        // o dono do registro eh quem preencheu, n o id que veio no
        // corpo. antes dava pra atribuir escala a outro paciente
        teaLog.setId(null);
        teaLog.setPatient(loggedPatient);
        return teaLogService.create(teaLog);
    }

    // endpoint para LER todos os registros de tea
    // GET /registro-tea
    @PreAuthorize("hasRole('PRESCRIBER')")
    @GetMapping
    public List<TEALog> getAll() {
        return teaLogService.getAll();
    }

    // endpoint para LER um registro de tea por ID
    // GET /registro-tea/{id}
    @PreAuthorize("@assessmentAccess.canAccess('REGISTRO_TEA', #id, authentication)")
    @GetMapping("/{id}")
    public ResponseEntity<TEALog> getById(@PathVariable Long id) {
        TEALog teaLog = teaLogService.getById(id);
        return ResponseEntity.ok(teaLog);
    }

    // endpoint para ATUALIZAR um registro de tea
    // PUT /registro-tea/{id}
    @PreAuthorize("@assessmentAccess.canAccess('REGISTRO_TEA', #id, authentication)")
    @PutMapping("/{id}")
    public ResponseEntity<TEALog> update(@PathVariable Long id, @RequestBody TEALog logDetails) {
            TEALog updatedLog = teaLogService.update(id, logDetails);
            return ResponseEntity.ok(updatedLog);
    }
}