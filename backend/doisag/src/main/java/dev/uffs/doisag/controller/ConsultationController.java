package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.AnnulmentDTO;
import dev.uffs.doisag.dto.AppointmentResponseDTO;
import dev.uffs.doisag.dto.ConsultationRecordDTO;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.service.ConsultationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// registro clinico da consulta (RF04)
// so o prescritor do paciente registra altera e anula
@RestController
public class ConsultationController {

    private final ConsultationService consultationService;

    public ConsultationController(ConsultationService consultationService) {
        this.consultationService = consultationService;
    }

    @PreAuthorize("hasRole('PRESCRIBER') and @patientAccess.canAccess(#patientId, authentication)")
    @PostMapping("/pacientes/{patientId}/consultas")
    public ResponseEntity<AppointmentResponseDTO> register(@PathVariable Long patientId,
                                                           @RequestBody @Valid ConsultationRecordDTO recordData,
                                                           @AuthenticationPrincipal Prescriber loggedPrescriber) {
        Appointment appointment = consultationService.register(patientId, recordData, loggedPrescriber);
        return ResponseEntity.status(HttpStatus.CREATED).body(new AppointmentResponseDTO(appointment));
    }

    @PreAuthorize("hasRole('PRESCRIBER') and @patientAccess.canAccessAppointment(#appointmentId, authentication)")
    @PutMapping("/consulta/{appointmentId}/registro-clinico")
    public AppointmentResponseDTO updateRecord(@PathVariable Long appointmentId,
                                               @RequestBody @Valid ConsultationRecordDTO recordData) {
        return new AppointmentResponseDTO(consultationService.updateRecord(appointmentId, recordData));
    }

    @PreAuthorize("hasRole('PRESCRIBER') and @patientAccess.canAccessAppointment(#appointmentId, authentication)")
    @PutMapping("/consulta/{appointmentId}/anulacao")
    public AppointmentResponseDTO annul(@PathVariable Long appointmentId,
                                        @RequestBody @Valid AnnulmentDTO annulmentData,
                                        @AuthenticationPrincipal Prescriber loggedPrescriber) {
        return new AppointmentResponseDTO(consultationService.annul(appointmentId, annulmentData, loggedPrescriber));
    }
}
