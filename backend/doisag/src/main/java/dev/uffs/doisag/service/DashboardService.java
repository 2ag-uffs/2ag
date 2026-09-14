package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.PatientDashboardDTO;
import dev.uffs.doisag.dto.PrescriberDashboardDTO;
import dev.uffs.doisag.dto.ScaleTaskDTO;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PatientRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

// o painel de cada perfil, juntando o q vem da agenda e das escalas (RF03)
@Service
public class DashboardService {

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final ScaleTaskService scaleTaskService;
    private final AuditService auditService;

    public DashboardService(PatientRepository patientRepository,
                            AppointmentRepository appointmentRepository,
                            ScaleTaskService scaleTaskService,
                            AuditService auditService) {
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.scaleTaskService = scaleTaskService;
        this.auditService = auditService;
    }

    public PrescriberDashboardDTO getPrescriberDashboard(Long prescriberId) {
        long activePatients = patientRepository.countByPrescriberIdAndArchivedAtIsNull(prescriberId);

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);
        List<PrescriberDashboardDTO.AppointmentSummaryDTO> todaysAppointments = appointmentRepository
                .findByPrescriberIdAndDateTimeBetween(prescriberId, startOfDay, endOfDay)
                .stream()
                // pedido sem resposta e consulta cancelada recusada ou anulada n contam como consulta do dia
                .filter(apt -> apt.getStatus().isConfirmed() && !apt.isAnnulled())
                .map(apt -> new PrescriberDashboardDTO.AppointmentSummaryDTO(apt.getId(), apt.getPatient().getName(), apt.getModality()))
                .toList();

        // as escalas q ainda esperam resposta, com as atrasadas na frente
        List<PrescriberDashboardDTO.PendingFormSummaryDTO> pendingForms = scaleTaskService
                .getPendingOfPrescriber(prescriberId)
                .stream()
                .map(task -> new PrescriberDashboardDTO.PendingFormSummaryDTO(
                        task.id(),
                        task.patientName(),
                        task.late() ? task.scaleName() + " (atrasada)" : task.scaleName()))
                .toList();

        return new PrescriberDashboardDTO(
                activePatients,
                todaysAppointments.size(),
                pendingForms.size(),
                todaysAppointments,
                pendingForms
        );
    }

    public PatientDashboardDTO getPatientDashboard(Long patientId) {
        auditService.recordChartView(patientId);
        // as consultas futuras do paciente entram junto com o RF03
        List<PatientDashboardDTO.UpcomingAppointmentDTO> upcomingAppointments = Collections.emptyList();

        List<PatientDashboardDTO.PendingScaleDTO> pendingScales = scaleTaskService
                .getPatientScalesPage(patientId)
                .pending()
                .stream()
                .map(this::pendingScaleOf)
                .toList();

        return new PatientDashboardDTO(upcomingAppointments, pendingScales);
    }

    private PatientDashboardDTO.PendingScaleDTO pendingScaleOf(ScaleTaskDTO task) {
        return new PatientDashboardDTO.PendingScaleDTO(
                task.scaleName(),
                task.status().toString(),
                task.path());
    }
}
