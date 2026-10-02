package dev.uffs.doisag.service;

import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.infra.InputCleaner;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

// a administracao liga e desliga o acesso de um paciente
// arquivar eh ato clinico do prescritor e n tira o acesso, quem tira eh isso aqui
@Service
public class PatientAccountService {

    public static final String PATIENT_NOT_FOUND_MESSAGE = "Nenhuma conta de paciente com este e-mail";

    private final PatientRepository patientRepository;
    private final AuditService auditService;

    public PatientAccountService(PatientRepository patientRepository, AuditService auditService) {
        this.patientRepository = patientRepository;
        this.auditService = auditService;
    }

    // a busca eh pelo e-mail inteiro pq a administracao n tem lista de paciente
    @Transactional(readOnly = true)
    public Patient findByEmail(String email) {
        return patientRepository.findByEmail(InputCleaner.normalizeEmail(email))
                .orElseThrow(() -> NotFoundException.forUser(PATIENT_NOT_FOUND_MESSAGE));
    }

    // pra administracao achar de novo quem ela desativou e poder reativar
    @Transactional(readOnly = true)
    public List<Patient> listDeactivated() {
        return patientRepository.findAllByActiveFalseOrderByNameAsc();
    }

    @Transactional
    public Patient changeActive(Long patientId, boolean active) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado com o id: " + patientId));
        if (patient.isActive() == active) {
            return patient;
        }
        patient.setActive(active);
        if (!active) {
            // sem isso a sessao aberta voltava a valer se a conta fosse reativada logo depois
            patient.setSessionsEndedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        }
        auditService.recordAccountActiveChange(AuditRecordType.CONTA_DE_PACIENTE, patientId, patientId, active);
        return patientRepository.save(patient);
    }
}
