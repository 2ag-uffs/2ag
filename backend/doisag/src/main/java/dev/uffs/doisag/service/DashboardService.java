package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.NotificationDTO;
import dev.uffs.doisag.dto.PatientDashboardDTO;
import dev.uffs.doisag.dto.PrescriberDashboardDTO;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.PrescriptionStatus;
import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescription;
import dev.uffs.doisag.model.ScaleTask;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.NotificationRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriptionRepository;
import dev.uffs.doisag.repository.ScaleTaskRepository;
import org.springframework.data.domain.PageRequest;
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
    private static final PageRequest PANEL_PAGE = PageRequest.of(0, PANEL_LIMIT);

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final ScaleTaskRepository taskRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final NotificationRepository notificationRepository;
    private final AuditService auditService;

    public DashboardService(PatientRepository patientRepository,
                            AppointmentRepository appointmentRepository,
                            ScaleTaskRepository taskRepository,
                            PrescriptionRepository prescriptionRepository,
                            NotificationRepository notificationRepository,
                            AuditService auditService) {
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.taskRepository = taskRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.notificationRepository = notificationRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PrescriberDashboardDTO getPrescriberDashboard(Long prescriberId) {
        long activePatients = patientRepository.countByPrescriberIdAndArchivedAtIsNull(prescriberId);
        long unreadNotifications = notificationRepository.countUnread(prescriberId);
        LocalDate today = LocalDate.now();

        List<PrescriberDashboardDTO.TodayAppointmentDTO> todaysAppointments = appointmentRepository
                .findByPrescriberIdAndDateTimeBetween(prescriberId, today.atStartOfDay(), today.atTime(LocalTime.MAX))
                .stream()
                // pedido sem resposta e consulta cancelada recusada ou anulada n contam como consulta do dia
                .filter(appointment -> appointment.getStatus().isConfirmed() && !appointment.isAnnulled())
                .sorted((first, second) -> first.getDateTime().compareTo(second.getDateTime()))
                .map(this::todayAppointmentOf)
                .toList();

        // os pedidos q vieram da agenda e ainda esperam resposta (RF11)
        LocalDateTime now = LocalDateTime.now();
        List<PrescriberDashboardDTO.WaitingRequestDTO> waitingRequests = appointmentRepository
                .findByPrescriberIdAndStatusAndDateTimeAfterOrderByDateTimeAsc(
                        prescriberId, AppointmentStatus.SOLICITADA, now, PANEL_PAGE)
                .stream()
                .map(this::waitingRequestOf)
                .toList();
        // a lista para em cinco, o numero do cartao n
        long waitingRequestsTotal = appointmentRepository.countByPrescriberIdAndStatusAndDateTimeAfter(
                prescriberId, AppointmentStatus.SOLICITADA, now);

        // as escalas q passaram do prazo e ninguem respondeu (RF32)
        List<ScaleTaskStatus> lateStatuses = List.of(ScaleTaskStatus.PENDENTE, ScaleTaskStatus.NAO_RESPONDIDA);
        List<PrescriberDashboardDTO.LateScaleDTO> lateScales = taskRepository
                .findLateWithoutAnswers(prescriberId, lateStatuses, today, PANEL_PAGE)
                .stream()
                .map(this::lateScaleOf)
                .toList();
        long lateScalesTotal = taskRepository.countLateWithoutAnswers(prescriberId, lateStatuses, today);

        return new PrescriberDashboardDTO(activePatients, unreadNotifications, todaysAppointments, waitingRequests,
                lateScales, waitingRequestsTotal, lateScalesTotal);
    }

    @Transactional
    public PatientDashboardDTO getPatientDashboard(Long patientId, Users loggedUser) {
        auditService.recordChartView(patientId);
        LocalDate today = LocalDate.now();

        // o pedido esperando resposta tambem eh proxima consulta: o horario
        // ja esta segurado pro paciente (RF10)
        //
        // esse corte fica na memoria mesmo pq quem entra depende de regra do enum,
        // e a lista eh so o futuro de um paciente
        List<PatientDashboardDTO.UpcomingAppointmentDTO> upcomingAppointments = appointmentRepository
                .findByPatientIdAndDateTimeAfterOrderByDateTimeAsc(patientId, LocalDateTime.now())
                .stream()
                .filter(appointment -> appointment.getStatus().holdsTimeSlot() && !appointment.isAnnulled())
                .limit(PANEL_LIMIT)
                .map(this::upcomingAppointmentOf)
                .toList();

        List<PatientDashboardDTO.PendingScaleDTO> pendingScales = taskRepository
                .findByPatientIdAndStatusOrderByPeriodEndAsc(patientId, ScaleTaskStatus.PENDENTE, PANEL_PAGE)
                .stream()
                .map(task -> pendingScaleOf(task, today))
                .toList();
        long pendingScalesTotal = taskRepository.countByPatientIdAndStatus(patientId, ScaleTaskStatus.PENDENTE);

        PatientDashboardDTO.CurrentPrescriptionDTO currentPrescription = prescriptionRepository
                .findByAppointmentPatientIdAndStatusAndAnnulmentAnnulledAtIsNull(patientId, PrescriptionStatus.VIGENTE)
                .stream()
                .findFirst()
                .map(this::currentPrescriptionOf)
                .orElse(null);

        // os avisos sao da caixa pessoal do paciente e o prescritor n le a caixa dele
        List<NotificationDTO> latestNotifications = List.of();
        long unreadNotifications = 0;
        if (loggedUser instanceof Patient) {
            latestNotifications = notificationRepository
                    .findByUserIdAndIsReadFalseOrderByCreatedAtDesc(patientId, PANEL_PAGE)
                    .stream()
                    .map(NotificationDTO::new)
                    .toList();
            unreadNotifications = notificationRepository.countUnread(patientId);
        }

        return new PatientDashboardDTO(upcomingAppointments, pendingScales, currentPrescription, latestNotifications,
                pendingScalesTotal, unreadNotifications);
    }

    private PrescriberDashboardDTO.TodayAppointmentDTO todayAppointmentOf(Appointment appointment) {
        return new PrescriberDashboardDTO.TodayAppointmentDTO(
                appointment.getId(),
                appointment.getPatient().getId(),
                appointment.getPatient().getDisplayName(),
                appointment.getDateTime(),
                appointment.getModality());
    }

    private PrescriberDashboardDTO.WaitingRequestDTO waitingRequestOf(Appointment appointment) {
        return new PrescriberDashboardDTO.WaitingRequestDTO(
                appointment.getId(),
                appointment.getPatient().getId(),
                appointment.getPatient().getDisplayName(),
                appointment.getDateTime(),
                appointment.getModality(),
                appointment.getPatientNote());
    }

    private PrescriberDashboardDTO.LateScaleDTO lateScaleOf(ScaleTask task) {
        return new PrescriberDashboardDTO.LateScaleDTO(
                task.getId(),
                task.getPatient().getId(),
                task.getPatient().getDisplayName(),
                task.getScaleType().getDisplayName(),
                task.getPeriodEnd());
    }

    private PatientDashboardDTO.UpcomingAppointmentDTO upcomingAppointmentOf(Appointment appointment) {
        return new PatientDashboardDTO.UpcomingAppointmentDTO(
                appointment.getId(),
                appointment.getDateTime(),
                appointment.getPrescriber() == null ? null : appointment.getPrescriber().getName(),
                appointment.getModality(),
                appointment.getStatus());
    }

    private PatientDashboardDTO.PendingScaleDTO pendingScaleOf(ScaleTask task, LocalDate today) {
        return new PatientDashboardDTO.PendingScaleDTO(
                task.getId(),
                task.getScaleType().getDisplayName(),
                task.getPeriodEnd(),
                task.getPeriodEnd().isBefore(today),
                task.getScaleType().getPath());
    }

    private PatientDashboardDTO.CurrentPrescriptionDTO currentPrescriptionOf(Prescription prescription) {
        return new PatientDashboardDTO.CurrentPrescriptionDTO(
                prescription.getId(),
                prescription.getProductDescription(),
                prescription.getPosology(),
                prescription.getAppointment().getDateTime().toLocalDate());
    }
}
