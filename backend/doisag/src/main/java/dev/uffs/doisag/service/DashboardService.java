package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.NotificationDTO;
import dev.uffs.doisag.dto.PatientDashboardDTO;
import dev.uffs.doisag.dto.PrescriberDashboardDTO;
import dev.uffs.doisag.enums.PrescriptionStatus;
import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Prescription;
import dev.uffs.doisag.model.ScaleTask;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.NotificationRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriptionRepository;
import dev.uffs.doisag.repository.ScaleResponseRepository;
import dev.uffs.doisag.repository.ScaleTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

// o painel de cada perfil (RF03)
//
// so entra o q tem dado de verdade atras. cartao sem origem confunde
// mais do q ajuda, entao o painel mostra o q o resto do sistema grava
@Service
public class DashboardService {

    // o painel eh um resumo, entao cada lista mostra so o comeco
    private static final int PANEL_LIMIT = 5;

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final ScaleTaskRepository taskRepository;
    private final ScaleResponseRepository responseRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final NotificationRepository notificationRepository;
    private final AuditService auditService;

    public DashboardService(PatientRepository patientRepository,
                            AppointmentRepository appointmentRepository,
                            ScaleTaskRepository taskRepository,
                            ScaleResponseRepository responseRepository,
                            PrescriptionRepository prescriptionRepository,
                            NotificationRepository notificationRepository,
                            AuditService auditService) {
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.taskRepository = taskRepository;
        this.responseRepository = responseRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.notificationRepository = notificationRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PrescriberDashboardDTO getPrescriberDashboard(Long prescriberId) {
        long activePatients = patientRepository.countByPrescriberIdAndArchivedAtIsNull(prescriberId);
        LocalDate today = LocalDate.now();

        List<PrescriberDashboardDTO.TodayAppointmentDTO> todaysAppointments = appointmentRepository
                .findByPrescriberIdAndDateTimeBetween(prescriberId, today.atStartOfDay(), today.atTime(LocalTime.MAX))
                .stream()
                // pedido sem resposta e consulta cancelada recusada ou anulada n contam como consulta do dia
                .filter(appointment -> appointment.getStatus().isConfirmed() && !appointment.isAnnulled())
                .sorted((first, second) -> first.getDateTime().compareTo(second.getDateTime()))
                .map(appointment -> new PrescriberDashboardDTO.TodayAppointmentDTO(
                        appointment.getId(),
                        appointment.getPatient().getId(),
                        appointment.getPatient().getName(),
                        appointment.getDateTime(),
                        appointment.getModality()))
                .toList();

        // os pedidos q vieram da agenda e ainda esperam resposta (RF11)
        List<PrescriberDashboardDTO.WaitingRequestDTO> waitingRequests = appointmentRepository
                .findByPrescriberIdAndStatusAndDateTimeAfterOrderByDateTimeAsc(
                        prescriberId, dev.uffs.doisag.enums.AppointmentStatus.SOLICITADA, LocalDateTime.now())
                .stream()
                .limit(PANEL_LIMIT)
                .map(this::waitingRequestOf)
                .toList();

        // as escalas q passaram do prazo, respondidas ou n (RF32)
        List<PrescriberDashboardDTO.LateScaleDTO> lateScales = taskRepository
                .findByPrescriberIdAndStatusInAndPeriodEndBeforeOrderByPeriodEndDesc(
                        prescriberId,
                        List.of(ScaleTaskStatus.PENDENTE, ScaleTaskStatus.NAO_RESPONDIDA),
                        today)
                .stream()
                .filter(task -> responseRepository.countByTaskId(task.getId()) == 0)
                .limit(PANEL_LIMIT)
                .map(this::lateScaleOf)
                .toList();

        return new PrescriberDashboardDTO(activePatients, todaysAppointments, waitingRequests, lateScales);
    }

    @Transactional(readOnly = true)
    public PatientDashboardDTO getPatientDashboard(Long patientId) {
        auditService.recordChartView(patientId);
        LocalDate today = LocalDate.now();

        // o pedido esperando resposta tambem eh proxima consulta: o horario
        // ja esta segurado pro paciente (RF10)
        List<PatientDashboardDTO.UpcomingAppointmentDTO> upcomingAppointments = appointmentRepository
                .findByPatientIdAndDateTimeAfterOrderByDateTimeAsc(patientId, LocalDateTime.now())
                .stream()
                .filter(appointment -> appointment.getStatus().holdsTimeSlot() && !appointment.isAnnulled())
                .limit(PANEL_LIMIT)
                .map(appointment -> new PatientDashboardDTO.UpcomingAppointmentDTO(
                        appointment.getId(),
                        appointment.getDateTime(),
                        appointment.getPrescriber() == null ? null : appointment.getPrescriber().getName(),
                        appointment.getModality(),
                        appointment.getStatus()))
                .toList();

        List<PatientDashboardDTO.PendingScaleDTO> pendingScales = taskRepository
                .findByPatientIdAndStatusOrderByPeriodEndAsc(patientId, ScaleTaskStatus.PENDENTE)
                .stream()
                .limit(PANEL_LIMIT)
                .map(task -> new PatientDashboardDTO.PendingScaleDTO(
                        task.getId(),
                        task.getScaleType().getDisplayName(),
                        task.getPeriodEnd(),
                        task.getPeriodEnd().isBefore(today),
                        task.getScaleType().getPath()))
                .toList();

        PatientDashboardDTO.CurrentPrescriptionDTO currentPrescription = prescriptionRepository
                .findByAppointmentPatientIdAndStatusAndAnnulmentAnnulledAtIsNull(patientId, PrescriptionStatus.VIGENTE)
                .stream()
                .findFirst()
                .map(this::currentPrescriptionOf)
                .orElse(null);

        List<NotificationDTO> latestNotifications = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(patientId)
                .stream()
                .filter(notification -> !notification.isRead())
                .limit(PANEL_LIMIT)
                .map(NotificationDTO::new)
                .toList();

        return new PatientDashboardDTO(upcomingAppointments, pendingScales, currentPrescription, latestNotifications);
    }

    private PrescriberDashboardDTO.WaitingRequestDTO waitingRequestOf(Appointment appointment) {
        return new PrescriberDashboardDTO.WaitingRequestDTO(
                appointment.getId(),
                appointment.getPatient().getId(),
                appointment.getPatient().getName(),
                appointment.getDateTime(),
                appointment.getModality(),
                appointment.getPatientNote());
    }

    private PrescriberDashboardDTO.LateScaleDTO lateScaleOf(ScaleTask task) {
        return new PrescriberDashboardDTO.LateScaleDTO(
                task.getId(),
                task.getPatient().getId(),
                task.getPatient().getName(),
                task.getScaleType().getDisplayName(),
                task.getPeriodEnd());
    }

    private PatientDashboardDTO.CurrentPrescriptionDTO currentPrescriptionOf(Prescription prescription) {
        return new PatientDashboardDTO.CurrentPrescriptionDTO(
                prescription.getId(),
                prescription.getProductDescription(),
                prescription.getPosology(),
                prescription.getAppointment().getDateTime().toLocalDate());
    }
}
