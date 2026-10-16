package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.AnnulmentDTO;
import dev.uffs.doisag.dto.AppointmentResponseDTO;
import dev.uffs.doisag.dto.ConsultationRecordDTO;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.AppointmentType;
import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.DateCheck;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.infra.RowLock;
import dev.uffs.doisag.model.Annulment;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriptionRepository;
import dev.uffs.doisag.repository.ScaleResponseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// registro clinico da consulta (RF04)
// marcar horario continua no AppointmentService e aqui fica o q aconteceu na consulta
@Service
public class ConsultationService {

    public static final String FUTURE_DATE_MESSAGE =
            "A consulta registrada precisa já ter acontecido. Para marcar uma consulta futura use a agenda";
    public static final String EMPTY_RECORD_MESSAGE = "Preencha pelo menos um campo clínico da consulta";
    public static final String ANNULLED_MESSAGE = "Consulta anulada não pode ser alterada";
    public static final String CANCELED_MESSAGE = "Consulta cancelada não recebe registro clínico";
    public static final String NOT_CONFIRMED_MESSAGE = "Só consulta confirmada na agenda recebe registro clínico";
    public static final String ALREADY_ANNULLED_MESSAGE = "Esta consulta já foi anulada";
    public static final String HAS_PRESCRIPTION_MESSAGE =
            "Esta consulta tem prescrição. Anule a prescrição antes de anular a consulta";
    public static final String HAS_EXAM_MESSAGE =
            "Esta consulta tem um Mini-Exame aplicado. Anule o exame antes de anular a consulta";
    public static final String DATE_WITH_PRESCRIPTION_MESSAGE =
            "Esta consulta tem prescrição. Anule a prescrição antes de mudar a data da consulta";
    public static final String DATE_WITH_EXAM_MESSAGE =
            "Esta consulta tem um Mini-Exame aplicado. Anule o exame antes de mudar a data da consulta";

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final ScaleResponseRepository responseRepository;
    private final AuditService auditService;
    private final RowLock rowLock;

    public ConsultationService(AppointmentRepository appointmentRepository, PatientRepository patientRepository,
                               PrescriptionRepository prescriptionRepository,
                               ScaleResponseRepository responseRepository, AuditService auditService,
                               RowLock rowLock) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.responseRepository = responseRepository;
        this.auditService = auditService;
        this.rowLock = rowLock;
    }

    // consulta q aconteceu sem ter sido marcada antes ou lancada depois
    @Transactional
    public AppointmentResponseDTO register(Long patientId, ConsultationRecordDTO recordData, Prescriber prescriber) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado com o id: " + patientId));

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
        auditService.recordCreation(AuditRecordType.CONSULTA, savedAppointment.getId(), patientId);
        return new AppointmentResponseDTO(savedAppointment);
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

        LocalDateTime dateTime = recordData.dateTime() == null ? appointment.getDateTime() : recordData.dateTime();
        DateCheck.checkAlreadyHappened(dateTime, FUTURE_DATE_MESSAGE);
        checkRecordHasClinicalContent(recordData);
        checkDateCanChange(appointment, dateTime);

        appointment.setDateTime(dateTime);
        appointment.setStatus(AppointmentStatus.CONCLUIDA);
        copyClinicalFields(recordData, appointment);

        Appointment savedAppointment = appointmentRepository.save(appointment);
        auditService.recordChange(AuditRecordType.CONSULTA, savedAppointment.getId(),
                savedAppointment.getPatient().getId());
        return new AppointmentResponseDTO(savedAppointment);
    }

    // registro feito por engano n some e fica marcado como anulado com o motivo
    @Transactional
    public AppointmentResponseDTO annul(Long appointmentId, AnnulmentDTO annulmentData, Prescriber loggedPrescriber) {
        Appointment appointment = findAppointmentForUpdate(appointmentId);
        if (appointment.isAnnulled()) {
            throw new BusinessException(ALREADY_ANNULLED_MESSAGE);
        }
        if (prescriptionRepository.existsByAppointmentIdAndAnnulmentAnnulledAtIsNull(appointmentId)) {
            throw new BusinessException(HAS_PRESCRIPTION_MESSAGE);
        }
        // mesmo tratamento da prescricao: o exame tem motivo proprio de anulacao
        // e a trilha precisa registrar cada um, entao n da pra anular em cascata
        if (responseRepository.existsByAppointmentIdAndAnnulmentAnnulledAtIsNull(appointmentId)) {
            throw new BusinessException(HAS_EXAM_MESSAGE);
        }

        appointment.setAnnulment(new Annulment(loggedPrescriber, annulmentData.reason()));
        Appointment savedAppointment = appointmentRepository.save(appointment);
        auditService.recordAnnulment(AuditRecordType.CONSULTA, savedAppointment.getId(),
                savedAppointment.getPatient().getId());
        return new AppointmentResponseDTO(savedAppointment);
    }

    // a receita vigente sai da consulta mais recente e o meem fica no dia da consulta,
    // entao mudar a data com eles deixava a receita de uma consulta antiga valendo (RF05)
    // vale pra correcao do registro e pra remarcacao na agenda
    // a tela manda a hora sem os segundos, por isso a comparacao eh no minuto
    public void checkDateCanChange(Appointment appointment, LocalDateTime newDateTime) {
        boolean dateChanged = !newDateTime.truncatedTo(ChronoUnit.MINUTES)
                .equals(appointment.getDateTime().truncatedTo(ChronoUnit.MINUTES));
        if (!dateChanged) {
            return;
        }
        if (prescriptionRepository.existsByAppointmentIdAndAnnulmentAnnulledAtIsNull(appointment.getId())) {
            throw new BusinessException(DATE_WITH_PRESCRIPTION_MESSAGE);
        }
        if (responseRepository.existsByAppointmentIdAndAnnulmentAnnulledAtIsNull(appointment.getId())) {
            throw new BusinessException(DATE_WITH_EXAM_MESSAGE);
        }
    }

    // a consulta fica travada e eh lida de novo, igual na receita e no meem: a receita gravada
    // no meio da anulacao aparece na conferencia, em vez de sobrar uma receita vigente numa
    // consulta anulada, e a anulacao e a correcao n sobrescrevem uma a outra
    private Appointment findAppointmentForUpdate(Long appointmentId) {
        return rowLock.reload(Appointment.class, appointmentId)
                .orElseThrow(() -> new NotFoundException("Consulta não encontrada com o id: " + appointmentId));
    }

    private void checkRecordHasClinicalContent(ConsultationRecordDTO recordData) {
        boolean hasClinicalContent = hasText(recordData.clinicalObservation())
                || hasText(recordData.physicalExam())
                || hasText(recordData.evolution())
                || hasText(recordData.diagnosis())
                || hasText(recordData.therapeuticPlan())
                || hasText(recordData.complementaryExams());
        if (!hasClinicalContent) {
            throw new BusinessException(EMPTY_RECORD_MESSAGE);
        }
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
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
