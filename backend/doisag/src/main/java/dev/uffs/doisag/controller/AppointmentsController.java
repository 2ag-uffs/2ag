package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.AppointmentCreateDTO;
import dev.uffs.doisag.dto.AppointmentResponseDTO;
import dev.uffs.doisag.dto.BusySlotDTO;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import dev.uffs.doisag.service.AppointmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/consulta")

public class AppointmentsController {

    private final AppointmentService appointmentService;

    public AppointmentsController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    // create. vale pros dois papeis: o prescritor marca pra paciente da
    // carteira dele (RF11) e o paciente marca pra si (RF10). o canAccess
    // ja cobre os dois casos, entao n tem mais hasRole aqui.
    // o loggedPrescriber vem nulo quando quem chama eh paciente, e ai o
    // prescritor da consulta sai do vinculo dele
    @PreAuthorize("@patientAccess.canAccess(#dados.patientId(), authentication)")
    @PostMapping
    public AppointmentResponseDTO create(@RequestBody @Valid AppointmentCreateDTO dados,
                                         @AuthenticationPrincipal Prescriber loggedPrescriber) {
        return new AppointmentResponseDTO(appointmentService.create(dados, loggedPrescriber));
    }

    // os horarios ocupados do prescritor do paciente logado, pra ele
    // escolher um horario livre. devolve so os intervalos, sem nome de
    // ninguem: o paciente n pode descobrir quem mais se consulta ali
    @PreAuthorize("hasRole('PATIENT')")
    @GetMapping("/disponibilidade")
    public List<BusySlotDTO> disponibilidade(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @AuthenticationPrincipal Patient loggedPatient) {
        if (loggedPatient.getPrescriber() == null) {
            return List.of();
        }
        return appointmentService.getHorariosOcupados(loggedPatient.getPrescriber().getId(), data);
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
}
