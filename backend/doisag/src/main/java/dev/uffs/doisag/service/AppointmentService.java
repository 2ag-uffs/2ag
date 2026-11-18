package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.AgendaAppointmentDTO;
import dev.uffs.doisag.dto.AppointmentResponseDTO;
import dev.uffs.doisag.dto.AppointmentDeclineDTO;
import dev.uffs.doisag.dto.AppointmentMarkerDTO;
import dev.uffs.doisag.dto.AppointmentRequestDTO;
import dev.uffs.doisag.dto.AppointmentRescheduleDTO;
import dev.uffs.doisag.dto.AppointmentScheduleDTO;
import dev.uffs.doisag.dto.TimeSlotDTO;
import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.AppointmentType;
import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.enums.TimePeriod;
import dev.uffs.doisag.enums.UserRole;
import dev.uffs.doisag.infra.BrazilianFormat;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.DateCheck;
import dev.uffs.doisag.infra.InputCleaner;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.infra.RowLock;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Notification;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.PrescriberAvailability;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberAvailabilityRepository;
import dev.uffs.doisag.repository.PrescriberDayOffRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

// agenda de consultas (RF10 e RF11)
// o paciente pede um horario livre da agenda do prescritor dele e o pedido segura o horario ate a resposta
// ele pede com 24 horas de antecedencia e tem um pedido esperando resposta por vez: o novo substitui o antigo
// o prescritor confirma recusa marca remarca e cancela
// o paciente cancela o pedido a qualquer hora e a consulta marcada so com 24 horas de antecedencia
@Service
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

    public static final String PAST_DATE_MESSAGE = "Não é possível marcar consulta em um horário que já passou";
    public static final String SLOT_TAKEN_MESSAGE = "Já existe consulta marcada nesse horário";
    public static final String SLOT_NOT_FREE_MESSAGE = "Esse horário não está livre na agenda. Escolha outro";
    public static final String NO_PRESCRIBER_MESSAGE = "Você ainda não tem um prescritor vinculado";
    public static final String ARCHIVED_PATIENT_MESSAGE =
            "Seu acompanhamento está encerrado. Fale com a clínica para voltar a marcar consulta";
    public static final String ARCHIVED_SCHEDULE_MESSAGE =
            "Paciente arquivado não recebe consulta nova. Reative o paciente antes de marcar";
    public static final String INACTIVE_PRESCRIBER_MESSAGE =
            "Seu prescritor não está mais atendendo pelo sistema. Fale com a clínica";
    public static final String ALREADY_ANSWERED_MESSAGE = "Este pedido de consulta já foi respondido";
    public static final String EXPIRED_REQUEST_MESSAGE =
            "O horário deste pedido já passou. Recuse o pedido para o paciente escolher outro horário";
    public static final String CLOSED_APPOINTMENT_MESSAGE = "Esta consulta não pode mais ser alterada";
    public static final String ALREADY_CANCELED_MESSAGE = "Esta consulta já está cancelada";
    public static final String LATE_CANCELLATION_MESSAGE =
            "Faltam menos de 24 horas para a consulta. Para cancelar, fale com o seu prescritor";
    public static final String APPOINTMENT_OVER_MESSAGE =
            "Esta consulta já terminou e não pode mais ser cancelada. Marque a falta ou registre o atendimento";
    public static final String NOT_SCHEDULED_MESSAGE = "Só dá para marcar falta em consulta que estava agendada";
    public static final String APPOINTMENT_NOT_OVER_MESSAGE =
            "Espere o horário da consulta terminar para marcar falta";
    public static final String NO_SHOW_WITH_PRESCRIPTION_MESSAGE =
            "Esta consulta tem prescrição. Registre o atendimento ou anule a prescrição antes de marcar falta";
    public static final String NO_SHOW_WITH_EXAM_MESSAGE = "Esta consulta tem um Mini-Exame aplicado. "
            + "Registre o atendimento ou anule o exame antes de marcar falta";
    public static final String CANCEL_WITH_PRESCRIPTION_MESSAGE =
            "Esta consulta tem prescrição. Registre o atendimento ou anule a prescrição antes de cancelar a consulta";
    public static final String CANCEL_WITH_EXAM_MESSAGE = "Esta consulta tem um Mini-Exame aplicado. "
            + "Registre o atendimento ou anule o exame antes de cancelar a consulta";
    public static final String INVALID_RANGE_MESSAGE = DateCheck.INVALID_RANGE_MESSAGE;
    public static final String INCOMPLETE_RANGE_MESSAGE = DateCheck.INCOMPLETE_RANGE_MESSAGE;
    public static final String RANGE_TOO_LONG_MESSAGE = "Escolha um intervalo de até 31 dias";

    // o pedido esperando resposta segura o horario, entao o paciente tem um por vez e o pedido novo substitui o antigo
    public static final String SAME_SLOT_MESSAGE = "Este já é o horário do seu pedido";
    public static final String REQUEST_CHANGED_MESSAGE = "Seus pedidos de consulta mudaram enquanto você escolhia "
            + "o horário. Confira seus pedidos e consultas e, se ainda quiser outro horário, peça de novo";

    // antecedencia minima pro paciente cancelar sozinho a consulta marcada
    public static final int PATIENT_CANCELLATION_HOURS = 24;

    // o pedido do paciente tem a mesma antecedencia do cancelamento, pro prescritor ter tempo de responder
    public static final int MIN_REQUEST_HOURS = PATIENT_CANCELLATION_HOURS;
    public static final String TOO_SOON_MESSAGE =
            "Escolha um horário com pelo menos " + MIN_REQUEST_HOURS + " horas de antecedência";

    // o paciente ve no maximo um mes de horarios livres por vez
    public static final int MAX_FREE_SLOT_DAYS = 31;

    // o paciente pede horario so dos proximos 30 dias contando hoje, a tela e a api seguem o mesmo limite
    public static final int MAX_DAYS_AHEAD = 30;
    public static final String TOO_FAR_AHEAD_MESSAGE =
            "Escolha um horário dos próximos " + MAX_DAYS_AHEAD + " dias";

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final PrescriberRepository prescriberRepository;
    private final PrescriberAvailabilityRepository availabilityRepository;
    private final PrescriberDayOffRepository dayOffRepository;
    private final AuditService auditService;
    private final ConsultationService consultationService;
    private final ReminderService reminderService;
    private final RowLock rowLock;
    private final TransactionTemplate transactionTemplate;
    private NotificationService notificationService;

    public AppointmentService(AppointmentRepository appointmentRepository, PatientRepository patientRepository,
                              PrescriberRepository prescriberRepository,
                              PrescriberAvailabilityRepository availabilityRepository,
                              PrescriberDayOffRepository dayOffRepository, AuditService auditService,
                              ConsultationService consultationService, ReminderService reminderService,
                              RowLock rowLock, PlatformTransactionManager transactionManager) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.prescriberRepository = prescriberRepository;
        this.availabilityRepository = availabilityRepository;
        this.dayOffRepository = dayOffRepository;
        this.auditService = auditService;
        this.consultationService = consultationService;
        this.reminderService = reminderService;
        this.rowLock = rowLock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // injetado pelo setter pra n formar ciclo na montagem dos servicos
    @Autowired
    public void setNotificationService(@Lazy NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // o prescritor marca direto pra paciente da carteira dele e a consulta ja nasce confirmada
    // ele pode marcar fora dos periodos e em dia de folga, a agenda eh dele
    // paciente arquivado n recebe consulta nova, igual o pedido dele. a q ja estava marcada continua valendo
    @Transactional
    public AgendaAppointmentDTO schedule(AppointmentScheduleDTO scheduleData, Long prescriberId) {
        Patient patient = findPatient(scheduleData.patientId());
        if (patient.isArchived()) {
            throw new BusinessException(ARCHIVED_SCHEDULE_MESSAGE);
        }
        Prescriber prescriber = findPrescriber(prescriberId);
        AppointmentType type = scheduleData.appointmentType() == null
                ? typeFor(patient.getId())
                : scheduleData.appointmentType();
        // a duracao q o prescritor escolheu vale mais q a do tipo
        int durationMinutes = scheduleData.durationMinutes() == null
                ? durationOf(prescriber, type)
                : scheduleData.durationMinutes();

        checkNotInThePast(scheduleData.dateTime());
        checkSlotIsFree(prescriberId, scheduleData.dateTime(), durationMinutes, null);
        // o paciente vem dps do prescritor na ordem das travas: o arquivamento gravado enquanto a marcacao
        // esperava aparece aqui e a consulta nova eh recusada
        patientRepository.lockById(patient.getId());
        if (patientRepository.existsByIdAndArchivedAtIsNotNull(patient.getId())) {
            throw new BusinessException(ARCHIVED_SCHEDULE_MESSAGE);
        }

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setPrescriber(prescriber);
        appointment.setDateTime(scheduleData.dateTime());
        appointment.setModality(scheduleData.modality());
        appointment.setDurationMinutes(durationMinutes);
        appointment.setAppointmentType(type);
        appointment.setStatus(AppointmentStatus.AGENDADA);

        Appointment savedAppointment = appointmentRepository.save(appointment);
        auditService.recordCreation(AuditRecordType.CONSULTA, savedAppointment.getId(), patient.getId());
        notificationService.createNotification(patient, "Consulta marcada",
                "Sua consulta com " + prescriber.getName() + " foi marcada para "
                        + formatDateTime(savedAppointment) + ".",
                Notification.TYPE_APPOINTMENT, Notification.PATIENT_AGENDA_LINK);
        reminderService.remindNowIfTomorrow(savedAppointment);
        return new AgendaAppointmentDTO(savedAppointment);
    }

    // o paciente pede um dos horarios livres da agenda do prescritor dele, com 24 horas de antecedencia
    // ele tem um pedido esperando resposta por vez, entao o pedido novo substitui o q ainda esperava
    @Transactional
    public AgendaAppointmentDTO request(Long patientId, AppointmentRequestDTO requestData) {
        Patient patient = findPatient(patientId);
        checkPatientCanRequest(patient);
        Prescriber prescriber = patient.getPrescriber();
        LocalDateTime earliestStart = earliestRequestStart();
        if (requestData.dateTime().isBefore(earliestStart)) {
            throw new BusinessException(TOO_SOON_MESSAGE);
        }
        if (requestData.dateTime().toLocalDate().isAfter(lastDayToRequest())) {
            throw new BusinessException(TOO_FAR_AHEAD_MESSAGE);
        }

        // o pedido antigo vai ser cancelado e a consulta vem antes do prescritor na ordem das travas,
        // entao os pedidos em aberto do paciente sao travados e lidos de novo antes da agenda, igual no arquivamento
        List<Appointment> lockedRequests = lockOpenRequestsOf(patientId);
        // a agenda travada faz os pedidos do mesmo prescritor passarem um de cada vez
        lockAgendaOf(prescriber.getId());
        // o paciente vem dps do prescritor na ordem das travas. o arquivamento gravado enquanto o pedido esperava
        // aparece aqui e o pedido eh recusado, e o arquivamento q chega no meio espera o pedido gravar (#170)
        patientRepository.lockById(patientId);
        if (patientRepository.existsByIdAndArchivedAtIsNotNull(patientId)) {
            throw new BusinessException(ARCHIVED_PATIENT_MESSAGE);
        }
        // o pedido q outra aba gravou entre a leitura dos pedidos e a trava da agenda ficou fora da lista travada,
        // e travar ele agora quebrava a ordem. este eh recusado sem mexer em nada, senao ficavam dois esperando
        if (hasOpenRequestsBesides(patientId, lockedRequests)) {
            throw new BusinessException(REQUEST_CHANGED_MESSAGE);
        }
        List<Appointment> replacedRequests = stillOpen(lockedRequests);
        // a tela manda os pedidos q mostrou na troca. se eles mudaram desde q ela abriu (o prescritor respondeu ou
        // outra aba pediu), nada muda e o paciente confere a lista de novo
        checkScreenShowedTheseRequests(replacedRequests, requestData.replacedRequestIds());
        for (Appointment replaced : replacedRequests) {
            if (replaced.getDateTime().equals(requestData.dateTime())) {
                throw new BusinessException(SAME_SLOT_MESSAGE);
            }
        }

        // o paciente n escolhe o tipo pq ele sai do historico dele
        AppointmentType type = typeFor(patientId);
        int durationMinutes = durationOf(prescriber, type);
        // o horario dos pedidos q vao ser cancelados n segura a troca
        checkIsOfferedSlot(prescriber, requestData.dateTime(), durationMinutes, earliestStart, replacedRequests);

        // so dps de tudo conferido o antigo eh cancelado, como no cancelamento do paciente: libera o horario e
        // entra na trilha. o aviso pro prescritor vai junto com o do pedido novo
        for (Appointment replaced : replacedRequests) {
            replaced.setStatus(AppointmentStatus.CANCELADA);
            saveChange(replaced);
        }

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setPrescriber(prescriber);
        appointment.setDateTime(requestData.dateTime());
        appointment.setModality(requestData.modality());
        appointment.setDurationMinutes(durationMinutes);
        appointment.setAppointmentType(type);
        appointment.setStatus(AppointmentStatus.SOLICITADA);
        appointment.setPatientNote(InputCleaner.textOrNull(requestData.patientNote()));

        Appointment savedAppointment = appointmentRepository.save(appointment);
        auditService.recordCreation(AuditRecordType.CONSULTA, savedAppointment.getId(), patientId);
        notificationService.createNotification(prescriber, "Pedido de consulta",
                patient.getDisplayName() + " pediu uma consulta para " + formatDateTime(savedAppointment)
                        + replacementNote(replacedRequests) + ".",
                Notification.TYPE_APPOINTMENT, Notification.PRESCRIBER_AGENDA_LINK);
        return new AgendaAppointmentDTO(savedAppointment);
    }

    // o aviso do pedido novo diz qual pedido ele substituiu, pra n chegar um aviso de cancelamento separado
    // mais de um so com pedido de antes da regra de um por vez
    private String replacementNote(List<Appointment> replacedRequests) {
        if (replacedRequests.isEmpty()) {
            return "";
        }
        List<String> times = new ArrayList<>();
        for (Appointment replaced : replacedRequests) {
            times.add(formatDateTime(replaced));
        }
        if (times.size() == 1) {
            return " no lugar do pedido de " + times.get(0) + ", que foi cancelado";
        }
        return " no lugar dos pedidos de " + String.join(", ", times) + ", que foram cancelados";
    }

    @Transactional
    public AgendaAppointmentDTO confirm(Long appointmentId) {
        Appointment appointment = findAppointmentForUpdate(appointmentId);
        checkIsWaitingAnswer(appointment);
        // pedido q passou da hora espera a recusa, senao virava consulta no passado
        // e o paciente so ficava sabendo dps do horario
        if (appointment.getDateTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(EXPIRED_REQUEST_MESSAGE);
        }
        // outro pedido do mesmo horario pode ter sido confirmado antes desse
        lockAgendaOf(appointment.getPrescriber().getId());
        if (overlapsAny(appointment.getDateTime(), endOf(appointment), confirmedAppointmentsAround(appointment))) {
            throw new BusinessException(SLOT_TAKEN_MESSAGE);
        }

        appointment.setStatus(AppointmentStatus.AGENDADA);
        Appointment savedAppointment = saveChange(appointment);
        notificationService.createNotification(savedAppointment.getPatient(), "Consulta confirmada",
                "Sua consulta com " + savedAppointment.getPrescriber().getName() + " em "
                        + formatDateTime(savedAppointment) + " foi confirmada.",
                Notification.TYPE_APPOINTMENT, Notification.PATIENT_AGENDA_LINK);
        reminderService.remindNowIfTomorrow(savedAppointment);
        return new AgendaAppointmentDTO(savedAppointment);
    }

    // pedido q passou da data sem resposta n pode ficar em aberto pra sempre:
    // ele some das telas, o paciente fica esperando e o registro nunca fecha (RF11)
    // cada pedido na propria transacao, com a consulta travada e lida de novo antes de tudo, igual nas rotas:
    // o cancelamento ou a remarcacao q o prescritor grava no mesmo instante aparece e o pedido fica como ele
    // deixou. antes o job gravava a linha inteira lida antes, por cima, e no postgres a trilha dele tomava o
    // paciente antes da consulta, e ficava esperando o cancelamento q segurava a consulta e esperava o paciente
    // um pedido com problema n segura os outros (#110)
    public int declineExpiredRequests(LocalDateTime moment) {
        int declined = 0;
        int failures = 0;
        for (Appointment expired : appointmentRepository.findByStatusAndDateTimeBefore(AppointmentStatus.SOLICITADA,
                moment)) {
            try {
                Boolean wasDeclined = transactionTemplate.execute(
                        status -> declineIfStillExpired(expired.getId(), moment));
                if (Boolean.TRUE.equals(wasDeclined)) {
                    declined++;
                }
            } catch (RuntimeException exception) {
                failures++;
                log.error("Tarefas do dia: falha no pedido de consulta vencido {}", expired.getId(), exception);
            }
        }
        // o dia n conta como feito e a proxima rodada tenta de novo sem repetir o q ja saiu
        if (failures > 0) {
            throw new IllegalStateException(failures + " pedido(s) de consulta vencido(s) com falha, "
                    + declined + " recusados nos outros");
        }
        return declined;
    }

    private boolean declineIfStillExpired(Long appointmentId, LocalDateTime moment) {
        Appointment appointment = rowLock.reload(Appointment.class, appointmentId).orElse(null);
        if (appointment == null) {
            return false;
        }
        boolean stillExpired = !appointment.isAnnulled() && appointment.getStatus() == AppointmentStatus.SOLICITADA
                && appointment.getDateTime().isBefore(moment);
        if (!stillExpired) {
            return false;
        }
        // com a conta do prescritor desativada a agenda do paciente n mostra horario e o pedido novo eh recusado,
        // entao o aviso manda falar com a clinica. o estado da conta n aparece pro paciente
        // (decisao de 17/11/2026)
        String whatToDo = appointment.getPrescriber().isActive() ? "Escolha outro horário na agenda."
                : "Fale com a clínica para marcar.";
        refuse(appointment, "Pedido de consulta sem resposta",
                "Seu pedido de consulta para " + formatDateTime(appointment)
                        + " passou da data sem resposta. " + whatToDo);
        return true;
    }

    // o pedido de amanha q ainda n teve resposta avisa o paciente na vespera pra falar com a clinica, senao ele
    // so ficava sabendo dps q o horario passou (decisao de 14/11/2026, revisao das regras R1)
    // so o pedido pra amanha, como a decisao diz. o pedido continua esperando a resposta
    // um aviso so por pedido, e cada um na propria transacao com o pedido travado e lido de novo, igual na recusa
    public int warnUnansweredRequests(LocalDateTime moment) {
        LocalDate tomorrow = moment.toLocalDate().plusDays(1);
        LocalDateTime startOfTomorrow = tomorrow.atStartOfDay();
        LocalDateTime endOfTomorrow = tomorrow.atTime(LocalTime.MAX);
        int warned = 0;
        int failures = 0;
        for (Appointment found : appointmentRepository.findByStatusAndDateTimeBetweenAndUnansweredNoticeSentAtIsNull(
                AppointmentStatus.SOLICITADA, startOfTomorrow, endOfTomorrow)) {
            try {
                Boolean wasWarned = transactionTemplate.execute(
                        status -> warnIfStillUnanswered(found.getId(), startOfTomorrow, endOfTomorrow));
                if (Boolean.TRUE.equals(wasWarned)) {
                    warned++;
                }
            } catch (RuntimeException exception) {
                failures++;
                log.error("Tarefas do dia: falha no aviso do pedido de consulta sem resposta {}", found.getId(),
                        exception);
            }
        }
        if (failures > 0) {
            throw new IllegalStateException(failures + " aviso(s) de pedido sem resposta com falha, "
                    + warned + " enviados nos outros");
        }
        return warned;
    }

    private boolean warnIfStillUnanswered(Long appointmentId, LocalDateTime startOfTomorrow,
                                          LocalDateTime endOfTomorrow) {
        Appointment request = rowLock.reload(Appointment.class, appointmentId).orElse(null);
        if (request == null) {
            return false;
        }
        boolean stillWaiting = !request.isAnnulled() && request.getStatus() == AppointmentStatus.SOLICITADA
                && request.getUnansweredNoticeSentAt() == null
                && !request.getDateTime().isBefore(startOfTomorrow) && !request.getDateTime().isAfter(endOfTomorrow);
        if (!stillWaiting) {
            return false;
        }
        notificationService.createNotification(request.getPatient(), "Pedido ainda sem resposta",
                "Seu pedido de consulta para " + formatDateTime(request)
                        + " ainda não foi confirmado. Fale com a clínica.",
                Notification.TYPE_ALERT, Notification.PATIENT_AGENDA_LINK);
        request.setUnansweredNoticeSentAt(LocalDateTime.now());
        appointmentRepository.save(request);
        return true;
    }

    // o pedido recusado devolve o horario pra agenda
    @Transactional
    public AgendaAppointmentDTO decline(Long appointmentId, AppointmentDeclineDTO declineData) {
        Appointment appointment = findAppointmentForUpdate(appointmentId);
        checkIsWaitingAnswer(appointment);

        String message = "Seu pedido de consulta para " + formatDateTime(appointment)
                + " não foi aceito. Escolha outro horário na agenda.";
        String reason = declineData == null ? null : InputCleaner.textOrNull(declineData.reason());
        if (reason != null) {
            message = message + " Motivo: " + reason;
        }
        return new AgendaAppointmentDTO(refuse(appointment, "Pedido de consulta recusado", message));
    }

    // os pedidos em aberto do paciente, ja travados e lidos de novo, q o arquivamento recusa e o pedido novo cancela
    // a consulta vem antes do prescritor e do paciente na ordem das travas, entao os dois chamam isso antes
    // MANDATORY pq fora da transacao de quem chama a trava soltaria aqui mesmo
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Appointment> lockOpenRequestsOf(Long patientId) {
        List<Appointment> lockedRequests = new ArrayList<>();
        for (Appointment request : appointmentRepository.findByPatientIdAndStatusAndAnnulmentAnnulledAtIsNullOrderByIdAsc(
                patientId, AppointmentStatus.SOLICITADA)) {
            rowLock.reload(Appointment.class, request.getId()).ifPresent(lockedRequests::add);
        }
        return lockedRequests;
    }

    // o pedido gravado dps da leitura acima n esta na lista travada, e travar ele dps do paciente quebrava a ordem
    // o arquivamento e o pedido novo conferem isso ja com o paciente travado, qnd nenhum pedido novo entra mais
    // (o pedido trava ele)
    @Transactional(readOnly = true)
    public boolean hasOpenRequestsBesides(Long patientId, List<Appointment> lockedRequests) {
        long openNow = appointmentRepository.countByPatientIdAndStatusAndAnnulmentAnnulledAtIsNull(patientId,
                AppointmentStatus.SOLICITADA);
        return openNow > stillOpen(lockedRequests).size();
    }

    // cada pedido q a tela mostrou ainda espera resposta, e cada um q espera e ainda n venceu estava na tela
    // o vencido q o job ainda n recusou n aparece na tela e sai junto sem perguntar
    private void checkScreenShowedTheseRequests(List<Appointment> openRequests, List<Long> shownIds) {
        List<Long> screenIds = shownIds == null ? List.of() : shownIds;
        for (Long shownId : screenIds) {
            if (!hasAppointment(openRequests, shownId)) {
                throw new BusinessException(REQUEST_CHANGED_MESSAGE);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        for (Appointment request : openRequests) {
            if (request.getDateTime().isAfter(now) && !screenIds.contains(request.getId())) {
                throw new BusinessException(REQUEST_CHANGED_MESSAGE);
            }
        }
    }

    // os da lista travada q ainda esperam resposta: pode ter sido confirmado ou cancelado antes da trava
    private List<Appointment> stillOpen(List<Appointment> lockedRequests) {
        List<Appointment> openRequests = new ArrayList<>();
        for (Appointment request : lockedRequests) {
            if (!request.isAnnulled() && request.getStatus() == AppointmentStatus.SOLICITADA) {
                openRequests.add(request);
            }
        }
        return openRequests;
    }

    // o arquivamento recusa o pedido em aberto, q segurava horario de quem segue em tratamento (#170)
    // o mesmo caminho da recusa do prescritor, so q o aviso n manda escolher outro horario,
    // pq o arquivado n pede mais consulta pelo sistema
    @Transactional
    public int declineForArchiving(List<Appointment> openRequests) {
        int declined = 0;
        for (Appointment request : openRequests) {
            // lido de novo com a trava: pode ter sido confirmado ou cancelado nesse meio tempo
            if (request.isAnnulled() || request.getStatus() != AppointmentStatus.SOLICITADA) {
                continue;
            }
            refuse(request, "Pedido de consulta recusado",
                    "Seu pedido de consulta para " + formatDateTime(request)
                            + " não foi aceito porque o seu acompanhamento foi encerrado."
                            + " Fale com a clínica se quiser marcar uma consulta.");
            declined++;
        }
        return declined;
    }

    // recusar devolve o horario pra agenda, grava a trilha e avisa o paciente
    // quem chama ja travou o pedido
    private Appointment refuse(Appointment request, String title, String message) {
        request.setStatus(AppointmentStatus.RECUSADA);
        Appointment savedRequest = saveChange(request);
        notificationService.createNotification(savedRequest.getPatient(), title, message,
                Notification.TYPE_ALERT, Notification.PATIENT_AGENDA_LINK);
        return savedRequest;
    }

    // quantas consultas marcadas o paciente ainda tem por vir: o arquivamento n cancela, so conta e avisa
    @Transactional(readOnly = true)
    public long countFutureConfirmedOf(Long patientId) {
        return appointmentRepository.countByPatientIdAndStatusAndDateTimeAfterAndAnnulmentAnnulledAtIsNull(
                patientId, AppointmentStatus.AGENDADA, LocalDateTime.now());
    }

    // remarcar muda quando e como a consulta acontece e avisa o paciente
    // o pedido remarcado pelo prescritor ja fica confirmado no horario novo
    @Transactional
    public AgendaAppointmentDTO reschedule(Long appointmentId, AppointmentRescheduleDTO rescheduleData) {
        Appointment appointment = findAppointmentForUpdate(appointmentId);
        boolean canBeMoved = appointment.getStatus() == AppointmentStatus.SOLICITADA
                || appointment.getStatus() == AppointmentStatus.AGENDADA;
        if (appointment.isAnnulled() || !canBeMoved) {
            throw new BusinessException(CLOSED_APPOINTMENT_MESSAGE);
        }

        AppointmentType newType = rescheduleData.appointmentType();
        int durationMinutes = durationAfterReschedule(appointment, rescheduleData);
        checkNotInThePast(rescheduleData.dateTime());
        // consulta q ja passou pode ter receita ou meem, e com eles a remarcacao, q leva pro futuro, n muda a hora
        // vem dps do passado pra n mandar anular a receita por uma remarcacao q ia ser recusada de qq jeito
        consultationService.checkCanBeRescheduled(appointment, rescheduleData.dateTime());
        checkSlotIsFree(appointment.getPrescriber().getId(), rescheduleData.dateTime(), durationMinutes,
                appointment.getId());

        // a tela de remarcar manda a mesma data qnd so troca modalidade duracao ou tipo
        boolean timeChanged = !appointment.getDateTime().equals(rescheduleData.dateTime());
        // o pedido q vira consulta no mesmo horario segue com o texto do horario, q eh o q ele confirma
        boolean saysNewTime = timeChanged || appointment.getStatus() == AppointmentStatus.SOLICITADA;
        boolean modalityChanged = appointment.getModality() != rescheduleData.modality();
        boolean durationChanged = appointment.getDurationMinutes() != durationMinutes;
        boolean typeChanged = newType != null && newType != appointment.getAppointmentType();
        appointment.setDateTime(rescheduleData.dateTime());
        appointment.setModality(rescheduleData.modality());
        appointment.setDurationMinutes(durationMinutes);
        if (newType != null) {
            appointment.setAppointmentType(newType);
        }
        appointment.setStatus(AppointmentStatus.AGENDADA);
        // so o horario novo precisa de lembrete de novo pq a edicao no msm horario repetia o lembrete de consulta amanha
        if (timeChanged) {
            appointment.setReminderSentAt(null);
        }
        Appointment savedAppointment = saveChange(appointment);
        // o aviso diz o q mudou: antes era sempre "remarcada para", ate qnd o dia e a hora ficavam iguais e so
        // a modalidade trocava (decisao de 17/11/2026)
        String otherChanges = otherChangesText(savedAppointment, modalityChanged, durationChanged, typeChanged,
                saysNewTime);
        String noticeText = "Sua consulta com " + savedAppointment.getPrescriber().getName();
        if (saysNewTime || otherChanges.isEmpty()) {
            noticeText += " foi remarcada para " + formatDateTime(savedAppointment) + otherChanges + ".";
        } else {
            noticeText += " de " + formatDateTime(savedAppointment) + otherChanges + ".";
        }
        notificationService.createNotification(savedAppointment.getPatient(), "Consulta remarcada", noticeText,
                Notification.TYPE_APPOINTMENT, Notification.PATIENT_AGENDA_LINK);
        // remarcada pra amanha, o lembrete do horario novo sai agora: o job de amanha ja olha o dia seguinte,
        // e quem recebeu o e-mail do horario antigo ficava so com ele. a q ja tem lembrete (mesmo horario) n
        // ganha outro, e o pedido q vira consulta no mesmo horario ganha o primeiro
        boolean newReminderSent = reminderService.remindNowIfTomorrow(savedAppointment);
        // o paciente ja recebeu por e-mail o horario antigo e o novo, em outro dia, n ganhou lembrete agora, entao
        // a remarcacao tbm vai por e-mail (R11). o e-mail sempre diz "foi remarcada para", mesmo qnd essa edicao
        // n mudou o horario (o e-mail da remarcacao anterior n saiu), entao a mudanca vai do jeito curto
        if (!newReminderSent) {
            String emailChanges = otherChangesText(savedAppointment, modalityChanged, durationChanged, typeChanged,
                    true);
            reminderService.emailRescheduleIfEmailed(savedAppointment, emailChanges);
        }
        return new AgendaAppointmentDTO(savedAppointment);
    }

    // o q mudou alem do dia e da hora, numa frase so, pra entrar no aviso e no e-mail da remarcacao
    // com horario novo fica curto (", agora remota e com 45 minutos"), sem ele fica a frase inteira
    // (" agora é remota e tem 45 minutos"). vazio qnd so o horario mudou
    private String otherChangesText(Appointment appointment, boolean modalityChanged, boolean durationChanged,
                                    boolean typeChanged, boolean afterNewTime) {
        List<String> changes = new ArrayList<>();
        if (modalityChanged) {
            String modality = appointment.getModality() == AppointmentModality.PRESENCIAL ? "presencial" : "remota";
            changes.add(afterNewTime ? modality : "é " + modality);
        }
        if (durationChanged) {
            changes.add((afterNewTime ? "com " : "tem ") + appointment.getDurationMinutes() + " minutos");
        }
        if (typeChanged) {
            boolean isReturn = appointment.getAppointmentType() == AppointmentType.RETORNO;
            if (afterNewTime) {
                changes.add(isReturn ? "como retorno" : "como primeira consulta");
            } else {
                changes.add(isReturn ? "é um retorno" : "é uma primeira consulta");
            }
        }
        if (changes.isEmpty()) {
            return "";
        }
        String text = changes.get(0);
        for (int index = 1; index < changes.size(); index++) {
            boolean isLast = index == changes.size() - 1;
            text += (isLast ? " e " : ", ") + changes.get(index);
        }
        return (afterNewTime ? ", agora " : " agora ") + text;
    }

    // cancelar n apaga e a consulta continua no historico como cancelada
    // o pedido ainda sem resposta o paciente cancela a qualquer hora
    @Transactional
    public AgendaAppointmentDTO cancel(Long appointmentId, Users loggedUser) {
        Appointment appointment = findAppointmentForUpdate(appointmentId);
        // a trilha e o aviso daqui encostam no paciente e na conta, entao o paciente eh travado logo dps
        // da consulta: o cancelamento espera a confirmacao do e-mail (paciente, dps conta) em vez de
        // segurar a conta q ela vai travar enquanto ela segura o paciente
        patientRepository.lockById(appointment.getPatient().getId());
        if (appointment.getStatus() == AppointmentStatus.CANCELADA) {
            throw new BusinessException(ALREADY_CANCELED_MESSAGE);
        }
        if (appointment.isAnnulled() || !isOpen(appointment.getStatus())) {
            throw new BusinessException(CLOSED_APPOINTMENT_MESSAGE);
        }

        boolean canceledByPatient = loggedUser instanceof Patient;
        boolean isRequest = appointment.getStatus() == AppointmentStatus.SOLICITADA;
        boolean isLateForThePatient = !isRequest
                && appointment.getDateTime().isBefore(LocalDateTime.now().plusHours(PATIENT_CANCELLATION_HOURS));
        if (canceledByPatient && isLateForThePatient) {
            throw new BusinessException(LATE_CANCELLATION_MESSAGE);
        }
        // a consulta q ja terminou n eh mais cancelada, senao o paciente recebia "Consulta cancelada" de uma consulta
        // q ja passou. sobram a falta e o registro do atendimento, o par q a agenda mostra dps do fim
        // (decisao de 17/11/2026)
        // o pedido segue o caminho dele de confirmar ou recusar
        if (!isRequest && !endOf(appointment).isAfter(LocalDateTime.now())) {
            throw new BusinessException(APPOINTMENT_OVER_MESSAGE);
        }
        // a consulta q ficou agendada com receita ou meem aconteceu e n vira cancelada com eles valendo
        // vem dps do fim e das 24 horas, entao so a q comecou e ainda n terminou chega aqui
        consultationService.checkHasNoPrescriptionOrExam(appointmentId, CANCEL_WITH_PRESCRIPTION_MESSAGE,
                CANCEL_WITH_EXAM_MESSAGE);

        appointment.setStatus(AppointmentStatus.CANCELADA);
        Appointment savedAppointment = saveChange(appointment);
        // o pedido o prescritor nunca confirmou, entao o aviso dele fala de pedido e n de consulta
        if (canceledByPatient && isRequest) {
            notificationService.createNotification(savedAppointment.getPrescriber(),
                    "Pedido de consulta cancelado pelo paciente",
                    savedAppointment.getPatient().getDisplayName() + " cancelou o pedido de consulta de "
                            + formatDateTime(savedAppointment) + ".",
                    Notification.TYPE_ALERT, Notification.PRESCRIBER_AGENDA_LINK);
        } else if (canceledByPatient) {
            notificationService.createNotification(savedAppointment.getPrescriber(), "Consulta cancelada pelo paciente",
                    savedAppointment.getPatient().getDisplayName() + " cancelou a consulta de "
                            + formatDateTime(savedAppointment) + ".",
                    Notification.TYPE_ALERT, Notification.PRESCRIBER_AGENDA_LINK);
        } else {
            notificationService.createNotification(savedAppointment.getPatient(), "Consulta cancelada",
                    "Sua consulta com " + savedAppointment.getPrescriber().getName() + " de "
                            + formatDateTime(savedAppointment) + " foi cancelada.",
                    Notification.TYPE_ALERT, Notification.PATIENT_AGENDA_LINK);
            // o paciente q recebeu a consulta por e-mail recebe o cancelamento por e-mail tbm (R11)
            reminderService.emailCancellationIfEmailed(savedAppointment);
        }
        return new AgendaAppointmentDTO(savedAppointment);
    }

    // o paciente n apareceu na consulta marcada (RF10)
    // sem isso a consulta ficava AGENDADA pra sempre pq o unico jeito de fechar
    // era digitar o registro clinico de um atendimento q n aconteceu
    @Transactional
    public AgendaAppointmentDTO markNoShow(Long appointmentId) {
        Appointment appointment = findAppointmentForUpdate(appointmentId);
        if (appointment.isAnnulled()) {
            throw new BusinessException(CLOSED_APPOINTMENT_MESSAGE);
        }
        if (appointment.getStatus() != AppointmentStatus.AGENDADA) {
            throw new BusinessException(NOT_SCHEDULED_MESSAGE);
        }
        if (endOf(appointment).isAfter(LocalDateTime.now())) {
            throw new BusinessException(APPOINTMENT_NOT_OVER_MESSAGE);
        }
        // hoje a receita e o meem ja concluem a consulta, mas a antiga q ficou agendada com eles
        // mostra q o paciente veio, e a falta avisava ele com a receita daquela consulta em uso
        consultationService.checkHasNoPrescriptionOrExam(appointmentId, NO_SHOW_WITH_PRESCRIPTION_MESSAGE,
                NO_SHOW_WITH_EXAM_MESSAGE);

        appointment.setStatus(AppointmentStatus.NAO_COMPARECEU);
        Appointment savedAppointment = saveChange(appointment);
        notificationService.createNotification(savedAppointment.getPatient(), "Falta registrada",
                "A consulta de " + formatDateTime(savedAppointment)
                        + " foi marcada como falta. Fale com a clínica para remarcar.",
                Notification.TYPE_ALERT, Notification.PATIENT_AGENDA_LINK);
        return new AgendaAppointmentDTO(savedAppointment);
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

        // agenda vazia pra paciente arquivado e pra prescritor desativado pq oferecer
        // horario q ninguem vai atender so gera pedido parado na fila
        Patient patient = findPatient(patientId);
        Prescriber prescriber = patient.getPrescriber();
        if (patient.isArchived() || prescriber == null || !prescriber.isActive()) {
            return List.of();
        }

        // so do dia q fica a 24 horas daqui ate o limite do pedido pq o resto n tem horario q o paciente possa pedir
        // e data no fim do calendario nem chega no laco dos dias
        LocalDateTime earliestStart = earliestRequestStart();
        LocalDate firstDay = earliestStart.toLocalDate();
        if (from.isAfter(firstDay)) {
            firstDay = from;
        }
        LocalDate lastDay = lastDayToRequest();
        if (to.isBefore(lastDay)) {
            lastDay = to;
        }

        // o horario do paciente novo tem o tamanho da primeira consulta e o de quem ja foi atendido o do retorno
        int durationMinutes = durationOf(prescriber, typeFor(patientId));
        // o pedido novo substitui os q esperam resposta, entao eles n tiram horario do proprio paciente
        List<Appointment> ownRequests = appointmentRepository
                .findByPatientIdAndStatusAndAnnulmentAnnulledAtIsNullOrderByIdAsc(patientId, AppointmentStatus.SOLICITADA);
        List<TimeSlotDTO> freeSlots = new ArrayList<>();
        for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {
            freeSlots.addAll(getFreeSlots(prescriber, day, durationMinutes, earliestStart, ownRequests));
        }
        return freeSlots;
    }

    // agenda do prescritor entre duas datas e sem as datas vem inteira
    @Transactional(readOnly = true)
    public List<AgendaAppointmentDTO> getAgenda(Long prescriberId, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return agendaOf(appointmentRepository.findByPrescriberIdOrderByDateTimeAsc(prescriberId));
        }
        // so um lado do periodo ficava sem filtro nenhum e devolvia a agenda inteira
        DateCheck.checkRange(from, to);
        return agendaOf(appointmentRepository.findByPrescriberIdAndDateTimeBetweenOrderByDateTimeAsc(
                prescriberId, from.atStartOfDay(), to.atTime(LocalTime.MAX)));
    }

    // pedidos de pacientes q ainda esperam a resposta do prescritor
    @Transactional(readOnly = true)
    public List<AgendaAppointmentDTO> getWaitingRequests(Long prescriberId) {
        return agendaOf(appointmentRepository.findByPrescriberIdAndStatusAndDateTimeAfterOrderByDateTimeAsc(
                prescriberId, AppointmentStatus.SOLICITADA, LocalDateTime.now()));
    }

    // proximos pedidos e consultas do paciente com a situacao de cada um
    @Transactional(readOnly = true)
    public List<AgendaAppointmentDTO> getUpcomingForPatient(Long patientId) {
        return agendaOf(appointmentRepository.findByPatientIdAndDateTimeAfterOrderByDateTimeAsc(patientId,
                LocalDateTime.now()));
    }

    // abrir a consulta conta como abrir o prontuario do paciente
    @Transactional
    public AppointmentResponseDTO getById(Long id) {
        Appointment appointment = findAppointment(id);
        auditService.recordChartView(appointment.getPatient().getId());
        return new AppointmentResponseDTO(appointment);
    }

    private List<AgendaAppointmentDTO> agendaOf(List<Appointment> appointments) {
        return appointments.stream()
                .map(AgendaAppointmentDTO::new)
                .toList();
    }

    // consultas de um paciente da mais recente pra mais antiga
    // da consulta anulada o paciente recebe so a data, a marca e o motivo (revisao das regras R7)
    @Transactional
    public List<AppointmentResponseDTO> getByPatientId(Long patientId, Users loggedUser) {
        auditService.recordChartView(patientId);
        boolean isPatient = loggedUser.getRole() == UserRole.PATIENT;
        return appointmentRepository.findByPatientIdOrderByDateTimeDesc(patientId)
                .stream()
                .map(appointment -> isPatient && appointment.isAnnulled()
                        ? AppointmentResponseDTO.withoutClinicalContent(appointment)
                        : new AppointmentResponseDTO(appointment))
                .toList();
    }

    // as consultas de um paciente dentro da janela do grafico
    // entra so a consulta confirmada e n anulada pq o q n aconteceu n explica mudanca nenhuma
    @Transactional
    public List<AppointmentMarkerDTO> getMarcadoresDoPaciente(Long patientId, TimePeriod period) {
        auditService.recordChartView(patientId);
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(period.getDays());

        return appointmentRepository
                .findByPatientIdAndDateTimeBetweenOrderByDateTimeAsc(
                        patientId, start.atStartOfDay(), today.atTime(LocalTime.MAX))
                .stream()
                // marcada n eh o mesmo q aconteceu pq consulta q o paciente faltou fica
                // AGENDADA pra sempre e viraria marcador de atendimento no grafico
                .filter(appointment -> appointment.getStatus() == AppointmentStatus.CONCLUIDA)
                .filter(appointment -> !appointment.isAnnulled())
                .map(AppointmentMarkerDTO::new)
                .toList();
    }

    // os periodos de atendimento do dia divididos em horarios com a duracao da consulta do paciente
    // sai o q comeca antes das 24 horas de antecedencia e o q encosta em pedido ou consulta q segura o horario
    // dia de folga do prescritor n tem horario nenhum
    // os pedidos do proprio paciente q o pedido novo substitui n seguram horario pra ele, so o msm horario de um
    // deles n entra pq pedir ele de novo n muda nada
    private List<TimeSlotDTO> getFreeSlots(Prescriber prescriber, LocalDate day, int durationMinutes,
                                           LocalDateTime earliestStart, List<Appointment> ownRequests) {
        Long prescriberId = prescriber.getId();
        List<PrescriberAvailability> periods = availabilityRepository
                .findByPrescriberIdAndDayOfWeekOrderByStartTimeAsc(prescriberId, day.getDayOfWeek().getValue());
        if (periods.isEmpty() || dayOffRepository.existsByPrescriberIdAndOffDate(prescriberId, day)) {
            return List.of();
        }

        // anda no passo da consulta mais curta pra primeira e retorno se encaixarem
        int stepMinutes = Math.min(prescriber.getFirstAppointmentDurationMinutes(),
                prescriber.getAppointmentDurationMinutes());
        List<Appointment> takenAppointments = new ArrayList<>();
        for (Appointment taken : appointmentsHoldingTimeAround(prescriberId, day.atStartOfDay(),
                day.atTime(LocalTime.MAX), null)) {
            if (!hasAppointment(ownRequests, taken.getId())) {
                takenAppointments.add(taken);
            }
        }
        List<TimeSlotDTO> freeSlots = new ArrayList<>();
        for (PrescriberAvailability period : periods) {
            LocalDateTime slotStart = day.atTime(period.getStartTime());
            LocalDateTime periodEnd = day.atTime(period.getEndTime());
            while (!slotStart.plusMinutes(durationMinutes).isAfter(periodEnd)) {
                LocalDateTime slotEnd = slotStart.plusMinutes(durationMinutes);
                if (!slotStart.isBefore(earliestStart) && !overlapsAny(slotStart, slotEnd, takenAppointments)
                        && !startsAtOneOf(slotStart, ownRequests)) {
                    freeSlots.add(new TimeSlotDTO(slotStart, slotEnd));
                }
                slotStart = slotStart.plusMinutes(stepMinutes);
            }
        }
        return freeSlots;
    }

    private boolean hasAppointment(List<Appointment> appointments, Long appointmentId) {
        for (Appointment appointment : appointments) {
            if (appointment.getId().equals(appointmentId)) {
                return true;
            }
        }
        return false;
    }

    private boolean startsAtOneOf(LocalDateTime start, List<Appointment> appointments) {
        for (Appointment appointment : appointments) {
            if (appointment.getDateTime().equals(start)) {
                return true;
            }
        }
        return false;
    }

    // primeira consulta eh qnd o paciente ainda n tem consulta atendida
    // falta, cancelada e consulta anulada n contam como atendimento
    private AppointmentType typeFor(Long patientId) {
        boolean wasSeen = appointmentRepository.existsByPatientIdAndStatusAndAnnulmentAnnulledAtIsNull(
                patientId, AppointmentStatus.CONCLUIDA);
        return wasSeen ? AppointmentType.RETORNO : AppointmentType.PRIMEIRA_CONSULTA;
    }

    private int durationOf(Prescriber prescriber, AppointmentType type) {
        if (type == AppointmentType.PRIMEIRA_CONSULTA) {
            return prescriber.getFirstAppointmentDurationMinutes();
        }
        return prescriber.getAppointmentDurationMinutes();
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

    // 30 dias contando hoje igual a tela do paciente
    private LocalDate lastDayToRequest() {
        return LocalDate.now().plusDays(MAX_DAYS_AHEAD - 1);
    }

    // o horario mais cedo q o paciente pode pedir, 24 horas contadas de agora
    private LocalDateTime earliestRequestStart() {
        return LocalDateTime.now().plusHours(MIN_REQUEST_HOURS);
    }

    private void checkPatientCanRequest(Patient patient) {
        // arquivado saiu do acompanhamento e o pedido dele seguraria horario de quem ta em tratamento
        if (patient.isArchived()) {
            throw new BusinessException(ARCHIVED_PATIENT_MESSAGE);
        }
        Prescriber prescriber = patient.getPrescriber();
        if (prescriber == null) {
            throw new BusinessException(NO_PRESCRIBER_MESSAGE);
        }
        // prescritor desativado n entra mais entao ninguem responderia esse pedido
        if (!prescriber.isActive()) {
            throw new BusinessException(INACTIVE_PRESCRIBER_MESSAGE);
        }
    }

    // so vale um dos horarios livres entao fora do atendimento ocupado folga ou em cima da hora n entra
    private void checkIsOfferedSlot(Prescriber prescriber, LocalDateTime dateTime, int durationMinutes,
                                    LocalDateTime earliestStart, List<Appointment> replacedRequests) {
        boolean isFreeSlot = getFreeSlots(prescriber, dateTime.toLocalDate(), durationMinutes, earliestStart,
                replacedRequests)
                .stream()
                .anyMatch(slot -> slot.start().equals(dateTime));
        if (!isFreeSlot) {
            throw new BusinessException(SLOT_NOT_FREE_MESSAGE);
        }
    }

    // sem tipo novo fica o q era e sem duracao fica a q era
    // trocou o tipo sem dizer a duracao entao vale a duracao do tipo novo
    private int durationAfterReschedule(Appointment appointment, AppointmentRescheduleDTO rescheduleData) {
        AppointmentType newType = rescheduleData.appointmentType();
        boolean typeChanged = newType != null && newType != appointment.getAppointmentType();
        int durationMinutes = appointment.getDurationMinutes();
        if (rescheduleData.durationMinutes() != null) {
            durationMinutes = rescheduleData.durationMinutes();
        } else if (typeChanged) {
            durationMinutes = durationOf(appointment.getPrescriber(), newType);
        }
        return durationMinutes;
    }

    // RN08 dois horarios do mesmo prescritor n se sobrepoem
    private void checkSlotIsFree(Long prescriberId, LocalDateTime start, int durationMinutes, Long ignoredAppointmentId) {
        lockAgendaOf(prescriberId);
        LocalDateTime end = start.plusMinutes(durationMinutes);
        if (overlapsAny(start, end, appointmentsHoldingTimeAround(prescriberId, start, end, ignoredAppointmentId))) {
            throw new BusinessException(SLOT_TAKEN_MESSAGE);
        }
    }

    // a conferencia de horario livre le a agenda e so dps grava, entao dois pedidos
    // do mesmo horario ao mesmo tempo passavam os dois pela leitura e viravam duas consultas
    // a linha do prescritor fica travada ate o fim da transacao: o segundo pedido espera o
    // primeiro gravar, le a agenda ja com a consulta nova e recebe horario ocupado
    private void lockAgendaOf(Long prescriberId) {
        prescriberRepository.lockById(prescriberId);
    }

    // pedidos e consultas q seguram o horario e podem encostar no intervalo
    // a busca comeca no dia anterior pq a consulta marcada de noite pode passar da meia noite
    private List<Appointment> appointmentsHoldingTimeAround(Long prescriberId, LocalDateTime start, LocalDateTime end,
                                                          Long ignoredAppointmentId) {
        LocalDateTime previousDay = start.toLocalDate().minusDays(1).atStartOfDay();
        return appointmentRepository
                .findByPrescriberIdAndDateTimeBetween(prescriberId, previousDay, end)
                .stream()
                .filter(appointment -> !appointment.getId().equals(ignoredAppointmentId))
                .filter(appointment -> appointment.getStatus().holdsTimeSlot())
                .filter(appointment -> !appointment.isAnnulled())
                .toList();
    }

    // consultas ja confirmadas perto do horario do pedido sem contar o proprio pedido
    private List<Appointment> confirmedAppointmentsAround(Appointment request) {
        return appointmentsHoldingTimeAround(request.getPrescriber().getId(), request.getDateTime(), endOf(request),
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
        return BrazilianFormat.dateTime(appointment.getDateTime());
    }

    private Appointment findAppointment(Long appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> NotFoundException.appointment(appointmentId));
    }

    // a consulta q vai mudar fica travada e eh lida de novo antes de qualquer decisao: o q outro
    // acabou de gravar nela (um cancelamento no meio da confirmacao) aparece em vez de ser
    // sobrescrito. a ordem das travas fica sempre consulta e dps prescritor, igual no meem,
    // pra ninguem ficar esperando o outro
    private Appointment findAppointmentForUpdate(Long appointmentId) {
        return rowLock.reload(Appointment.class, appointmentId)
                .orElseThrow(() -> NotFoundException.appointment(appointmentId));
    }

    private Patient findPatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> NotFoundException.patient(patientId));
    }

    private Prescriber findPrescriber(Long prescriberId) {
        return prescriberRepository.findById(prescriberId)
                .orElseThrow(() -> NotFoundException.prescriber(prescriberId));
    }
}
