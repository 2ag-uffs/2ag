package dev.uffs.doisag.service;

import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.FollowUp;
import dev.uffs.doisag.repository.FollowUpRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.uffs.doisag.enums.ScaleType;

import java.util.Optional;

@Service
public class FollowUpService {
    // injeções
    private final FollowUpRepository followUpRepository;
    // aqui eh a injeçao do service q controla o status
    private final ScaleAssignmentService scaleAssignmentService;
    private final AuditService auditService;

    public FollowUpService(FollowUpRepository followUpRepository, ScaleAssignmentService scaleAssignmentService,
            AuditService auditService) {
        this.followUpRepository = followUpRepository;
        this.scaleAssignmentService = scaleAssignmentService;
        this.auditService = auditService;
    }

    // CREATE
    @Transactional
    public FollowUp create(FollowUp followUp) {
        // salva o acompanhamento preenchido no banco
        FollowUp savedFollowUp = followUpRepository.save(followUp);

        // ai avisa o outro service pra marcar a tarefa como concluída
        if (savedFollowUp.getPatient() != null) {
            auditService.recordCreation(AuditRecordType.ACOMPANHAMENTO_SEMANAL, savedFollowUp.getId(), savedFollowUp.getPatient().getId());
            scaleAssignmentService.completeAssignedScale(
                    savedFollowUp.getPatient().getId(),
                    ScaleType.ACOMPANHAMENTO_SEMANAL // o tipo
            );
        }

        // retorna o objeto salvo
        return savedFollowUp;
    }

    // READ BY ID
    public FollowUp getById(Long id) {
        FollowUp followUp = followUpRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Acompanhamento não encontrado com o id: " + id));
        auditService.recordChartView(followUp.getPatient().getId());
        return followUp;
    }

    // UPDATE
    @Transactional
    public FollowUp update(Long id, FollowUp followUpDetails) {
        // busca o acompanhamento ou lança uma exceção
        FollowUp followUp = followUpRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("acompanhamento não encontrado com o id: " + id));

        // atualiza os campos do objeto com os novos detalhes
        followUp.setAssessmentDate(followUpDetails.getAssessmentDate());
        // o paciente dono do registro n muda na edicao
        followUp.setMorningDrops(followUpDetails.getMorningDrops());
        followUp.setAfternoonDrops(followUpDetails.getAfternoonDrops());
        followUp.setComment(followUpDetails.getComment());
        followUp.setTremor(followUpDetails.getTremor());
        followUp.setRigiditySpasticity(followUpDetails.getRigiditySpasticity());
        followUp.setNausea(followUpDetails.getNausea());
        followUp.setConcentration(followUpDetails.getConcentration());
        followUp.setAppetite(followUpDetails.getAppetite());
        followUp.setSocialInteraction(followUpDetails.getSocialInteraction());
        followUp.setDisposition(followUpDetails.getDisposition());
        followUp.setIntestinalFunction(followUpDetails.getIntestinalFunction());
        followUp.setAnxiety(followUpDetails.getAnxiety());
        followUp.setSubstanceReduction(followUpDetails.getSubstanceReduction());
        followUp.setPain(followUpDetails.getPain());
        followUp.setSportsPerformance(followUpDetails.getSportsPerformance());
        followUp.setSleep(followUpDetails.getSleep());
        followUp.setDermatologicalDisease(followUpDetails.getDermatologicalDisease());
        followUp.setMood(followUpDetails.getMood());

        FollowUp savedRecord = followUpRepository.save(followUp);
        auditService.recordChange(AuditRecordType.ACOMPANHAMENTO_SEMANAL, savedRecord.getId(), savedRecord.getPatient().getId());
        return savedRecord;
    }
}