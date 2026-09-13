package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.MentalStateExamCreateDTO;
import dev.uffs.doisag.model.MentalStateExam;
import dev.uffs.doisag.service.MentalStateExamService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/mini-exame")
public class MentalStateExamController {
    private final MentalStateExamService mentalStateExamService;

    public MentalStateExamController(MentalStateExamService mentalStateExamService) {
        this.mentalStateExamService = mentalStateExamService;
    }

    // endpoint para CRIAR um novo exame
    // POST /mini-exame
    // o MEEM sai de dentro de uma consulta, igual a prescricao
    @PreAuthorize("hasRole('PRESCRIBER') and @patientAccess.canAccessAppointment(#appointmentId, authentication)")
    @PostMapping("/consulta/{appointmentId}")
    public MentalStateExam create(@PathVariable Long appointmentId,
                                  @RequestBody MentalStateExamCreateDTO dados) {
        return mentalStateExamService.create(dados, appointmentId);
    }

    // endpoint para LER um exame por ID
    // GET /mini-exame/{id}
    @PreAuthorize("hasRole('PRESCRIBER') and @patientAccess.canAccessMentalStateExam(#id, authentication)")
    @GetMapping("/{id}")
    public ResponseEntity<MentalStateExam> getById(@PathVariable Long id) {
        MentalStateExam mentalStateExam = mentalStateExamService.getById(id);
        return ResponseEntity.ok(mentalStateExam);
    }

    // endpoint para ATUALIZAR um exame
    // PUT /mini-exame/{id}
    @PreAuthorize("hasRole('PRESCRIBER') and @patientAccess.canAccessMentalStateExam(#id, authentication)")
    @PutMapping("/{id}")
    public ResponseEntity<MentalStateExam> update(@PathVariable Long id, @RequestBody MentalStateExam examDetails) {
            MentalStateExam updatedExam = mentalStateExamService.update(id, examDetails);
            return ResponseEntity.ok(updatedExam);
    }
}