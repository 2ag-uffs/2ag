package dev.uffs.doisag.service;

import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.TEALog;
import dev.uffs.doisag.repository.TEALogRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import dev.uffs.doisag.enums.ScaleType;

import java.util.Optional;

@Service
public class TEALogService {
    private final TEALogRepository teaLogRepository;
    // aqui eh a injeçao do service q controla o status
    private final ScaleAssignmentService scaleAssignmentService;

    public TEALogService(TEALogRepository teaLogRepository, ScaleAssignmentService scaleAssignmentService) {
        this.teaLogRepository = teaLogRepository;
        this.scaleAssignmentService = scaleAssignmentService;
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
    public TEALog create(TEALog teaLog) {
        // calcula o score
        Integer totalScore = calculateTotalScore(teaLog);
        teaLog.setTeaScore(totalScore);

        // salva no banco
        TEALog savedScale = teaLogRepository.save(teaLog);

        // usar o service para marcar como concluido
        if (savedScale.getPatient() != null) {
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
        return teaLogRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado com o id: " + id));
    }

    // UPDATE
    public TEALog update(Long id, TEALog logDetails) {
        TEALog existingLog = teaLogRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("registro tea não encontrado com o id: " + id));

        // atualiza os campos
        existingLog.setAssessmentDate(logDetails.getAssessmentDate());
        existingLog.setPatient(logDetails.getPatient());
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

        return teaLogRepository.save(existingLog);
    }
}