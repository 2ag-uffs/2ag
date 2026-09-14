package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.AgendaAppointmentDTO;
import dev.uffs.doisag.dto.AppointmentDeclineDTO;
import dev.uffs.doisag.dto.AppointmentRequestDTO;
import dev.uffs.doisag.dto.AppointmentRescheduleDTO;
import dev.uffs.doisag.dto.AppointmentResponseDTO;
import dev.uffs.doisag.dto.AppointmentScheduleDTO;
import dev.uffs.doisag.dto.TimeSlotDTO;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

// agenda de consultas (RF10 e RF11)
// o registro clinico do q aconteceu na consulta fica no ConsultationController
@RestController
@RequestMapping("/appointments")
public class AppointmentsController {

    private final AppointmentService appointmentService;

    public AppointmentsController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    // o prescritor marca consulta pra paciente da carteira dele
    @PreAuthorize("hasRole('PRESCRIBER') and @patientAccess.canAccess(#scheduleData.patientId(), authentication)")
    @PostMapping
    public ResponseEntity<AgendaAppointmentDTO> schedule(@RequestBody @Valid AppointmentScheduleDTO scheduleData,
                                                         @AuthenticationPrincipal Prescriber loggedPrescriber) {
        Appointment appointment = appointmentService.schedule(scheduleData, loggedPrescriber.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(new AgendaAppointmentDTO(appointment));
    }

    // o paciente pede um horario livre da agenda do prescritor dele
    // a rota eh separada pra ele nunca escrever campo clinico
    @PreAuthorize("hasRole('PATIENT')")
    @PostMapping("/requests")
    public ResponseEntity<AgendaAppointmentDTO> request(@RequestBody @Valid AppointmentRequestDTO requestData,
                                                        @AuthenticationPrincipal Patient loggedPatient) {
        Appointment appointment = appointmentService.request(loggedPatient.getId(), requestData);
        return ResponseEntity.status(HttpStatus.CREATED).body(new AgendaAppointmentDTO(appointment));
    }

    // horarios livres de alguns dias na agenda do prescritor do paciente logado
    // devolve so inicio e fim sem dizer quem ocupa o resto
    @PreAuthorize("hasRole('PATIENT')")
    @GetMapping("/free-slots")
    public List<TimeSlotDTO> getFreeSlots(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Patient loggedPatient) {
        return appointmentService.getFreeSlotsForPatient(loggedPatient.getId(), from, to);
    }

    // proximos pedidos e consultas do paciente logado
    @PreAuthorize("hasRole('PATIENT')")
    @GetMapping("/mine")
    public List<AgendaAppointmentDTO> getMyUpcomingAppointments(@AuthenticationPrincipal Patient loggedPatient) {
        return toAgendaList(appointmentService.getUpcomingForPatient(loggedPatient.getId()));
    }

    // agenda do prescritor logado entre duas datas e sem as datas vem inteira
    @PreAuthorize("hasRole('PRESCRIBER')")
    @GetMapping
    public List<AgendaAppointmentDTO> getAgenda(
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Prescriber loggedPrescriber) {
        return toAgendaList(appointmentService.getAgenda(loggedPrescriber.getId(), from, to));
    }

    // pedidos de pacientes esperando a resposta do prescritor logado
    @PreAuthorize("hasRole('PRESCRIBER')")
    @GetMapping("/requests")
    public List<AgendaAppointmentDTO> getWaitingRequests(@AuthenticationPrincipal Prescriber loggedPrescriber) {
        return toAgendaList(appointmentService.getWaitingRequests(loggedPrescriber.getId()));
    }

    // o vinculo com o paciente da consulta eh conferido antes de abrir
    @PreAuthorize("hasRole('PRESCRIBER') and @patientAccess.canAccessAppointment(#id, authentication)")
    @GetMapping("/{id}")
    public AppointmentResponseDTO getById(@PathVariable Long id) {
        return new AppointmentResponseDTO(appointmentService.getById(id));
    }

    @PreAuthorize("hasRole('PRESCRIBER') and @patientAccess.canAccessAppointment(#id, authentication)")
    @PutMapping("/{id}")
    public AgendaAppointmentDTO reschedule(@PathVariable Long id,
                                           @RequestBody @Valid AppointmentRescheduleDTO rescheduleData) {
        return new AgendaAppointmentDTO(appointmentService.reschedule(id, rescheduleData));
    }

    @PreAuthorize("hasRole('PRESCRIBER') and @patientAccess.canAccessAppointment(#id, authentication)")
    @PutMapping("/{id}/confirm")
    public AgendaAppointmentDTO confirm(@PathVariable Long id) {
        return new AgendaAppointmentDTO(appointmentService.confirm(id));
    }

    // o motivo da recusa eh opcional e vai no aviso pro paciente
    @PreAuthorize("hasRole('PRESCRIBER') and @patientAccess.canAccessAppointment(#id, authentication)")
    @PutMapping("/{id}/decline")
    public AgendaAppointmentDTO decline(@PathVariable Long id,
                                        @RequestBody(required = false) @Valid AppointmentDeclineDTO declineData) {
        return new AgendaAppointmentDTO(appointmentService.decline(id, declineData));
    }

    // o paciente e o prescritor cancelam e a regra das 24 horas vale so pro paciente
    @PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER') and @patientAccess.canAccessAppointment(#id, authentication)")
    @PutMapping("/{id}/cancel")
    public AgendaAppointmentDTO cancel(@PathVariable Long id, @AuthenticationPrincipal Users loggedUser) {
        return new AgendaAppointmentDTO(appointmentService.cancel(id, loggedUser));
    }

    private List<AgendaAppointmentDTO> toAgendaList(List<Appointment> appointments) {
        return appointments.stream()
                .map(AgendaAppointmentDTO::new)
                .toList();
    }
}
