package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.AnamnesisDTO;
import dev.uffs.doisag.dto.AnnulmentDTO;
import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Anamnesis;
import dev.uffs.doisag.model.Annulment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AnamnesisRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

// ficha de anamnese (RF19)
// o paciente preenche e corrige e o prescritor anula quando o registro foi feito por engano
@Service
public class AnamnesisService {

    public static final String ANNULLED_MESSAGE = "Anamnese anulada não pode ser alterada";
    public static final String ALREADY_ANNULLED_MESSAGE = "Esta anamnese já foi anulada";

    private final AnamnesisRepository anamnesisRepository;
    private final AuditService auditService;
    private final ScaleTaskService scaleTaskService;

    public AnamnesisService(AnamnesisRepository anamnesisRepository, AuditService auditService,
                            ScaleTaskService scaleTaskService) {
        this.anamnesisRepository = anamnesisRepository;
        this.auditService = auditService;
        this.scaleTaskService = scaleTaskService;
    }

    @Transactional
    public Anamnesis create(AnamnesisDTO anamnesisData, Patient patient) {
        Anamnesis anamnesis = new Anamnesis();
        anamnesis.setPatient(patient);
        anamnesis.setAssessmentDate(anamnesisData.assessmentDate() == null ? LocalDate.now() : anamnesisData.assessmentDate());
        copyAnswers(anamnesisData, anamnesis);

        Anamnesis savedAnamnesis = anamnesisRepository.save(anamnesis);
        auditService.recordCreation(AuditRecordType.ANAMNESE, savedAnamnesis.getId(), patient.getId());
        // preencher a anamnese da baixa na tarefa q o prescritor enviou igual as outras escalas
        scaleTaskService.completeTask(patient.getId(), ScaleType.ANAMNESE);
        return savedAnamnesis;
    }

    // o paciente corrige o q ele mesmo respondeu e o dono do registro nunca muda
    @Transactional
    public Anamnesis update(Long anamnesisId, AnamnesisDTO anamnesisData) {
        Anamnesis anamnesis = findAnamnesis(anamnesisId);
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
        return savedAnamnesis;
    }

    // anamnese feita por engano fica no historico marcada como anulada com o motivo
    @Transactional
    public Anamnesis annul(Long anamnesisId, AnnulmentDTO annulmentData, Users loggedUser) {
        Anamnesis anamnesis = findAnamnesis(anamnesisId);
        if (anamnesis.isAnnulled()) {
            throw new BusinessException(ALREADY_ANNULLED_MESSAGE);
        }

        anamnesis.setAnnulment(new Annulment(loggedUser, annulmentData.reason()));
        Anamnesis savedAnamnesis = anamnesisRepository.save(anamnesis);
        auditService.recordAnnulment(AuditRecordType.ANAMNESE, savedAnamnesis.getId(),
                savedAnamnesis.getPatient().getId());
        return savedAnamnesis;
    }

    // anamneses de um paciente da mais recente pra mais antiga
    public List<Anamnesis> getByPatientId(Long patientId) {
        auditService.recordChartView(patientId);
        return anamnesisRepository.findByPatientIdOrderByAssessmentDateDesc(patientId);
    }

    public Anamnesis getById(Long anamnesisId) {
        Anamnesis anamnesis = findAnamnesis(anamnesisId);
        auditService.recordChartView(anamnesis.getPatient().getId());
        return anamnesis;
    }

    private Anamnesis findAnamnesis(Long anamnesisId) {
        return anamnesisRepository.findById(anamnesisId)
                .orElseThrow(() -> new NotFoundException("Anamnese não encontrada com o id: " + anamnesisId));
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
