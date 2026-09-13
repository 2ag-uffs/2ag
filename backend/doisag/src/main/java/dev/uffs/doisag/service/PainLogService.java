package dev.uffs.doisag.service;

import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.PainLog;
import dev.uffs.doisag.repository.PainLogRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import dev.uffs.doisag.enums.ScaleType;

import java.util.Optional;

@Service
public class PainLogService {
    private final PainLogRepository painLogRepository;
    // aqui eh a injeçao do service q controla o status
    private final ScaleAssignmentService scaleAssignmentService;

    public PainLogService(PainLogRepository painLogRepository, ScaleAssignmentService scaleAssignmentService) {
        this.painLogRepository = painLogRepository;
        this.scaleAssignmentService = scaleAssignmentService;
    }

    // CREATE
    public PainLog create(PainLog painLog) {
        // primeiro salva o registro de dor no banco
        PainLog savedLog = painLogRepository.save(painLog);

        // depois de salvar avisa o sistema pra dar baixa na tarefa
        if (savedLog.getPatient() != null) {
            scaleAssignmentService.completeAssignedScale(
                    savedLog.getPatient().getId(),
                    ScaleType.REGISTRO_DOR
            );
        }
        // retorna a scala salva
        return savedLog;
    }

    // READ BY ID
    public PainLog getById(Long id) {
        return painLogRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado com o id: " + id));
    }

    // UPDATE
    public PainLog update(Long id, PainLog logDetails) {
        // busca o diário de dor ou lança uma exceção
        PainLog existingLog = painLogRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("diário de dor não encontrado com o id: " + id));

        // atualiza os campos
        existingLog.setAssessmentDate(logDetails.getAssessmentDate());
        // o paciente dono do registro n muda na edicao
        existingLog.setBasicActivityInterference(logDetails.getBasicActivityInterference());
        existingLog.setSocialActivityInterference(logDetails.getSocialActivityInterference());
        existingLog.setSleepInterference(logDetails.getSleepInterference());
        existingLog.setProductivityInterference(logDetails.getProductivityInterference());
        existingLog.setExtraMedication(logDetails.getExtraMedication());
        existingLog.setObservation(logDetails.getObservation());

        return painLogRepository.save(existingLog);
    }
}