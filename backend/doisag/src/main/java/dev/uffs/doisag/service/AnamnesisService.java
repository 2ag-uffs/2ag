package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.AnamnesisResponseDTO;
import dev.uffs.doisag.dto.AnamnesisDTO;
import dev.uffs.doisag.dto.AnnulmentDTO;
import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.infra.RowLock;
import dev.uffs.doisag.model.Anamnesis;
import dev.uffs.doisag.model.Annulment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.AnamnesisRepository;
import dev.uffs.doisag.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

// ficha de anamnese (RF19)
// o paciente preenche e corrige e o prescritor anula quando o registro foi feito por engano
@Service
public class AnamnesisService {

    public static final String ANNULLED_MESSAGE = "Anamnese anulada não pode ser alterada";
    public static final String ARCHIVED_PATIENT_MESSAGE =
            "Seu acompanhamento está encerrado. Fale com a clínica antes de enviar uma anamnese nova";
    public static final String ALREADY_ANNULLED_MESSAGE = "Esta anamnese já foi anulada";

    private final AnamnesisRepository anamnesisRepository;
    private final PatientRepository patientRepository;
    private final AuditService auditService;
    private final ScaleTaskService scaleTaskService;
    private final RowLock rowLock;

    public AnamnesisService(AnamnesisRepository anamnesisRepository, PatientRepository patientRepository,
                            AuditService auditService, ScaleTaskService scaleTaskService, RowLock rowLock) {
        this.anamnesisRepository = anamnesisRepository;
        this.patientRepository = patientRepository;
        this.auditService = auditService;
        this.scaleTaskService = scaleTaskService;
        this.rowLock = rowLock;
    }

    @Transactional
    public AnamnesisResponseDTO create(AnamnesisDTO anamnesisData, Patient patient) {
        // prontuario de quem foi arquivado n recebe registro novo, o acompanhamento acabou
        if (patient.isArchived()) {
            throw new BusinessException(ARCHIVED_PATIENT_MESSAGE);
        }
        Anamnesis anamnesis = new Anamnesis();
        anamnesis.setPatient(patient);
        anamnesis.setAssessmentDate(anamnesisData.assessmentDate() == null ? LocalDate.now() : anamnesisData.assessmentDate());
        copyAnswers(anamnesisData, anamnesis);

        Anamnesis savedAnamnesis = anamnesisRepository.save(anamnesis);
        auditService.recordCreation(AuditRecordType.ANAMNESE, savedAnamnesis.getId(), patient.getId());
        // preencher a anamnese da baixa na tarefa q o prescritor enviou igual as outras escalas
        scaleTaskService.completeTask(patient.getId(), ScaleType.ANAMNESE);
        return new AnamnesisResponseDTO(savedAnamnesis);
    }

    // o paciente corrige o q ele mesmo respondeu e o dono do registro nunca muda
    @Transactional
    public AnamnesisResponseDTO update(Long anamnesisId, AnamnesisDTO anamnesisData) {
        Anamnesis anamnesis = findAnamnesisForUpdate(anamnesisId);
        if (anamnesis.isAnnulled()) {
            throw new BusinessException(ANNULLED_MESSAGE);
        }

        if (anamnesisData.assessmentDate() != null) {
            anamnesis.setAssessmentDate(anamnesisData.assessmentDate());
        }
        copyAnswers(anamnesisData, anamnesis);

        Anamnesis savedAnamnesis = anamnesisRepository.save(anamnesis);
        auditService.recordChange(AuditRecordType.ANAMNESE, savedAnamnesis.getId(),
                savedAnamnesis.getPatient().getId());
        return new AnamnesisResponseDTO(savedAnamnesis);
    }

    // anamnese feita por engano fica no historico marcada como anulada com o motivo
    @Transactional
    public AnamnesisResponseDTO annul(Long anamnesisId, AnnulmentDTO annulmentData, Prescriber loggedPrescriber) {
        Anamnesis anamnesis = findAnamnesisForUpdate(anamnesisId);
        if (anamnesis.isAnnulled()) {
            throw new BusinessException(ALREADY_ANNULLED_MESSAGE);
        }

        anamnesis.setAnnulment(new Annulment(loggedPrescriber, annulmentData.reason()));
        Anamnesis savedAnamnesis = anamnesisRepository.save(anamnesis);
        auditService.recordAnnulment(AuditRecordType.ANAMNESE, savedAnamnesis.getId(),
                savedAnamnesis.getPatient().getId());
        return new AnamnesisResponseDTO(savedAnamnesis);
    }

    // anamneses de um paciente da mais recente pra mais antiga
    @Transactional
    public List<AnamnesisResponseDTO> getByPatientId(Long patientId) {
        auditService.recordChartView(patientId);
        return anamnesisRepository.findByPatientIdOrderByAssessmentDateDesc(patientId)
                .stream()
                .map(AnamnesisResponseDTO::new)
                .toList();
    }

    @Transactional
    public AnamnesisResponseDTO getById(Long anamnesisId) {
        Anamnesis anamnesis = findAnamnesis(anamnesisId);
        auditService.recordChartView(anamnesis.getPatient().getId());
        return new AnamnesisResponseDTO(anamnesis);
    }

    private Anamnesis findAnamnesis(Long anamnesisId) {
        return anamnesisRepository.findById(anamnesisId)
                .orElseThrow(() -> anamnesisNotFound(anamnesisId));
    }

    // a correcao e a anulacao gravam a linha inteira, entao uma apagava a outra qnd vinham juntas
    // com a trava quem chega dps espera e le a anamnese de novo: a correcao encontra ela anulada
    // e eh recusada, e a anulacao mantem o q o paciente acabou de corrigir
    // o paciente eh travado antes da anamnese, igual na resposta de escala: a trilha das duas encosta
    // no paciente e a da correcao na conta dele tbm, entao elas esperam a confirmacao do e-mail
    // (paciente, dps conta) em vez de segurar a conta q ela vai travar
    private Anamnesis findAnamnesisForUpdate(Long anamnesisId) {
        Long patientId = anamnesisRepository.findPatientIdById(anamnesisId)
                .orElseThrow(() -> anamnesisNotFound(anamnesisId));
        patientRepository.lockById(patientId);
        return rowLock.reload(Anamnesis.class, anamnesisId)
                .orElseThrow(() -> anamnesisNotFound(anamnesisId));
    }

    private NotFoundException anamnesisNotFound(Long anamnesisId) {
        return new NotFoundException("Anamnese não encontrada com o id: " + anamnesisId);
    }

    private void copyAnswers(AnamnesisDTO anamnesisData, Anamnesis anamnesis) {
        anamnesis.setProfession(anamnesisData.profession());
        anamnesis.setReasonForVisit(anamnesisData.reasonForVisit());
        anamnesis.setPreviousDiagnosis(anamnesisData.previousDiagnosis());
        anamnesis.setPreviousTreatment(anamnesisData.previousTreatment());
        anamnesis.setCurrentMedication(anamnesisData.currentMedication());
        anamnesis.setFamilyHistory(anamnesisData.familyHistory());
        anamnesis.setAdverseReaction(anamnesisData.adverseReaction());
        anamnesis.setGeneticCondition(anamnesisData.geneticCondition());
        anamnesis.setDiet(anamnesisData.diet());
        anamnesis.setSmokingHabits(anamnesisData.smokingHabits());
        anamnesis.setAlcoholConsumption(anamnesisData.alcoholConsumption());
        anamnesis.setWeight(anamnesisData.weight());
        anamnesis.setHeight(anamnesisData.height());
        anamnesis.setSubstanceUse(anamnesisData.substanceUse());
        anamnesis.setPhysicalActivity(anamnesisData.physicalActivity());
        anamnesis.setSleepHabits(anamnesisData.sleepHabits());
        anamnesis.setAnxiety(anamnesisData.anxiety());
        anamnesis.setPain(anamnesisData.pain());
        anamnesis.setExpectations(anamnesisData.expectations());
        anamnesis.setTreatmentAwareness(anamnesisData.treatmentAwareness());
        anamnesis.setObservation(anamnesisData.observation());
    }
}
