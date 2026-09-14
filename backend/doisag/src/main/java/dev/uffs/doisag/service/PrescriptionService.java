package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.AnnulmentDTO;
import dev.uffs.doisag.dto.DoseEscalationStepDTO;
import dev.uffs.doisag.dto.PrescriptionComponentDTO;
import dev.uffs.doisag.dto.PrescriptionCreateDTO;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.enums.PrescriptionStatus;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Annulment;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.DoseEscalationStep;
import dev.uffs.doisag.model.Prescription;
import dev.uffs.doisag.model.PrescriptionComponent;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PrescriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// prescricao (RF05)
// emitir uma nova substitui a vigente e a anterior fica no historico
// prescricao errada eh anulada com motivo e nunca apagada nem editada
@Service
public class PrescriptionService {

    public static final String ANNULLED_CONSULTATION_MESSAGE = "Consulta anulada não gera prescrição";
    public static final String CANCELED_CONSULTATION_MESSAGE = "Consulta cancelada não gera prescrição";
    public static final String NOT_CONFIRMED_CONSULTATION_MESSAGE = "Só consulta confirmada na agenda gera prescrição";
    public static final String ALREADY_ANNULLED_MESSAGE = "Esta prescrição já foi anulada";

    private final PrescriptionRepository prescriptionRepository;
    private final AppointmentRepository appointmentRepository;
    private final AuditService auditService;

    public PrescriptionService(PrescriptionRepository prescriptionRepository, AppointmentRepository appointmentRepository,
                               AuditService auditService) {
        this.prescriptionRepository = prescriptionRepository;
        this.appointmentRepository = appointmentRepository;
        this.auditService = auditService;
    }

    @Transactional
    public Prescription create(PrescriptionCreateDTO prescriptionData, Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Consulta não encontrada com o id: " + appointmentId));
        if (appointment.isAnnulled()) {
            throw new BusinessException(ANNULLED_CONSULTATION_MESSAGE);
        }
        if (appointment.getStatus() == AppointmentStatus.CANCELADA) {
            throw new BusinessException(CANCELED_CONSULTATION_MESSAGE);
        }
        // pedido sem resposta ou recusado n virou consulta
        if (!appointment.getStatus().isConfirmed()) {
            throw new BusinessException(NOT_CONFIRMED_CONSULTATION_MESSAGE);
        }

        Long patientId = appointment.getPatient().getId();
        replaceCurrentPrescriptions(patientId);

        Prescription prescription = new Prescription();
        prescription.setAppointment(appointment);
        prescription.setProductDescription(prescriptionData.productDescription());
        prescription.setBrand(prescriptionData.brand());
        prescription.setBatch(prescriptionData.batch());
        prescription.setSpectrum(prescriptionData.spectrum());
        prescription.setVolume(prescriptionData.volume());
        prescription.setPosology(prescriptionData.posology());
        prescription.setAdministrationRoute(prescriptionData.administrationRoute());
        prescription.setInstructions(prescriptionData.instructions());
        prescription.setPrecautions(prescriptionData.precautions());
        prescription.setExpectedEffects(prescriptionData.expectedEffects());
        prescription.setObservation(prescriptionData.observation());
        prescription.setTreatmentDurationDays(prescriptionData.treatmentDurationDays());
        prescription.setNextConsultationDate(prescriptionData.nextConsultationDate());

        for (PrescriptionComponentDTO componentData : prescriptionData.components()) {
            prescription.addComponent(new PrescriptionComponent(
                    componentData.cannabinoid(), componentData.concentration(), componentData.unit()));
        }

        // cada degrau do escalonamento vira uma linha propria
        if (prescriptionData.escalationSteps() != null) {
            for (DoseEscalationStepDTO stepData : prescriptionData.escalationSteps()) {
                DoseEscalationStep step = new DoseEscalationStep();
                step.setWeek(stepData.week());
                step.setDosage(stepData.dosage());
                step.setNote(stepData.note());
                prescription.addEscalationStep(step);
            }
        }

        Prescription savedPrescription = prescriptionRepository.save(prescription);
        auditService.recordCreation(AuditRecordType.PRESCRICAO, savedPrescription.getId(), patientId);
        return savedPrescription;
    }

    // prescricao errada fica no historico marcada como anulada e deixa de valer
    @Transactional
    public Prescription annul(Long prescriptionId, AnnulmentDTO annulmentData, Users loggedUser) {
        Prescription prescription = findPrescription(prescriptionId);
        if (prescription.isAnnulled()) {
            throw new BusinessException(ALREADY_ANNULLED_MESSAGE);
        }

        prescription.setAnnulment(new Annulment(loggedUser, annulmentData.reason()));
        Prescription savedPrescription = prescriptionRepository.save(prescription);
        auditService.recordAnnulment(AuditRecordType.PRESCRICAO, savedPrescription.getId(),
                savedPrescription.getAppointment().getPatient().getId());
        return savedPrescription;
    }

    // prescricoes de um paciente da consulta mais recente pra mais antiga
    public List<Prescription> getByPatientId(Long patientId) {
        auditService.recordChartView(patientId);
        return prescriptionRepository.findByAppointmentPatientIdOrderByAppointmentDateTimeDescCreatedAtDesc(patientId);
    }

    public Prescription getById(Long id) {
        Prescription prescription = findPrescription(id);
        auditService.recordChartView(prescription.getAppointment().getPatient().getId());
        return prescription;
    }

    public List<Prescription> getByAppointmentId(Long appointmentId) {
        appointmentRepository.findById(appointmentId)
                .ifPresent(appointment -> auditService.recordChartView(appointment.getPatient().getId()));
        return prescriptionRepository.findByAppointmentId(appointmentId);
    }

    // a prescricao q estava valendo passa a ser historico
    private void replaceCurrentPrescriptions(Long patientId) {
        List<Prescription> currentPrescriptions = prescriptionRepository
                .findByAppointmentPatientIdAndStatusAndAnnulmentAnnulledAtIsNull(patientId, PrescriptionStatus.VIGENTE);
        for (Prescription currentPrescription : currentPrescriptions) {
            currentPrescription.setStatus(PrescriptionStatus.SUBSTITUIDA);
            prescriptionRepository.save(currentPrescription);
            auditService.recordChange(AuditRecordType.PRESCRICAO, currentPrescription.getId(), patientId);
        }
    }

    private Prescription findPrescription(Long id) {
        return prescriptionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Prescrição não encontrada com o id: " + id));
    }
}
