package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.AnamnesisSummaryDTO;
import dev.uffs.doisag.dto.AppointmentResponseDTO;
import dev.uffs.doisag.dto.PrescriptionResponseDTO;
import dev.uffs.doisag.service.AnamnesisService;
import dev.uffs.doisag.service.AppointmentService;
import dev.uffs.doisag.service.PrescriptionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// historico clinico de um paciente (RF12 e RF13)
// toda lista ja sai filtrada pelo paciente e nenhuma traz o sistema inteiro
@RestController
@RequestMapping("/pacientes/{patientId}")
public class ClinicalHistoryController {

    private final AnamnesisService anamnesisService;
    private final AppointmentService appointmentService;
    private final PrescriptionService prescriptionService;

    public ClinicalHistoryController(AnamnesisService anamnesisService, AppointmentService appointmentService,
                                     PrescriptionService prescriptionService) {
        this.anamnesisService = anamnesisService;
        this.appointmentService = appointmentService;
        this.prescriptionService = prescriptionService;
    }

    @PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER') and @patientAccess.canAccess(#patientId, authentication)")
    @GetMapping("/anamneses")
    public List<AnamnesisSummaryDTO> getAnamneses(@PathVariable Long patientId) {
        return anamnesisService.getByPatientId(patientId)
                .stream()
                .map(AnamnesisSummaryDTO::new)
                .toList();
    }

    @PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER') and @patientAccess.canAccess(#patientId, authentication)")
    @GetMapping("/consultas")
    public List<AppointmentResponseDTO> getAppointments(@PathVariable Long patientId) {
        return appointmentService.getByPatientId(patientId)
                .stream()
                .map(AppointmentResponseDTO::new)
                .toList();
    }

    @PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER') and @patientAccess.canAccess(#patientId, authentication)")
    @GetMapping("/prescricoes")
    public List<PrescriptionResponseDTO> getPrescriptions(@PathVariable Long patientId) {
        return prescriptionService.getByPatientId(patientId)
                .stream()
                .map(PrescriptionResponseDTO::new)
                .toList();
    }
}
