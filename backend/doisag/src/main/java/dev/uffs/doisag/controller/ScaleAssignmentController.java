package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.AssignScaleDTO;
import dev.uffs.doisag.service.ScaleAssignmentService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.net.URI;
import dev.uffs.doisag.dto.AssignedScaleResponseDTO;
import dev.uffs.doisag.dto.PatientScalesPageDTO;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/pacientes/{patientId}/escalas")
public class ScaleAssignmentController {

    private final ScaleAssignmentService scaleAssignmentService;

    public ScaleAssignmentController(ScaleAssignmentService scaleAssignmentService) {
        this.scaleAssignmentService = scaleAssignmentService;
    }

    // endpoint que o prescritor vai usar pra mandar uma escala pro paciente
    @PostMapping
    // resposta agora é o DTO q criei
    public ResponseEntity<AssignedScaleResponseDTO> assignScale(@PathVariable Long patientId, @RequestBody AssignScaleDTO assignScaleDTO) {
        // guarda o DTO retornado pelo service
        AssignedScaleResponseDTO newAssignmentDto = scaleAssignmentService.assignScaleToPatient(patientId, assignScaleDTO);

        // essa parte aqui é pra construir a url do novo recurso que foi criado, copiei da net
        // exemplinho: /pacientes/1/escalas/5 (se o id da nova tarefa for 5)
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(newAssignmentDto.id())
                .toUri();

        // retorna 201 created com a url no header e o DTO no corpo
        return ResponseEntity.created(location).body(newAssignmentDto);
    }

    @GetMapping
    // o tipo da resposta agora é uma lista do nosso DTO
    public ResponseEntity<List<AssignedScaleResponseDTO>> getAssignedScales(@PathVariable Long patientId) {
        List<AssignedScaleResponseDTO> scalesDto = scaleAssignmentService.getAssignedScalesForPatient(patientId);
        return ResponseEntity.ok(scalesDto);
    }

    @GetMapping("/central") // sub-path para ser mais específico
    public ResponseEntity<PatientScalesPageDTO> getPatientScalesPage(@PathVariable Long patientId) {
        PatientScalesPageDTO pageData = scaleAssignmentService.getPatientScalesPageData(patientId);
        return ResponseEntity.ok(pageData);
    }
}