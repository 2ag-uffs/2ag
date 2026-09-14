package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.AppointmentMarkerDTO;
import dev.uffs.doisag.dto.ProgressCommentDTO;
import dev.uffs.doisag.dto.ProgressDataPointDTO;
import dev.uffs.doisag.dto.TrackableAttributeDTO;
import dev.uffs.doisag.enums.TimePeriod;
import dev.uffs.doisag.enums.TrackableAttribute;
import dev.uffs.doisag.service.AppointmentService;
import dev.uffs.doisag.service.ProgressReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ProgressReportController {

    private final ProgressReportService progressReportService;
    private final AppointmentService appointmentService;

    public ProgressReportController(ProgressReportService progressReportService,
                                    AppointmentService appointmentService) {
        this.progressReportService = progressReportService;
        this.appointmentService = appointmentService;
    }

    // catalogo do que da pra acompanhar, agrupado por escala.
    // fica fora da rota de paciente pq n depende de nenhum: eh a lista
    // de possibilidades do sistema
    @PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER')")
    @GetMapping("/progresso/atributos")
    public List<TrackableAttributeDTO> getTrackableAttributes() {
        return progressReportService.getTrackableAttributes();
    }

    // endpoint que o front chama pra montar os graficos
    @PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER') and @patientAccess.canAccess(#patientId, authentication)")
    @GetMapping("/pacientes/{patientId}/progresso")
    public ResponseEntity<List<ProgressDataPointDTO>> getProgress(
            @PathVariable Long patientId,
            @RequestParam("atributo") TrackableAttribute attribute,
            @RequestParam("periodo") TimePeriod period
    ) {
        List<ProgressDataPointDTO> progressData =
                progressReportService.getPatientProgress(patientId, attribute, period);
        return ResponseEntity.ok(progressData);
    }

    // o q o paciente escreveu nas escalas do periodo (RF07)
    @PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER') and @patientAccess.canAccess(#patientId, authentication)")
    @GetMapping("/pacientes/{patientId}/progresso/comentarios")
    public ResponseEntity<List<ProgressCommentDTO>> getComments(
            @PathVariable Long patientId,
            @RequestParam("periodo") TimePeriod period
    ) {
        return ResponseEntity.ok(progressReportService.getPatientComments(patientId, period));
    }

    // as consultas do mesmo periodo, pra marcar no grafico em que dia o
    // paciente foi atendido. serve pra ler a curva junto com a conduta:
    // se o sintoma virou depois de uma consulta, da pra ver
    @PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER') and @patientAccess.canAccess(#patientId, authentication)")
    @GetMapping("/pacientes/{patientId}/progresso/consultas")
    public ResponseEntity<List<AppointmentMarkerDTO>> getAppointmentMarkers(
            @PathVariable Long patientId,
            @RequestParam("periodo") TimePeriod period
    ) {
        return ResponseEntity.ok(appointmentService.getMarcadoresDoPaciente(patientId, period));
    }
}
