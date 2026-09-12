package dev.uffs.doisag.service;

import dev.uffs.doisag.infra.ResourceNotFoundException;
import dev.uffs.doisag.model.SleepLog;
import dev.uffs.doisag.repository.SleepLogRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import dev.uffs.doisag.enums.ScaleType;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class SleepLogService {
    private final SleepLogRepository sleepLogRepository;
    // aqui eh a injeçao do service q controla o status
    private final ScaleAssignmentService scaleAssignmentService;

    public SleepLogService(SleepLogRepository sleepLogRepository, ScaleAssignmentService scaleAssignmentService) {
        this.sleepLogRepository = sleepLogRepository;
        this.scaleAssignmentService = scaleAssignmentService;
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
            sleepLog.setTimeInBed(duration.toMinutes());
        }

        // calcula o tempo total que a pessoa ficou acordada no periodo de sono
        // eh a soma do tempo pra pegar no sono + o tempo que ficou acordada no meio da noite
        int totalTimeAwake = sleepLog.getTimeToFallAsleep() + sleepLog.getTotalTimeAwakeDuringNight();
        sleepLog.setTotalTimeAwake(totalTimeAwake);

        // calcula o tempo total de sono de fato
        // eh o tempo na cama menos o tempo que ficou acordada
        float totalSleepTime = sleepLog.getTimeInBed() - totalTimeAwake;
        sleepLog.setTotalSleepTime(totalSleepTime);
    }

    // CREATE
    public SleepLog create(SleepLog sleepLog) {
        // chama nosso metodo central pra fazer todos os calculos de tempo
        calculateSleepMetrics(sleepLog);

        // salva no banco
        SleepLog savedLog = sleepLogRepository.save(sleepLog);

        // depois de salvar, avisa o sistema pra dar baixa na tarefa
        if (savedLog.getPatient() != null) {
            scaleAssignmentService.completeAssignedScale(
                    savedLog.getPatient().getId(),
                    ScaleType.REGISTRO_SONO
            );
        }
        // retorna a escala salva
        return savedLog;
    }

    // READ ALL
    public List<SleepLog> getAll() {
        return sleepLogRepository.findAll();
    }

    // READ BY ID
    public SleepLog getById(Long id) {
        return sleepLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente não encontrado com o id: " + id));
    }

    // UPDATE
    public SleepLog update(Long id, SleepLog logDetails) {
        // busca o registro de sono ou lanca uma excecao se nao achar
        SleepLog existingLog = sleepLogRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Registro de sono não encontrado com o id: " + id));

        // atualiza todos os campos com os novos dados vindos do frontend
        existingLog.setAssessmentDate(logDetails.getAssessmentDate());
        existingLog.setPatient(logDetails.getPatient());
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

        return sleepLogRepository.save(existingLog);
    }


    // DELETE
    public void delete(Long id) {
        // verifica se o registro de sono existe antes de deletar
        if (!sleepLogRepository.existsById(id)) {
            throw new EntityNotFoundException("registro de sono não encontrado com o id: " + id);
        }
        sleepLogRepository.deleteById(id);
    }
}