package dev.uffs.doisag.service;

import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.InputCleaner;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

// a administracao liga e desliga o acesso de um paciente
// arquivar eh ato clinico do prescritor e n tira o acesso, quem tira eh isso aqui
@Service
public class PatientAccountService {

    public static final String PATIENT_NOT_FOUND_MESSAGE =
            "Nenhuma conta de paciente com este e-mail. Confira se ele foi digitado certo";
    public static final String ALREADY_ACTIVE_MESSAGE = "Esta conta já está ativa";
    public static final String ALREADY_INACTIVE_MESSAGE = "Esta conta já está desativada";

    private final PatientRepository patientRepository;
    private final AuditService auditService;

    public PatientAccountService(PatientRepository patientRepository, AuditService auditService) {
        this.patientRepository = patientRepository;
        this.auditService = auditService;
    }

    // a busca eh pelo e-mail inteiro pq a administracao n tem lista de paciente
    // e vai pra trilha, pq devolve de quem eh o numero (revisao das regras R20). sem transacao readOnly:
    // no postgres ela recusa o insert da trilha
    @Transactional
    public Patient findByEmail(String email) {
        Patient patient = patientRepository.findByEmail(InputCleaner.normalizeEmail(email))
                .orElseThrow(() -> NotFoundException.forUser(PATIENT_NOT_FOUND_MESSAGE));
        auditService.recordAdminLookup(patient.getId());
        return patient;
    }

    // pra administracao achar de novo quem ela desativou e poder reativar
    // a ordem eh pelo nome q a tela mostra, e o banco so sabe ordenar pelo civil (RN15)
    // cada abertura da lista vai pra trilha, como a busca (revisao das regras R20)
    @Transactional
    public List<Patient> listDeactivated() {
        auditService.recordDeactivatedListView();
        // collator do portugues pra letra com acento n ir pro fim da lista
        Collator collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"));
        return patientRepository.findAllByActiveFalseOrderByNameAsc()
                .stream()
                .sorted(Comparator.comparing(Patient::getDisplayName, collator))
                .toList();
    }

    @Transactional
    public Patient changeActive(Long patientId, boolean active) {
        // a trilha aponta pro paciente e a conta eh gravada no fim, entao o paciente eh travado antes,
        // na ordem da escala e da confirmacao do e-mail (paciente, dps conta)
        patientRepository.lockById(patientId);
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> NotFoundException.patient(patientId));
        // pedir o estado q a conta ja tem eh recusado: a resposta traz nome e e-mail, e sem isso
        // a administracao descobria quem eh cada numero da trilha sem deixar rastro nenhum
        if (patient.isActive() == active) {
            throw new BusinessException(active ? ALREADY_ACTIVE_MESSAGE : ALREADY_INACTIVE_MESSAGE);
        }
        patient.setActive(active);
        if (active) {
            // sem isso a sessao de antes da desativacao voltava a valer. a conta desativada ja n entra,
            // e encerrar na desativacao apagava a hora da saida da conta q a sessao esquecida precisa (CT-41)
            patient.endSessionsNow();
        }
        auditService.recordAccountActiveChange(AuditRecordType.CONTA_DE_PACIENTE, patientId, patientId, active);
        return patientRepository.save(patient);
    }
}
