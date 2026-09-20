package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.AppointmentDeclineDTO;
import dev.uffs.doisag.dto.AppointmentMarkerDTO;
import dev.uffs.doisag.dto.AppointmentRequestDTO;
import dev.uffs.doisag.dto.AppointmentRescheduleDTO;
import dev.uffs.doisag.dto.AppointmentScheduleDTO;
import dev.uffs.doisag.dto.TimeSlotDTO;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.enums.TimePeriod;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.PrescriberAvailability;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberAvailabilityRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

// agenda de consultas (RF10 e RF11)
// o paciente pede um horario livre da agenda do prescritor dele e o pedido segura o horario ate a resposta
// o prescritor confirma recusa marca remarca e cancela
// o paciente cancela o pedido a qualquer hora e a consulta marcada so com 24 horas de antecedencia
@Service
public class AppointmentService {

    public static final String PAST_DATE_MESSAGE = "Não é possível marcar consulta em um horário que já passou";
    public static final String SLOT_TAKEN_MESSAGE = "Já existe consulta marcada nesse horário";
    public static final String SLOT_NOT_FREE_MESSAGE = "Esse horário não está livre na agenda. Escolha outro";
    public static final String NO_PRESCRIBER_MESSAGE = "Você ainda não tem um prescritor vinculado";
    public static final String ARCHIVED_PATIENT_MESSAGE =
            "Seu acompanhamento está encerrado. Fale com a clínica para voltar a marcar consulta";
    public static final String INACTIVE_PRESCRIBER_MESSAGE =
            "Seu prescritor não está mais atendendo pelo sistema. Fale com a clínica";
    public static final String ALREADY_ANSWERED_MESSAGE = "Este pedido de consulta já foi respondido";
    public static final String CLOSED_APPOINTMENT_MESSAGE = "Esta consulta não pode mais ser alterada";
    public static final String ALREADY_CANCELED_MESSAGE = "Esta consulta já está cancelada";
    public static final String LATE_CANCELLATION_MESSAGE =
            "Faltam menos de 24 horas para a consulta. Para cancelar, fale com o seu prescritor";
    public static final String INVALID_RANGE_MESSAGE = "A data inicial precisa ser igual ou anterior à data final";
    public static final String INCOMPLETE_RANGE_MESSAGE = "Informe as duas datas do período, ou nenhuma das duas";
    public static final String RANGE_TOO_LONG_MESSAGE = "Escolha um intervalo de até 31 dias";

    // pedido em aberto segura o horario, entao poucos por vez pra agenda n travar
    private static final int MAX_OPEN_REQUESTS = 3;
    public static final String TOO_MANY_REQUESTS_MESSAGE =
            "Você já tem " + MAX_OPEN_REQUESTS + " pedidos esperando resposta. "
                    + "Espere a resposta ou cancele um deles antes de pedir outro horário";

    // antecedencia minima pro paciente cancelar sozinho a consulta marcada
    public static final int PATIENT_CANCELLATION_HOURS = 24;

    // o paciente ve no maximo um mes de horarios livres por vez
    public static final int MAX_FREE_SLOT_DAYS = 31;

    private static final String PATIENT_AGENDA_LINK = "/agendamento-consulta";
    private static final String PRESCRIBER_AGENDA_LINK = "/agendamento-prescritor";
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final PrescriberRepository prescriberRepository;
    private final PrescriberAvailabilityRepository availabilityRepository;
    private final AuditService auditService;
    private NotificationService notificationService;

    public AppointmentService(AppointmentRepository appointmentRepository, PatientRepository patientRepository,
                              PrescriberRepository prescriberRepository,
                              PrescriberAvailabilityRepository availabilityRepository, AuditService auditService) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.prescriberRepository = prescriberRepository;
        this.availabilityRepository = availabilityRepository;
        this.auditService = auditService;
    }

    // injetado pelo setter pra n formar ciclo na montagem dos servicos
    @Autowired
    public void setNotificationService(@Lazy NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // o prescritor marca direto pra paciente da carteira dele e a consulta ja nasce confirmada
    @Transactional
    public Appointment schedule(AppointmentScheduleDTO scheduleData, Long prescriberId) {
        Patient patient = findPatient(scheduleData.patientId());
        Prescriber prescriber = findPrescriber(prescriberId);
        int durationMinutes = scheduleData.durationMinutes() == null
                ? prescriber.getAppointmentDurationMinutes()
                : scheduleData.durationMinutes();

        checkNotInThePast(scheduleData.dateTime());
        checkSlotIsFree(prescriberId, scheduleData.dateTime(), durationMinutes, null);

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setPrescriber(prescriber);
        appointment.setDateTime(scheduleData.dateTime());
        appointment.setModality(scheduleData.modality());
        appointment.setDurationMinutes(durationMinutes);
        appointment.setStatus(AppointmentStatus.AGENDADA);

        Appointment savedAppointment = appointmentRepository.save(appointment);
        auditService.recordCreation(AuditRecordType.CONSULTA, savedAppointment.getId(), patient.getId());
        notificationService.createNotification(patient, "Consulta marcada",
                "Sua consulta com " + prescriber.getName() + " foi marcada para "
                        + formatDateTime(savedAppointment) + ".",
                "APPOINTMENT", PATIENT_AGENDA_LINK);
        return savedAppointment;
    }

    // o paciente pede um dos horarios livres da agenda do prescritor dele
    @Transactional
    public Appointment request(Long patientId, AppointmentRequestDTO requestData) {
        Patient patient = findPatient(patientId);
        // arquivado saiu do acompanhamento: o pedido dele seguraria horario de quem esta em tratamento
        if (patient.isArchived()) {
            throw new BusinessException(ARCHIVED_PATIENT_MESSAGE);
        }
        Prescriber prescriber = patient.getPrescriber();
        if (prescriber == null) {
            throw new BusinessException(NO_PRESCRIBER_MESSAGE);
        }
        // prescritor desativado n consegue mais entrar, entao ninguem responderia esse pedido
        if (!prescriber.isActive()) {
            throw new BusinessException(INACTIVE_PRESCRIBER_MESSAGE);
        }

        // cada pedido em aberto segura um horario q some da agenda dos outros pacientes
        long openRequests = appointmentRepository.countByPatientIdAndStatusAndDateTimeAfter(
                patientId, AppointmentStatus.SOLICITADA, LocalDateTime.now());
        if (openRequests >= MAX_OPEN_REQUESTS) {
            throw new BusinessException(TOO_MANY_REQUESTS_MESSAGE);
        }

        // so vale um dos horarios livres entao fora do atendimento ocupado ou passado n entra
        boolean isFreeSlot = getFreeSlots(prescriber.getId(), requestData.dateTime().toLocalDate())
                .stream()
                .anyMatch(slot -> slot.start().equals(requestData.dateTime()));
        if (!isFreeSlot) {
            throw new BusinessException(SLOT_NOT_FREE_MESSAGE);
        }

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setPrescriber(prescriber);
        appointment.setDateTime(requestData.dateTime());
        appointment.setModality(requestData.modality());
        appointment.setDurationMinutes(prescriber.getAppointmentDurationMinutes());
        appointment.setStatus(AppointmentStatus.SOLICITADA);
        appointment.setPatientNote(textOrNull(requestData.patientNote()));

        Appointment savedAppointment = appointmentRepository.save(appointment);
        auditService.recordCreation(AuditRecordType.CONSULTA, savedAppointment.getId(), patientId);
        notificationService.createNotification(prescriber, "Pedido de consulta",
                patient.getName() + " pediu uma consulta para " + formatDateTime(savedAppointment) + ".",
                "APPOINTMENT", PRESCRIBER_AGENDA_LINK);
        return savedAppointment;
    }

    @Transactional
    public Appointment confirm(Long appointmentId) {
        Appointment appointment = findAppointment(appointmentId);
        checkIsWaitingAnswer(appointment);
        // outro pedido do mesmo horario pode ter sido confirmado antes desse
        if (overlapsAny(appointment.getDateTime(), endOf(appointment), confirmedAppointmentsOnTheDayOf(appointment))) {
            throw new BusinessException(SLOT_TAKEN_MESSAGE);
        }

        appointment.setStatus(AppointmentStatus.AGENDADA);
        Appointment savedAppointment = saveChange(appointment);
        notificationService.createNotification(savedAppointment.getPatient(), "Consulta confirmada",
                "Sua consulta com " + savedAppointment.getPrescriber().getName() + " em "
                        + formatDateTime(savedAppointment) + " foi confirmada.",
                "APPOINTMENT", PATIENT_AGENDA_LINK);
        return savedAppointment;
    }

    // pedido q passou da data sem resposta n pode ficar em aberto pra sempre:
    // ele some das telas, o paciente fica esperando e o registro nunca fecha (RF11)
    @Transactional
    public int declineExpiredRequests(LocalDateTime moment) {
        List<Appointment> expired = appointmentRepository
                .findByStatusAndDateTimeBefore(AppointmentStatus.SOLICITADA, moment)
                .stream()
                .filter(appointment -> !appointment.isAnnulled())
                .toList();

        for (Appointment appointment : expired) {
            appointment.setStatus(AppointmentStatus.RECUSADA);
            Appointment savedAppointment = saveChange(appointment);
            notificationService.createNotification(savedAppointment.getPatient(), "Pedido de consulta sem resposta",
                    "Seu pedido de consulta para " + formatDateTime(savedAppointment)
                            + " passou da data sem resposta. Escolha outro horário na agenda.",
                    "ALERT", PATIENT_AGENDA_LINK);
        }
        return expired.size();
    }

    // o pedido recusado devolve o horario pra agenda
    @Transactional
    public Appointment decline(Long appointmentId, AppointmentDeclineDTO declineData) {
        Appointment appointment = findAppointment(appointmentId);
        checkIsWaitingAnswer(appointment);

        appointment.setStatus(AppointmentStatus.RECUSADA);
        Appointment savedAppointment = saveChange(appointment);

        String message = "Seu pedido de consulta para " + formatDateTime(savedAppointment)
                + " não foi aceito. Escolha outro horário na agenda.";
        String reason = declineData == null ? null : textOrNull(declineData.reason());
        if (reason != null) {
            message = message + " Motivo: " + reason;
        }
        notificationService.createNotification(savedAppointment.getPatient(), "Pedido de consulta recusado",
                message, "ALERT", PATIENT_AGENDA_LINK);
        return savedAppointment;
    }

    // remarcar muda quando e como a consulta acontece e avisa o paciente
    // o pedido remarcado pelo prescritor ja fica confirmado no horario novo
    @Transactional
    public Appointment reschedule(Long appointmentId, AppointmentRescheduleDTO rescheduleData) {
        Appointment appointment = findAppointment(appointmentId);
        boolean canBeMoved = appointment.getStatus() == AppointmentStatus.SOLICITADA
                || appointment.getStatus() == AppointmentStatus.AGENDADA;
        if (appointment.isAnnulled() || !canBeMoved) {
            throw new BusinessException(CLOSED_APPOINTMENT_MESSAGE);
        }

        int durationMinutes = rescheduleData.durationMinutes() == null
                ? appointment.getDurationMinutes()
                : rescheduleData.durationMinutes();
        checkNotInThePast(rescheduleData.dateTime());
        checkSlotIsFree(appointment.getPrescriber().getId(), rescheduleData.dateTime(), durationMinutes,
                appointment.getId());

        appointment.setDateTime(rescheduleData.dateTime());
        appointment.setModality(rescheduleData.modality());
        appointment.setDurationMinutes(durationMinutes);
        appointment.setStatus(AppointmentStatus.AGENDADA);
        // o horario mudou entao o lembrete precisa sair de novo pro horario novo
        appointment.setReminderSentAt(null);
        Appointment savedAppointment = saveChange(appointment);
        notificationService.createNotification(savedAppointment.getPatient(), "Consulta remarcada",
                "Sua consulta com " + savedAppointment.getPrescriber().getName() + " foi remarcada para "
                        + formatDateTime(savedAppointment) + ".",
                "APPOINTMENT", PATIENT_AGENDA_LINK);
        return savedAppointment;
    }

    // cancelar n apaga e a consulta continua no historico como cancelada
    // o pedido ainda sem resposta o paciente cancela a qualquer hora
    @Transactional
    public Appointment cancel(Long appointmentId, Users loggedUser) {
        Appointment appointment = findAppointment(appointmentId);
        if (appointment.getStatus() == AppointmentStatus.CANCELADA) {
            throw new BusinessException(ALREADY_CANCELED_MESSAGE);
        }
        if (appointment.isAnnulled() || !isOpen(appointment.getStatus())) {
            throw new BusinessException(CLOSED_APPOINTMENT_MESSAGE);
        }

        boolean canceledByPatient = loggedUser instanceof Patient;
        boolean isLateForThePatient = appointment.getStatus() != AppointmentStatus.SOLICITADA
                && appointment.getDateTime().isBefore(LocalDateTime.now().plusHours(PATIENT_CANCELLATION_HOURS));
        if (canceledByPatient && isLateForThePatient) {
            throw new BusinessException(LATE_CANCELLATION_MESSAGE);
        }

        appointment.setStatus(AppointmentStatus.CANCELADA);
        Appointment savedAppointment = saveChange(appointment);
        if (canceledByPatient) {
            notificationService.createNotification(savedAppointment.getPrescriber(), "Consulta cancelada pelo paciente",
                    savedAppointment.getPatient().getName() + " cancelou a consulta de "
                            + formatDateTime(savedAppointment) + ".",
                    "ALERT", PRESCRIBER_AGENDA_LINK);
        } else {
            notificationService.createNotification(savedAppointment.getPatient(), "Consulta cancelada",
                    "Sua consulta com " + savedAppointment.getPrescriber().getName() + " de "
                            + formatDateTime(savedAppointment) + " foi cancelada.",
                    "ALERT", PATIENT_AGENDA_LINK);
        }
        return savedAppointment;
    }

    // horarios livres de alguns dias na agenda do prescritor do paciente
    // a tela busca o mes inteiro de uma vez e mostra so os dias q tem horario
    @Transactional(readOnly = true)
    public List<TimeSlotDTO> getFreeSlotsForPatient(Long patientId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessException(INVALID_RANGE_MESSAGE);
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_FREE_SLOT_DAYS) {
            throw new BusinessException(RANGE_TOO_LONG_MESSAGE);
        }

        // agenda vazia pra paciente arquivado e pra prescritor desativado: oferecer
        // horario q ninguem vai atender so gera pedido parado na fila
        Patient patient = findPatient(patientId);
        Prescriber prescriber = patient.getPrescriber();
        if (patient.isArchived() || prescriber == null || !prescriber.isActive()) {
            return List.of();
        }

        List<TimeSlotDTO> freeSlots = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            freeSlots.addAll(getFreeSlots(prescriber.getId(), day));
        }
        return freeSlots;
    }

    // agenda do prescritor entre duas datas e sem as datas vem inteira
    @Transactional(readOnly = true)
    public List<Appointment> getAgenda(Long prescriberId, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return appointmentRepository.findByPrescriberIdOrderByDateTimeAsc(prescriberId);
        }
        // so um lado do periodo ficava sem filtro nenhum e devolvia a agenda inteira
        if (from == null || to == null) {
            throw new BusinessException(INCOMPLETE_RANGE_MESSAGE);
        }
        if (from.isAfter(to)) {
            throw new BusinessException(INVALID_RANGE_MESSAGE);
        }
        return appointmentRepository.findByPrescriberIdAndDateTimeBetweenOrderByDateTimeAsc(
                prescriberId, from.atStartOfDay(), to.atTime(LocalTime.MAX));
    }

    // pedidos de pacientes q ainda esperam a resposta do prescritor
    @Transactional(readOnly = true)
    public List<Appointment> getWaitingRequests(Long prescriberId) {
        return appointmentRepository.findByPrescriberIdAndStatusAndDateTimeAfterOrderByDateTimeAsc(
                prescriberId, AppointmentStatus.SOLICITADA, LocalDateTime.now());
    }

    // proximos pedidos e consultas do paciente com a situacao de cada um
    @Transactional(readOnly = true)
    public List<Appointment> getUpcomingForPatient(Long patientId) {
        return appointmentRepository.findByPatientIdAndDateTimeAfterOrderByDateTimeAsc(patientId, LocalDateTime.now());
    }

    // abrir a consulta conta como abrir o prontuario do paciente
    public Appointment getById(Long id) {
        Appointment appointment = findAppointment(id);
        auditService.recordChartView(appointment.getPatient().getId());
        return appointment;
    }

    // consultas de um paciente da mais recente pra mais antiga
    public List<Appointment> getByPatientId(Long patientId) {
        auditService.recordChartView(patientId);
        return appointmentRepository.findByPatientIdOrderByDateTimeDesc(patientId);
    }

    // as consultas de um paciente dentro da janela do grafico
    // entra so a consulta confirmada e n anulada pq o q n aconteceu n explica mudanca nenhuma
    public List<AppointmentMarkerDTO> getMarcadoresDoPaciente(Long patientId, TimePeriod period) {
        auditService.recordChartView(patientId);
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(period.getDays());

        return appointmentRepository
                .findByPatientIdAndDateTimeBetweenOrderByDateTimeAsc(
                        patientId, start.atStartOfDay(), today.atTime(LocalTime.MAX))
                .stream()
                // marcada n eh o mesmo q aconteceu: consulta q o paciente faltou fica
                // AGENDADA pra sempre e viraria marcador de atendimento no grafico
                .filter(appointment -> appointment.getStatus() == AppointmentStatus.CONCLUIDA)
                .filter(appointment -> !appointment.isAnnulled())
                .map(AppointmentMarkerDTO::new)
                .toList();
    }

    // os periodos de atendimento do dia divididos pela duracao da consulta
    // sai o q ja passou e o q encosta em pedido ou consulta q segura o horario
    private List<TimeSlotDTO> getFreeSlots(Long prescriberId, LocalDate day) {
        Prescriber prescriber = findPrescriber(prescriberId);
        int durationMinutes = prescriber.getAppointmentDurationMinutes();
        List<PrescriberAvailability> periods = availabilityRepository
                .findByPrescriberIdAndDayOfWeekOrderByStartTimeAsc(prescriberId, day.getDayOfWeek().getValue());
        if (periods.isEmpty()) {
            return List.of();
        }

        List<Appointment> takenAppointments = appointmentsHoldingTimeOn(prescriberId, day, null);
        LocalDateTime now = LocalDateTime.now();
        List<TimeSlotDTO> freeSlots = new ArrayList<>();
        for (PrescriberAvailability period : periods) {
            LocalDateTime slotStart = day.atTime(period.getStartTime());
            LocalDateTime periodEnd = day.atTime(period.getEndTime());
            while (!slotStart.plusMinutes(durationMinutes).isAfter(periodEnd)) {
                LocalDateTime slotEnd = slotStart.plusMinutes(durationMinutes);
                if (slotStart.isAfter(now) && !overlapsAny(slotStart, slotEnd, takenAppointments)) {
                    freeSlots.add(new TimeSlotDTO(slotStart, slotEnd));
                }
                slotStart = slotEnd;
            }
        }
        return freeSlots;
    }

    private Appointment saveChange(Appointment appointment) {
        Appointment savedAppointment = appointmentRepository.save(appointment);
        auditService.recordChange(AuditRecordType.CONSULTA, savedAppointment.getId(),
                savedAppointment.getPatient().getId());
        return savedAppointment;
    }

    private void checkIsWaitingAnswer(Appointment appointment) {
        if (appointment.isAnnulled() || appointment.getStatus() != AppointmentStatus.SOLICITADA) {
            throw new BusinessException(ALREADY_ANSWERED_MESSAGE);
        }
    }

    // pedido esperando resposta e consulta marcada ou em andamento ainda podem ser canceladas
    private boolean isOpen(AppointmentStatus status) {
        return status == AppointmentStatus.SOLICITADA
                || status == AppointmentStatus.AGENDADA
                || status == AppointmentStatus.EM_ANDAMENTO;
    }

    private void checkNotInThePast(LocalDateTime dateTime) {
        if (dateTime.isBefore(LocalDateTime.now())) {
            throw new BusinessException(PAST_DATE_MESSAGE);
        }
    }

    // RN08 dois horarios do mesmo prescritor n se sobrepoem
    private void checkSlotIsFree(Long prescriberId, LocalDateTime start, int durationMinutes, Long ignoredAppointmentId) {
        List<Appointment> takenAppointments =
                appointmentsHoldingTimeOn(prescriberId, start.toLocalDate(), ignoredAppointmentId);
        if (overlapsAny(start, start.plusMinutes(durationMinutes), takenAppointments)) {
            throw new BusinessException(SLOT_TAKEN_MESSAGE);
        }
    }

    // pedidos e consultas do dia q seguram o horario
    private List<Appointment> appointmentsHoldingTimeOn(Long prescriberId, LocalDate day, Long ignoredAppointmentId) {
        return appointmentRepository
                .findByPrescriberIdAndDateTimeBetween(prescriberId, day.atStartOfDay(), day.atTime(LocalTime.MAX))
                .stream()
                .filter(appointment -> !appointment.getId().equals(ignoredAppointmentId))
                .filter(appointment -> appointment.getStatus().holdsTimeSlot())
                .filter(appointment -> !appointment.isAnnulled())
                .toList();
    }

    // consultas ja confirmadas no dia do pedido sem contar o proprio pedido
    private List<Appointment> confirmedAppointmentsOnTheDayOf(Appointment request) {
        return appointmentsHoldingTimeOn(request.getPrescriber().getId(), request.getDateTime().toLocalDate(),
                request.getId())
                .stream()
                .filter(appointment -> appointment.getStatus().isConfirmed())
                .toList();
    }

    private boolean overlapsAny(LocalDateTime start, LocalDateTime end, List<Appointment> appointments) {
        for (Appointment appointment : appointments) {
            if (start.isBefore(endOf(appointment)) && appointment.getDateTime().isBefore(end)) {
                return true;
            }
        }
        return false;
    }

    private LocalDateTime endOf(Appointment appointment) {
        return appointment.getDateTime().plusMinutes(appointment.getDurationMinutes());
    }

    private String formatDateTime(Appointment appointment) {
        return appointment.getDateTime().format(DATE_TIME_FORMAT);
    }

    private String textOrNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private Appointment findAppointment(Long appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Consulta não encontrada com o id: " + appointmentId));
    }

    private Patient findPatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado com o id: " + patientId));
    }

    private Prescriber findPrescriber(Long prescriberId) {
        return prescriberRepository.findById(prescriberId)
                .orElseThrow(() -> new NotFoundException("Prescritor não encontrado com o id: " + prescriberId));
    }
}
