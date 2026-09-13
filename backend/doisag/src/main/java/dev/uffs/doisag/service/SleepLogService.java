package dev.uffs.doisag.service;

import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.SleepLog;
import dev.uffs.doisag.repository.SleepLogRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.uffs.doisag.enums.ScaleType;

import java.time.Duration;
import java.util.Optional;

@Service
public class SleepLogService {
    private final SleepLogRepository sleepLogRepository;
    // aqui eh a injeçao do service q controla o status
    private final ScaleAssignmentService scaleAssignmentService;
    private final AuditService auditService;

    public SleepLogService(SleepLogRepository sleepLogRepository, ScaleAssignmentService scaleAssignmentService,
            AuditService auditService) {
        this.sleepLogRepository = sleepLogRepository;
        this.scaleAssignmentService = scaleAssignmentService;
        this.auditService = auditService;
    }

    // método pra centralizar a lógica de calc
    private void calculateSleepMetrics(SleepLog sleepLog) {
        // calcula o tempo na cama em minutos
        if (sleepLog.getBedTime() != null && sleepLog.getWakeUpTime() != null) {
            Duration duration = Duration.between(sleepLog.getBedTime(), sleepLog.getWakeUpTime());
            // se a duracao for negativa quer dizer que virou o dia
            // entao a gente soma 24h pra corrigir o calculo
            if (duration.isNegative()) {
                duration = duration.plusDays(1);
            }
            sleepLog.setTimeInBed((float) duration.toMinutes());
        }

        // calcula o tempo total que a pessoa ficou acordada no periodo de sono
        // eh a soma do tempo pra pegar no sono + o tempo que ficou acordada no meio da noite
        // se faltou algum dos dois, n da pra calcular o tempo acordado
        Integer totalTimeAwake = ScoreHelper.sumOrNull(
                sleepLog.getTimeToFallAsleep(), sleepLog.getTotalTimeAwakeDuringNight());
        sleepLog.setTotalTimeAwake(totalTimeAwake);

        // calcula o tempo total de sono de fato
        // eh o tempo na cama menos o tempo que ficou acordada
        Float totalSleepTime = (sleepLog.getTimeInBed() == null || totalTimeAwake == null)
                ? null
                : sleepLog.getTimeInBed() - totalTimeAwake;
        sleepLog.setTotalSleepTime(totalSleepTime);
    }

    // CREATE
    @Transactional
    public SleepLog create(SleepLog sleepLog) {
        // chama nosso metodo central pra fazer todos os calculos de tempo
        calculateSleepMetrics(sleepLog);

        // salva no banco
        SleepLog savedLog = sleepLogRepository.save(sleepLog);

        // depois de salvar, avisa o sistema pra dar baixa na tarefa
        if (savedLog.getPatient() != null) {
            auditService.recordCreation(AuditRecordType.REGISTRO_SONO, savedLog.getId(), savedLog.getPatient().getId());
            scaleAssignmentService.completeAssignedScale(
                    savedLog.getPatient().getId(),
                    ScaleType.REGISTRO_SONO
            );
        }
        // retorna a escala salva
        return savedLog;
    }

    // READ BY ID
    public SleepLog getById(Long id) {
        SleepLog sleepLog = sleepLogRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Registro de sono não encontrado com o id: " + id));
        auditService.recordChartView(sleepLog.getPatient().getId());
        return sleepLog;
    }

    // UPDATE
    @Transactional
    public SleepLog update(Long id, SleepLog logDetails) {
        // busca o registro de sono ou lanca uma excecao se nao achar
        SleepLog existingLog = sleepLogRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Registro de sono não encontrado com o id: " + id));

        // atualiza todos os campos com os novos dados vindos do frontend
        existingLog.setAssessmentDate(logDetails.getAssessmentDate());
        // o paciente dono do registro n muda na edicao
        existingLog.setBedTime(logDetails.getBedTime());
        existingLog.setWakeUpTime(logDetails.getWakeUpTime());
        existingLog.setTimeToFallAsleep(logDetails.getTimeToFallAsleep());
        existingLog.setTimesWokenUp(logDetails.getTimesWokenUp());
        existingLog.setTotalTimeAwakeDuringNight(logDetails.getTotalTimeAwakeDuringNight()); // importante atualizar esse
        existingLog.setCommonDay(logDetails.getCommonDay());
        existingLog.setFatigue(logDetails.getFatigue());
        existingLog.setStress(logDetails.getStress());
        existingLog.setDaytimeSleepiness(logDetails.getDaytimeSleepiness());
        existingLog.setInattention(logDetails.getInattention());
        existingLog.setIrritability(logDetails.getIrritability());
        existingLog.setPain(logDetails.getPain());
        existingLog.setHealthPerception(logDetails.getHealthPerception());
        existingLog.setPhysicalActivityTime(logDetails.getPhysicalActivityTime());
        existingLog.setTimeAwayFromHome(logDetails.getTimeAwayFromHome());
        existingLog.setUsedSleepMedication(logDetails.getUsedSleepMedication());
        existingLog.setAlcoholConsumption(logDetails.getAlcoholConsumption());
        existingLog.setNapsTime(logDetails.getNapsTime());
        existingLog.setCoffeeConsumption(logDetails.getCoffeeConsumption());
        existingLog.setNighttimeSmoking(logDetails.getNighttimeSmoking());

        // depois de atualizar os dados, a gente recalcula as metricas de tempo
        calculateSleepMetrics(existingLog);

        SleepLog savedRecord = sleepLogRepository.save(existingLog);
        auditService.recordChange(AuditRecordType.REGISTRO_SONO, savedRecord.getId(), savedRecord.getPatient().getId());
        return savedRecord;
    }

}