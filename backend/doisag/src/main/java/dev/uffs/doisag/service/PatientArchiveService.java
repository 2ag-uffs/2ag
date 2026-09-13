package dev.uffs.doisag.service;

import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// arquivamento de paciente
// o paciente arquivado sai da lista de ativos e o acompanhamento automatico dele acaba
// o prontuario continua inteiro e o paciente segue vendo o proprio historico
// as escalas q o prescritor enviar na mao continuam chegando pra ele
@Service
public class PatientArchiveService {

    public static final String ALREADY_ARCHIVED_MESSAGE = "Este paciente já está arquivado";
    public static final String NOT_ARCHIVED_MESSAGE = "Este paciente não está arquivado";

    private final PatientRepository patientRepository;
    private final TreatmentProtocolService treatmentProtocolService;
    private final AuditService auditService;

    public PatientArchiveService(PatientRepository patientRepository, TreatmentProtocolService treatmentProtocolService,
                                 AuditService auditService) {
        this.patientRepository = patientRepository;
        this.treatmentProtocolService = treatmentProtocolService;
        this.auditService = auditService;
    }

    @Transactional
    public Patient archive(Long patientId, Users loggedUser) {
        Patient patient = findPatient(patientId);
        if (patient.isArchived()) {
            throw new BusinessException(ALREADY_ARCHIVED_MESSAGE);
        }

        patient.setArchivedAt(LocalDateTime.now());
        patient.setArchivedBy(loggedUser);
        Patient savedPatient = patientRepository.save(patient);
        treatmentProtocolService.endActiveProtocolIfAny(patientId);
        auditService.recordArchiving(patientId);
        return savedPatient;
    }

    // reativar n recria o acompanhamento automatico e o prescritor monta um novo se quiser
    @Transactional
    public Patient reactivate(Long patientId) {
        Patient patient = findPatient(patientId);
        if (!patient.isArchived()) {
            throw new BusinessException(NOT_ARCHIVED_MESSAGE);
        }

        patient.setArchivedAt(null);
        patient.setArchivedBy(null);
        Patient savedPatient = patientRepository.save(patient);
        auditService.recordReactivation(patientId);
        return savedPatient;
    }

    private Patient findPatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado com o id: " + patientId));
    }
}
