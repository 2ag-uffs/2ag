package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.AnnulmentDTO;
import dev.uffs.doisag.dto.AppointmentResponseDTO;
import dev.uffs.doisag.dto.ConsultationRecordDTO;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.AppointmentType;
import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.infra.BrazilianFormat;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.DateCheck;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.infra.RepeatedSendMemory;
import dev.uffs.doisag.infra.RowLock;
import dev.uffs.doisag.model.Annulment;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Notification;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Prescription;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.PrescriptionRepository;
import dev.uffs.doisag.repository.ScaleResponseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

// registro clinico da consulta (RF04)
// marcar horario continua no AppointmentService e aqui fica o q aconteceu na consulta
@Service
public class ConsultationService {

    public static final String FUTURE_DATE_MESSAGE =
            "A consulta registrada precisa já ter acontecido. Para marcar uma consulta futura use a agenda";
    public static final String EMPTY_RECORD_MESSAGE =
            "Preencha pelo menos um campo clínico da consulta, como a queixa principal ou a evolução do quadro";
    public static final String ANNULLED_MESSAGE = "Esta consulta foi anulada e não pode ser alterada";
    public static final String CANCELED_MESSAGE = "Esta consulta foi cancelada e não recebe registro clínico";
    public static final String NOT_CONFIRMED_MESSAGE = "Só consulta confirmada na agenda recebe registro clínico";
    public static final String ALREADY_ANNULLED_MESSAGE = "Esta consulta já foi anulada";
    public static final String ARCHIVED_PATIENT_MESSAGE =
            "Este paciente está arquivado. Reative o paciente antes de registrar a consulta";
    public static final String HAS_PRESCRIPTION_MESSAGE =
            "Esta consulta tem prescrição. Anule a prescrição antes de anular a consulta";
    public static final String HAS_EXAM_MESSAGE =
            "Esta consulta tem um Mini-Exame aplicado. Anule o exame antes de anular a consulta";
    public static final String DATE_WITH_PRESCRIPTION_MESSAGE =
            "Esta consulta tem prescrição. Anule a prescrição antes de mudar a data da consulta";
    public static final String DATE_WITH_EXAM_MESSAGE =
            "Esta consulta tem um Mini-Exame aplicado. Anule o exame antes de mudar a data da consulta";
    public static final String DATE_PASSES_PRESCRIPTION_MESSAGE = "A nova data põe esta consulta antes ou depois "
            + "de outra consulta com prescrição, o que muda a prescrição em uso. "
            + "Anule a prescrição desta consulta antes de mudar a data";

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final PrescriberRepository prescriberRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final ScaleResponseRepository responseRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final RowLock rowLock;
    private final RepeatedSendMemory repeatedSendMemory;

    public ConsultationService(AppointmentRepository appointmentRepository, PatientRepository patientRepository,
                               PrescriberRepository prescriberRepository, PrescriptionRepository prescriptionRepository,
                               ScaleResponseRepository responseRepository, AuditService auditService,
                               NotificationService notificationService, RowLock rowLock,
                               RepeatedSendMemory repeatedSendMemory) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.prescriberRepository = prescriberRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.responseRepository = responseRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.rowLock = rowLock;
        this.repeatedSendMemory = repeatedSendMemory;
    }

    // consulta q aconteceu sem ter sido marcada antes ou lancada depois
    // a chave do envio eh a q a tela manda no X-Request-Key, e vem nula de quem n manda
    @Transactional
    public AppointmentResponseDTO register(Long patientId, ConsultationRecordDTO recordData, Prescriber prescriber,
                                           String requestKey) {
        // o prescritor fica travado antes de tudo, igual na agenda: no postgres o insert da consulta toma o paciente
        // e dps o prescritor em modo compartilhado, e o cadastro do acompanhamento segura o prescritor e espera o
        // paciente. sem essa trava os dois ficavam esperando um pelo outro. de quebra dois registros do mesmo
        // prescritor passam um de cada vez e o segundo ja conta o primeiro na hora de decidir o tipo
        prescriberRepository.lockById(prescriber.getId());
        // o mesmo envio de novo, pq a resposta do primeiro se perdeu, recebe a consulta q ja foi gravada
        // em vez de criar outra igual. vem dps da trava do prescritor, entao o reenvio espera o primeiro terminar
        String sendPlace = "consulta do prescritor " + prescriber.getId() + " com o paciente " + patientId;
        Appointment alreadySaved = findSavedBySameSend(sendPlace, requestKey, recordData);
        if (alreadySaved != null) {
            return new AppointmentResponseDTO(alreadySaved);
        }
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> NotFoundException.patient(patientId));
        checkPatientIsNotArchived(patientId, ARCHIVED_PATIENT_MESSAGE);

        LocalDateTime dateTime = recordData.dateTime() == null ? LocalDateTime.now() : recordData.dateTime();
        DateCheck.checkAlreadyHappened(dateTime, FUTURE_DATE_MESSAGE);
        checkRecordHasClinicalContent(recordData);

        // o tipo segue a mesma regra da agenda: primeira se o paciente n tinha consulta atendida antes dessa
        // olha so as anteriores pq a consulta antiga pode ser lancada depois de uma mais nova
        boolean wasSeen = appointmentRepository.existsByPatientIdAndStatusAndDateTimeBeforeAndAnnulmentAnnulledAtIsNull(
                patientId, AppointmentStatus.CONCLUIDA, dateTime);

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setPrescriber(prescriber);
        appointment.setDateTime(dateTime);
        if (wasSeen) {
            appointment.setAppointmentType(AppointmentType.RETORNO);
            appointment.setDurationMinutes(prescriber.getAppointmentDurationMinutes());
        } else {
            appointment.setAppointmentType(AppointmentType.PRIMEIRA_CONSULTA);
            appointment.setDurationMinutes(prescriber.getFirstAppointmentDurationMinutes());
        }
        appointment.setStatus(AppointmentStatus.CONCLUIDA);
        copyClinicalFields(recordData, appointment);

        Appointment savedAppointment = appointmentRepository.save(appointment);
        repeatedSendMemory.remember(sendPlace, requestKey, recordData, savedAppointment.getId());
        auditService.recordCreation(AuditRecordType.CONSULTA, savedAppointment.getId(), patientId);
        return new AppointmentResponseDTO(savedAppointment);
    }

    // a consulta q esse mesmo envio ja criou, ou null. a anulada nesse meio tempo n conta, e a q n foi
    // gravada de vdd (o primeiro envio falhou dps de guardar a chave) tbm n
    private Appointment findSavedBySameSend(String sendPlace, String requestKey, ConsultationRecordDTO recordData) {
        Long savedId = repeatedSendMemory.findSaved(sendPlace, requestKey, recordData);
        if (savedId == null) {
            return null;
        }
        Appointment saved = appointmentRepository.findById(savedId).orElse(null);
        if (saved == null || saved.isAnnulled()) {
            return null;
        }
        return saved;
    }

    // corrige o registro ou registra o atendimento de uma consulta q estava marcada
    @Transactional
    public AppointmentResponseDTO updateRecord(Long appointmentId, ConsultationRecordDTO recordData) {
        Appointment appointment = findAppointmentForUpdate(appointmentId);
        if (appointment.isAnnulled()) {
            throw new BusinessException(ANNULLED_MESSAGE);
        }
        if (appointment.getStatus() == AppointmentStatus.CANCELADA) {
            throw new BusinessException(CANCELED_MESSAGE);
        }
        // pedido sem resposta ou recusado n virou consulta
        // falta marcada por engano aceita, e o registro do atendimento desfaz ela
        if (!appointment.getStatus().acceptsClinicalRecord()) {
            throw new BusinessException(NOT_CONFIRMED_MESSAGE);
        }
        // a consulta ja concluida so esta sendo corrigida, e a correcao do q ja existe continua valendo pro
        // arquivado. a q estava marcada ou com falta ia ganhar o primeiro registro, q eh atendimento novo
        if (appointment.getStatus() != AppointmentStatus.CONCLUIDA) {
            checkPatientIsNotArchived(appointment.getPatient().getId(), ARCHIVED_PATIENT_MESSAGE);
        }

        LocalDateTime dateTime = recordData.dateTime() == null ? appointment.getDateTime() : recordData.dateTime();
        DateCheck.checkAlreadyHappened(dateTime, FUTURE_DATE_MESSAGE);
        checkRecordHasClinicalContent(recordData);
        checkDateCanChange(appointment, dateTime);
        boolean undoesAbsence = appointment.getStatus() == AppointmentStatus.NAO_COMPARECEU;
        LocalDateTime absenceDateTime = appointment.getDateTime();

        appointment.setDateTime(dateTime);
        appointment.setStatus(AppointmentStatus.CONCLUIDA);
        copyClinicalFields(recordData, appointment);

        Appointment savedAppointment = appointmentRepository.save(appointment);
        auditService.recordChange(AuditRecordType.CONSULTA, savedAppointment.getId(),
                savedAppointment.getPatient().getId());
        // a falta avisou o paciente, entao desfazer avisa tbm, senao sobrava so o aviso pedindo pra remarcar
        // vai com a data da falta pra bater com o aviso dela. igual na falta, o aviso vem dps da trilha
        if (undoesAbsence) {
            notificationService.createNotification(savedAppointment.getPatient(), "Falta desfeita",
                    "A falta da consulta de " + BrazilianFormat.dateTime(absenceDateTime)
                            + " foi desfeita e o atendimento foi registrado.",
                    Notification.TYPE_APPOINTMENT, Notification.PATIENT_AGENDA_LINK);
        }
        return new AppointmentResponseDTO(savedAppointment);
    }

    // registro feito por engano n some e fica marcado como anulado com o motivo
    @Transactional
    public AppointmentResponseDTO annul(Long appointmentId, AnnulmentDTO annulmentData, Prescriber loggedPrescriber) {
        Appointment appointment = findAppointmentForUpdate(appointmentId);
        if (appointment.isAnnulled()) {
            throw new BusinessException(ALREADY_ANNULLED_MESSAGE);
        }
        // a receita e o exame tem motivo proprio de anulacao e a trilha
        // precisa registrar cada um entao n da pra anular em cascata
        checkHasNoPrescriptionOrExam(appointmentId, HAS_PRESCRIPTION_MESSAGE, HAS_EXAM_MESSAGE);

        appointment.setAnnulment(new Annulment(loggedPrescriber, annulmentData.reason()));
        Appointment savedAppointment = appointmentRepository.save(appointment);
        auditService.recordAnnulment(AuditRecordType.CONSULTA, savedAppointment.getId(),
                savedAppointment.getPatient().getId());
        return new AppointmentResponseDTO(savedAppointment);
    }

    // prontuario de quem foi arquivado n recebe registro de consulta, receita nem mini-exame novos: a receita
    // nascia ja como "ultima prescricao", encerrada num dia antes do dela, e o paciente recebia "prescricao nova"
    // (decisao de 17/11/2026). anular e corrigir o q ja existe continua valendo
    // o paciente fica travado e eh lido de novo, entao o arquivamento gravado enquanto isto esperava aparece
    // na ordem das travas ele vem dps da consulta e do prescritor
    public void checkPatientIsNotArchived(Long patientId, String message) {
        patientRepository.lockById(patientId);
        if (patientRepository.existsByIdAndArchivedAtIsNotNull(patientId)) {
            throw new BusinessException(message);
        }
    }

    // a receita vigente sai da consulta mais recente e o meem fica no dia da consulta (RF05)
    // na correcao do registro isso so prende o q eles protegem (decisao de 14/11/2026, revisao das regras R26):
    // com receita a data so n muda qnd passa para antes ou depois de outra consulta do paciente com receita, e
    // com meem o dia n muda. antes trocar 18h por 10h no mesmo dia obrigava a anular a receita e emitir de novo
    // a tela manda a hora sem os segundos por isso a comparacao eh no minuto
    public void checkDateCanChange(Appointment appointment, LocalDateTime newDateTime) {
        if (isSameMinute(appointment.getDateTime(), newDateTime)) {
            return;
        }
        if (prescriptionRepository.existsByAppointmentIdAndAnnulmentAnnulledAtIsNull(appointment.getId())) {
            // o paciente vem dps da consulta, como no lancamento da receita: a receita lancada no mesmo instante
            // em outra consulta dele espera ou aparece aqui
            patientRepository.lockById(appointment.getPatient().getId());
            if (passesAnotherPrescription(appointment, newDateTime)) {
                throw new BusinessException(DATE_PASSES_PRESCRIPTION_MESSAGE);
            }
        }
        boolean sameDay = newDateTime.toLocalDate().equals(appointment.getDateTime().toLocalDate());
        if (!sameDay && responseRepository.existsByAppointmentIdAndAnnulmentAnnulledAtIsNull(appointment.getId())) {
            throw new BusinessException(DATE_WITH_EXAM_MESSAGE);
        }
    }

    // a remarcacao leva a consulta pro futuro, e receita e meem so existem em consulta q ja aconteceu: com eles
    // nem a hora muda, senao a receita ia pra uma consulta futura e virava a vigente pela data errada (RF05)
    public void checkCanBeRescheduled(Appointment appointment, LocalDateTime newDateTime) {
        if (isSameMinute(appointment.getDateTime(), newDateTime)) {
            return;
        }
        checkHasNoPrescriptionOrExam(appointment.getId(), DATE_WITH_PRESCRIPTION_MESSAGE, DATE_WITH_EXAM_MESSAGE);
    }

    private boolean isSameMinute(LocalDateTime first, LocalDateTime second) {
        return first.truncatedTo(ChronoUnit.MINUTES).equals(second.truncatedTo(ChronoUnit.MINUTES));
    }

    // a nova data troca de lado com alguma outra consulta do paciente q tem receita valendo, vigente ou substituida
    private boolean passesAnotherPrescription(Appointment appointment, LocalDateTime newDateTime) {
        List<Prescription> prescriptions = prescriptionRepository
                .findByAppointmentPatientIdOrderByAppointmentDateTimeDescCreatedAtDesc(appointment.getPatient().getId());
        for (Prescription prescription : prescriptions) {
            Appointment other = prescription.getAppointment();
            if (prescription.isAnnulled() || other.getId().equals(appointment.getId())) {
                continue;
            }
            boolean wasBefore = appointment.getDateTime().isBefore(other.getDateTime());
            boolean willBeBefore = newDateTime.isBefore(other.getDateTime());
            if (wasBefore != willBeBefore) {
                return true;
            }
        }
        return false;
    }

    // receita ou meem n anulados mostram q a consulta aconteceu e prendem ela ao dia dela
    // anular, mudar a data, marcar falta e cancelar esperam o prescritor anular os dois antes
    // cada acao manda a propria mensagem pq o q o prescritor faz em seguida muda
    public void checkHasNoPrescriptionOrExam(Long appointmentId, String prescriptionMessage, String examMessage) {
        if (prescriptionRepository.existsByAppointmentIdAndAnnulmentAnnulledAtIsNull(appointmentId)) {
            throw new BusinessException(prescriptionMessage);
        }
        if (responseRepository.existsByAppointmentIdAndAnnulmentAnnulledAtIsNull(appointmentId)) {
            throw new BusinessException(examMessage);
        }
    }

    // a receita e o meem concluem a consulta agendada com o registro clinico vazio. anulado o ultimo deles,
    // nada mais mostra q ela aconteceu, entao ela volta a agendada: senao a receita anulada seguia contando
    // como atendimento (retorno, marca no grafico) e a falta ficava travada, e o cancelamento tbm ate o fim dela
    // registro clinico nunca eh gravado vazio, entao concluida sem ele so veio da receita ou do meem
    // quem chama ja travou a consulta e o paciente, por isso a trilha pode vir aqui
    public void reopenIfConcludedOnlyByPrescriptionOrExam(Appointment appointment) {
        if (appointment.isAnnulled() || appointment.getStatus() != AppointmentStatus.CONCLUIDA) {
            return;
        }
        if (hasClinicalRecord(appointment)) {
            return;
        }
        if (prescriptionRepository.existsByAppointmentIdAndAnnulmentAnnulledAtIsNull(appointment.getId())
                || responseRepository.existsByAppointmentIdAndAnnulmentAnnulledAtIsNull(appointment.getId())) {
            return;
        }
        appointment.setStatus(AppointmentStatus.AGENDADA);
        Appointment savedAppointment = appointmentRepository.save(appointment);
        auditService.recordChange(AuditRecordType.CONSULTA, savedAppointment.getId(),
                savedAppointment.getPatient().getId());
    }

    // a consulta fica travada e eh lida de novo, igual na receita e no meem: a receita gravada
    // no meio da anulacao aparece na conferencia, em vez de sobrar uma receita vigente numa
    // consulta anulada, e a anulacao e a correcao n sobrescrevem uma a outra
    private Appointment findAppointmentForUpdate(Long appointmentId) {
        return rowLock.reload(Appointment.class, appointmentId)
                .orElseThrow(() -> NotFoundException.appointment(appointmentId));
    }

    private void checkRecordHasClinicalContent(ConsultationRecordDTO recordData) {
        boolean hasClinicalContent = hasAnyText(recordData.clinicalObservation(), recordData.physicalExam(),
                recordData.evolution(), recordData.diagnosis(), recordData.therapeuticPlan(),
                recordData.complementaryExams());
        if (!hasClinicalContent) {
            throw new BusinessException(EMPTY_RECORD_MESSAGE);
        }
    }

    // os mesmos campos q o registro pede preenchidos
    private boolean hasClinicalRecord(Appointment appointment) {
        return hasAnyText(appointment.getClinicalObservation(), appointment.getPhysicalExam(),
                appointment.getEvolution(), appointment.getDiagnosis(), appointment.getTherapeuticPlan(),
                appointment.getComplementaryExams());
    }

    private boolean hasAnyText(String... texts) {
        for (String text : texts) {
            if (text != null && !text.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private void copyClinicalFields(ConsultationRecordDTO recordData, Appointment appointment) {
        appointment.setModality(recordData.modality());
        appointment.setClinicalObservation(recordData.clinicalObservation());
        appointment.setPhysicalExam(recordData.physicalExam());
        appointment.setEvolution(recordData.evolution());
        appointment.setDiagnosis(recordData.diagnosis());
        appointment.setTherapeuticPlan(recordData.therapeuticPlan());
        appointment.setComplementaryExams(recordData.complementaryExams());
        appointment.setBloodPressure(recordData.bloodPressure());
        appointment.setWeight(recordData.weight());
        appointment.setHeight(recordData.height());
    }
}
