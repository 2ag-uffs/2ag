package dev.uffs.doisag.controller;

import dev.uffs.doisag.model.SleepLog;
import dev.uffs.doisag.service.SleepLogService;
import dev.uffs.doisag.model.Patient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/registro-sono")
public class SleepLogController {
    private final SleepLogService sleepLogService;

    public SleepLogController(SleepLogService sleepLogService) {
        this.sleepLogService = sleepLogService;
    }

    // endpoint para CRIAR um novo registro de sono
    // POST /registro-sono
    @PreAuthorize("hasRole('PATIENT')")
    @PostMapping
    public SleepLog create(@RequestBody SleepLog sleepLog, @AuthenticationPrincipal Patient loggedPatient) {
        // o dono do registro eh quem preencheu, n o id que veio no
        // corpo. antes dava pra atribuir escala a outro paciente
        sleepLog.setId(null);
        sleepLog.setPatient(loggedPatient);
        return sleepLogService.create(sleepLog);
    }

    // endpoint para LER todos os registros de sono
    // GET /registro-sono
    @PreAuthorize("hasRole('PRESCRIBER')")
    @GetMapping
    public List<SleepLog> getAll() {
        return sleepLogService.getAll();
    }

    // endpoint para LER um registro de sono por ID
    // GET /registro-sono/{id}
    @PreAuthorize("@assessmentAccess.canAccess('REGISTRO_SONO', #id, authentication)")
    @GetMapping("/{id}")
    public ResponseEntity<SleepLog> getById(@PathVariable Long id) {
        SleepLog sleepLog = sleepLogService.getById(id);
        return ResponseEntity.ok(sleepLog);
    }

    // endpoint para ATUALIZAR um registro de sono
    // PUT /registro-sono/{id}
    @PreAuthorize("@assessmentAccess.canAccess('REGISTRO_SONO', #id, authentication)")
    @PutMapping("/{id}")
    public ResponseEntity<SleepLog> update(@PathVariable Long id, @RequestBody SleepLog logDetails) {
            SleepLog updatedLog = sleepLogService.update(id, logDetails);
            return ResponseEntity.ok(updatedLog);
    }
}