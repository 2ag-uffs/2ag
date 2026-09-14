package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.AnnulmentDTO;
import dev.uffs.doisag.dto.ConsultationRecordDTO;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Annulment;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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

    // folga pro relogio do computador de quem registra estar um pouco adiantado
    private static final int CLOCK_TOLERANCE_MINUTES = 5;

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final AuditService auditService;

    public ConsultationService(AppointmentRepository appointmentRepository, PatientRepository patientRepository,
                               PrescriptionRepository prescriptionRepository, AuditService auditService) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.auditService = auditService;
    }

    // consulta q aconteceu sem ter sido marcada antes ou lancada depois
    @Transactional
    public Appointment register(Long patientId, ConsultationRecordDTO recordData, Prescriber prescriber) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado com o id: " + patientId));

        LocalDateTime dateTime = recordData.dateTime() == null ? LocalDateTime.now() : recordData.dateTime();
        checkDateAlreadyHappened(dateTime);
        checkRecordHasClinicalContent(recordData);

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setPrescriber(prescriber);
        appointment.setDateTime(dateTime);
        appointment.setDurationMinutes(prescriber.getAppointmentDurationMinutes());
        appointment.setStatus(AppointmentStatus.CONCLUIDA);
        copyClinicalFields(recordData, appointment);

        Appointment savedAppointment = appointmentRepository.save(appointment);
        auditService.recordCreation(AuditRecordType.CONSULTA, savedAppointment.getId(), patientId);
        return savedAppointment;
    }

    // corrige o registro ou registra o atendimento de uma consulta q estava marcada
    @Transactional
    public Appointment updateRecord(Long appointmentId, ConsultationRecordDTO recordData) {
        Appointment appointment = findAppointment(appointmentId);
        if (appointment.isAnnulled()) {
            throw new BusinessException(ANNULLED_MESSAGE);
        }
        if (appointment.getStatus() == AppointmentStatus.CANCELADA) {
            throw new BusinessException(CANCELED_MESSAGE);
        }
        // pedido sem resposta ou recusado n virou consulta
        if (!appointment.getStatus().isConfirmed()) {
            throw new BusinessException(NOT_CONFIRMED_MESSAGE);
        }

        LocalDateTime dateTime = recordData.dateTime() == null ? appointment.getDateTime() : recordData.dateTime();
        checkDateAlreadyHappened(dateTime);
        checkRecordHasClinicalContent(recordData);

        appointment.setDateTime(dateTime);
        appointment.setStatus(AppointmentStatus.CONCLUIDA);
        copyClinicalFields(recordData, appointment);

        Appointment savedAppointment = appointmentRepository.save(appointment);
        auditService.recordChange(AuditRecordType.CONSULTA, savedAppointment.getId(),
                savedAppointment.getPatient().getId());
        return savedAppointment;
    }

    // registro feito por engano n some e fica marcado como anulado com o motivo
    @Transactional
    public Appointment annul(Long appointmentId, AnnulmentDTO annulmentData, Users loggedUser) {
        Appointment appointment = findAppointment(appointmentId);
        if (appointment.isAnnulled()) {
            throw new BusinessException(ALREADY_ANNULLED_MESSAGE);
        }
        if (prescriptionRepository.existsByAppointmentIdAndAnnulmentAnnulledAtIsNull(appointmentId)) {
            throw new BusinessException(HAS_PRESCRIPTION_MESSAGE);
        }

        appointment.setAnnulment(new Annulment(loggedUser, annulmentData.reason()));
        Appointment savedAppointment = appointmentRepository.save(appointment);
        auditService.recordAnnulment(AuditRecordType.CONSULTA, savedAppointment.getId(),
                savedAppointment.getPatient().getId());
        return savedAppointment;
    }

    private Appointment findAppointment(Long appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Consulta não encontrada com o id: " + appointmentId));
    }

    private void checkDateAlreadyHappened(LocalDateTime dateTime) {
        if (dateTime.isAfter(LocalDateTime.now().plusMinutes(CLOCK_TOLERANCE_MINUTES))) {
            throw new BusinessException(FUTURE_DATE_MESSAGE);
        }
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
