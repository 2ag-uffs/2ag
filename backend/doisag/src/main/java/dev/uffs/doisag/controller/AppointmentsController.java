package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.AppointmentCreateDTO;
import dev.uffs.doisag.dto.AppointmentResponseDTO;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Prescriber;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import dev.uffs.doisag.service.AppointmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/consulta")

public class AppointmentsController {

    private final AppointmentService appointmentService;

    public AppointmentsController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    // create
    @PreAuthorize("hasRole('PRESCRIBER') and @patientAccess.canAccess(#dados.patientId(), authentication)")
    @PostMapping
    public AppointmentResponseDTO create(@RequestBody @Valid AppointmentCreateDTO dados,
                                         @AuthenticationPrincipal Prescriber loggedPrescriber) {
        return new AppointmentResponseDTO(appointmentService.create(dados, loggedPrescriber));
    }

    // read all
    // so as consultas dos pacientes do prescritor logado.
    // antes devolvia as consultas do sistema inteiro
    @PreAuthorize("hasRole('PRESCRIBER')")
    @GetMapping
    public List<AppointmentResponseDTO> getMyAppointments(@AuthenticationPrincipal Prescriber loggedPrescriber) {
        return appointmentService.getByPrescriberId(loggedPrescriber.getId())
                .stream()
                .map(AppointmentResponseDTO::new)
                .toList();
    }

    // read by id
    @PreAuthorize("hasRole('PRESCRIBER')")
    @GetMapping("/{id}")
    public ResponseEntity<AppointmentResponseDTO> getById(@PathVariable Long id,
                                                          @AuthenticationPrincipal Prescriber loggedPrescriber) {
        Appointment appointment = appointmentService.getById(id);
        // consulta de paciente de outro prescritor n eh da conta dele
        if (!appointment.getPrescriber().getId().equals(loggedPrescriber.getId())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(new AppointmentResponseDTO(appointment));
    }

    // update
    @PreAuthorize("hasRole('PRESCRIBER')")
    @PutMapping("/{id}")
    public ResponseEntity<AppointmentResponseDTO> update(@PathVariable Long id,
                                                         @RequestBody @Valid AppointmentCreateDTO dados,
                                                         @AuthenticationPrincipal Prescriber loggedPrescriber) {
        Appointment atual = appointmentService.getById(id);
        if (!atual.getPrescriber().getId().equals(loggedPrescriber.getId())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(new AppointmentResponseDTO(appointmentService.update(id, dados)));
    }

    // cancelar. n eh delete de proposito: a consulta continua no
    // historico com status CANCELADA, e o paciente recebe o aviso
    @PreAuthorize("hasRole('PRESCRIBER')")
    @PutMapping("/{id}/cancelar")
    public ResponseEntity<AppointmentResponseDTO> cancel(@PathVariable Long id,
                                                         @AuthenticationPrincipal Prescriber loggedPrescriber) {
        Appointment atual = appointmentService.getById(id);
        if (!atual.getPrescriber().getId().equals(loggedPrescriber.getId())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(new AppointmentResponseDTO(appointmentService.cancel(id)));
    }

    // apaga de vez, pra consulta lancada por engano
    @PreAuthorize("hasRole('PRESCRIBER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @AuthenticationPrincipal Prescriber loggedPrescriber) {
        Appointment atual = appointmentService.getById(id);
        if (!atual.getPrescriber().getId().equals(loggedPrescriber.getId())) {
            return ResponseEntity.status(403).build();
        }
        appointmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
