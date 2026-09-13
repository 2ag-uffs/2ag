package dev.uffs.doisag.service;

import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.PainLog;
import dev.uffs.doisag.repository.PainLogRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.uffs.doisag.enums.ScaleType;

import java.util.Optional;

@Service
public class PainLogService {
    private final PainLogRepository painLogRepository;
    // aqui eh a injeçao do service q controla o status
    private final ScaleAssignmentService scaleAssignmentService;
    private final AuditService auditService;

    public PainLogService(PainLogRepository painLogRepository, ScaleAssignmentService scaleAssignmentService,
            AuditService auditService) {
        this.painLogRepository = painLogRepository;
        this.scaleAssignmentService = scaleAssignmentService;
        this.auditService = auditService;
    }

    // CREATE
    @Transactional
    public PainLog create(PainLog painLog) {
        // primeiro salva o registro de dor no banco
        PainLog savedLog = painLogRepository.save(painLog);

        // depois de salvar avisa o sistema pra dar baixa na tarefa
        if (savedLog.getPatient() != null) {
            auditService.recordCreation(AuditRecordType.REGISTRO_DOR, savedLog.getId(), savedLog.getPatient().getId());
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
        PainLog painLog = painLogRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Registro de dor não encontrado com o id: " + id));
        auditService.recordChartView(painLog.getPatient().getId());
        return painLog;
    }

    // UPDATE
    @Transactional
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

        PainLog savedRecord = painLogRepository.save(existingLog);
        auditService.recordChange(AuditRecordType.REGISTRO_DOR, savedRecord.getId(), savedRecord.getPatient().getId());
        return savedRecord;
    }
}