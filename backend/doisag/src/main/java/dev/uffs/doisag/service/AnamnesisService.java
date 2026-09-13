package dev.uffs.doisag.service;

import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Anamnesis;
import dev.uffs.doisag.repository.AnamnesisRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class AnamnesisService {
    private final AnamnesisRepository anamnesisRepository;
    private final AuditService auditService;
    private final ScaleAssignmentService scaleAssignmentService;

    public AnamnesisService(AnamnesisRepository anamnesisRepository, AuditService auditService,
                            ScaleAssignmentService scaleAssignmentService) {
        this.anamnesisRepository = anamnesisRepository;
        this.auditService = auditService;
        this.scaleAssignmentService = scaleAssignmentService;
    }

    // CREATE
    @Transactional
    public Anamnesis create(Anamnesis anamnesis) {
        Anamnesis savedAnamnesis = anamnesisRepository.save(anamnesis);
        auditService.recordCreation(AuditRecordType.ANAMNESE, savedAnamnesis.getId(),
                savedAnamnesis.getPatient().getId());
        // preencher a anamnese da baixa na tarefa q o prescritor enviou igual as outras escalas
        scaleAssignmentService.completeAssignedScale(savedAnamnesis.getPatient().getId(), ScaleType.ANAMNESE);
        return savedAnamnesis;
    }

    // anamneses de um paciente da mais recente pra mais antiga
    public List<Anamnesis> getByPatientId(Long patientId) {
        auditService.recordChartView(patientId);
        return anamnesisRepository.findByPatientIdOrderByAssessmentDateDesc(patientId);
    }

    // READ BY ID
    public Anamnesis getById(Long id) {
        Anamnesis anamnesis = anamnesisRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Anamnese não encontrada com o id: " + id));
        auditService.recordChartView(anamnesis.getPatient().getId());
        return anamnesis;
    }

    // UPDATE
    @Transactional
    public Anamnesis update(Long id, Anamnesis anamnesisDetails) {
        // busca a anamnese ou lança uma exceção
        Anamnesis anamnesis = anamnesisRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("anamnese não encontrada com o id: " + id));

        // atualiza os campos do objeto com os novos detalhes
        anamnesis.setAssessmentDate(anamnesisDetails.getAssessmentDate());
        // o paciente dono do registro n muda na edicao
        anamnesis.setReasonForVisit(anamnesisDetails.getReasonForVisit());
        anamnesis.setProfession(anamnesisDetails.getProfession());
        anamnesis.setDiet(anamnesisDetails.getDiet());
        anamnesis.setAnxiety(anamnesisDetails.getAnxiety());
        anamnesis.setObservation(anamnesisDetails.getObservation());
        anamnesis.setPain(anamnesisDetails.getPain());
        anamnesis.setAdverseReaction(anamnesisDetails.getAdverseReaction());
        anamnesis.setPreviousDiagnosis(anamnesisDetails.getPreviousDiagnosis());
        anamnesis.setSmokingHabits(anamnesisDetails.getSmokingHabits());
        anamnesis.setExpectations(anamnesisDetails.getExpectations());
        anamnesis.setCurrentMedication(anamnesisDetails.getCurrentMedication());
        anamnesis.setTreatmentAwareness(anamnesisDetails.getTreatmentAwareness());
        anamnesis.setGeneticCondition(anamnesisDetails.getGeneticCondition());
        anamnesis.setPreviousTreatment(anamnesisDetails.getPreviousTreatment());
        anamnesis.setFamilyHistory(anamnesisDetails.getFamilyHistory());
        anamnesis.setHeight(anamnesisDetails.getHeight());
        anamnesis.setWeight(anamnesisDetails.getWeight());
        anamnesis.setAlcoholConsumption(anamnesisDetails.getAlcoholConsumption());
        anamnesis.setSleepHabits(anamnesisDetails.getSleepHabits());
        anamnesis.setSubstanceUse(anamnesisDetails.getSubstanceUse());
        anamnesis.setPhysicalActivity(anamnesisDetails.getPhysicalActivity());

        Anamnesis savedAnamnesis = anamnesisRepository.save(anamnesis);
        auditService.recordChange(AuditRecordType.ANAMNESE, savedAnamnesis.getId(),
                savedAnamnesis.getPatient().getId());
        return savedAnamnesis;
    }
}