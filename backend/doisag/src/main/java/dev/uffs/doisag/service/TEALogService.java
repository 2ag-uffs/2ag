package dev.uffs.doisag.service;

import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.TEALog;
import dev.uffs.doisag.repository.TEALogRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.uffs.doisag.enums.ScaleType;

import java.util.Optional;

@Service
public class TEALogService {
    private final TEALogRepository teaLogRepository;
    // aqui eh a injeçao do service q controla o status
    private final ScaleAssignmentService scaleAssignmentService;
    private final AuditService auditService;

    public TEALogService(TEALogRepository teaLogRepository, ScaleAssignmentService scaleAssignmentService,
            AuditService auditService) {
        this.teaLogRepository = teaLogRepository;
        this.scaleAssignmentService = scaleAssignmentService;
        this.auditService = auditService;
    }

    // método privado para calcular o score total
    private Integer calculateTotalScore(TEALog log) {
        return ScoreHelper.sumOrNull(
                log.getFreqAggressiveness(),
                log.getFreqAgitation(),
                log.getFreqSleepIssues(),
                log.getFreqSocialInteraction(),
                log.getFreqStereotypy(),
                log.getFreqAppetiteIssues());
    }

    // CREATE
    @Transactional
    public TEALog create(TEALog teaLog) {
        // calcula o score
        Integer totalScore = calculateTotalScore(teaLog);
        teaLog.setTeaScore(totalScore);

        // salva no banco
        TEALog savedScale = teaLogRepository.save(teaLog);

        // usar o service para marcar como concluido
        if (savedScale.getPatient() != null) {
            auditService.recordCreation(AuditRecordType.REGISTRO_TEA, savedScale.getId(), savedScale.getPatient().getId());
            scaleAssignmentService.completeAssignedScale(
                    savedScale.getPatient().getId(),
                    ScaleType.REGISTRO_TEA
            );
        }
        // retorna a escala salva
        return savedScale;
    }

    // READ BY ID
    public TEALog getById(Long id) {
        TEALog teaLog = teaLogRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Registro de sintomas não encontrado com o id: " + id));
        auditService.recordChartView(teaLog.getPatient().getId());
        return teaLog;
    }

    // UPDATE
    @Transactional
    public TEALog update(Long id, TEALog logDetails) {
        TEALog existingLog = teaLogRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("registro tea não encontrado com o id: " + id));

        // atualiza os campos
        existingLog.setAssessmentDate(logDetails.getAssessmentDate());
        // o paciente dono do registro n muda na edicao
        existingLog.setFreqAggressiveness(logDetails.getFreqAggressiveness());
        existingLog.setFreqAgitation(logDetails.getFreqAgitation());
        existingLog.setFreqSleepIssues(logDetails.getFreqSleepIssues());
        existingLog.setFreqSocialInteraction(logDetails.getFreqSocialInteraction());
        existingLog.setFreqStereotypy(logDetails.getFreqStereotypy());
        existingLog.setFreqAppetiteIssues(logDetails.getFreqAppetiteIssues());
        existingLog.setObservation(logDetails.getObservation());

        // recalcula o score
        Integer totalScore = calculateTotalScore(existingLog);
        existingLog.setTeaScore(totalScore);

        TEALog savedRecord = teaLogRepository.save(existingLog);
        auditService.recordChange(AuditRecordType.REGISTRO_TEA, savedRecord.getId(), savedRecord.getPatient().getId());
        return savedRecord;
    }
}