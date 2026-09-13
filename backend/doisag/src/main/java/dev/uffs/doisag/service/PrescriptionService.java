package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.PrescriptionUpdateDTO;
import dev.uffs.doisag.model.Prescription;
import dev.uffs.doisag.dto.PrescriptionCreateDTO;
import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PrescriptionRepository;
import dev.uffs.doisag.model.Appointment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class PrescriptionService {
    private final PrescriptionRepository prescriptionRepository;
    private final AppointmentRepository appointmentRepository; // precisa do repo de appointment
    private final AuditService auditService;

    // injeto via construtor
    public PrescriptionService(PrescriptionRepository prescriptionRepository, AppointmentRepository appointmentRepository,
                               AuditService auditService) {
        this.prescriptionRepository = prescriptionRepository;
        this.appointmentRepository = appointmentRepository;
        this.auditService = auditService;
    }

    // CREATE
    @Transactional
    public Prescription create(PrescriptionCreateDTO dto, Long appointmentId) {
        // busca a consulta ou lança nossa exceção personalizada
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Consulta não encontrada com o id: " + appointmentId));

        // cria a nova entidade a partir dos dados do DTO
        Prescription newPrescription = new Prescription();
        newPrescription.setProductDescription(dto.productDescription());
        newPrescription.setPosology(dto.posology());
        newPrescription.setBrand(dto.brand());
        newPrescription.setConcentration(dto.concentration());
        newPrescription.setSpectrum(dto.spectrum());
        newPrescription.setVolume(dto.volume());
        newPrescription.setAdministrationRoute(dto.administrationRoute());
        newPrescription.setObservation(dto.observation());
        newPrescription.setInstructions(dto.instructions());
        newPrescription.setPrecautions(dto.precautions());
        newPrescription.setExpectedEffects(dto.expectedEffects());
        newPrescription.setTreatmentDurationDays(dto.treatmentDurationDays());
        newPrescription.setNextConsultationDate(dto.nextConsultationDate());

        // cada degrau do escalonamento vira uma linha propria
        if (dto.escalationSteps() != null) {
            for (var passo : dto.escalationSteps()) {
                var step = new dev.uffs.doisag.model.DoseEscalationStep();
                step.setWeek(passo.week());
                step.setDosage(passo.dosage());
                step.setNote(passo.note());
                newPrescription.addEscalationStep(step);
            }
        }

        // associa a prescrição com a consulta encontrada
        newPrescription.setAppointment(appointment);

        // salva a nova prescrição já com a FK preenchida
        Prescription savedPrescription = prescriptionRepository.save(newPrescription);
        auditService.recordCreation(AuditRecordType.PRESCRICAO, savedPrescription.getId(),
                appointment.getPatient().getId());
        return savedPrescription;
    }

    // prescricoes de um paciente da consulta mais recente pra mais antiga
    public List<Prescription> getByPatientId(Long patientId) {
        auditService.recordChartView(patientId);
        return prescriptionRepository.findByAppointmentPatientIdOrderByAppointmentDateTimeDesc(patientId);
    }

    // READ BY ID
    public Prescription getById(Long id) {
        Prescription prescription = prescriptionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Prescrição não encontrada com o id: " + id));
        auditService.recordChartView(prescription.getAppointment().getPatient().getId());
        return prescription;
    }

    // UPDATE
    @Transactional
    public Prescription update(Long id, PrescriptionUpdateDTO dto) {
        // busca a prescrição ou lança uma exceção
        Prescription prescription = prescriptionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Prescrição não encontrada com o id: " + id));

        // atualiza os campos do objeto com os novos detalhes do DTO
        // garante que apenas os campos permitidos sejam alterados
        prescription.setProductDescription(dto.productDescription());
        prescription.setPosology(dto.posology());
        prescription.setBrand(dto.brand());
        prescription.setConcentration(dto.concentration());
        prescription.setSpectrum(dto.spectrum());
        prescription.setObservation(dto.observation());

        Prescription savedPrescription = prescriptionRepository.save(prescription);
        auditService.recordChange(AuditRecordType.PRESCRICAO, savedPrescription.getId(),
                savedPrescription.getAppointment().getPatient().getId());
        return savedPrescription;
    }

    public List<Prescription> getByAppointmentId(Long appointmentId) {
        appointmentRepository.findById(appointmentId)
                .ifPresent(appointment -> auditService.recordChartView(appointment.getPatient().getId()));
        return prescriptionRepository.findByAppointmentId(appointmentId);
    }
}