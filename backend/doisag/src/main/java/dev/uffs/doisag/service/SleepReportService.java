package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.WeeklySleepReportDTO;
import dev.uffs.doisag.model.SleepLog;
import dev.uffs.doisag.repository.SleepLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;

// criei outro service apenas pro report semanal para n bagunçar aquele q ja fiz e não misturar as logicas
@Service
public class SleepReportService {

    private final SleepLogRepository sleepLogRepository;

    public SleepReportService(SleepLogRepository sleepLogRepository) {
        this.sleepLogRepository = sleepLogRepository;
    }

    public WeeklySleepReportDTO generateWeeklyReport(Long patientId) {
        // busca os últimos 7 registros no banco
        List<SleepLog> recentLogs = sleepLogRepository.findTop7ByPatientIdOrderByAssessmentDateDesc(patientId);

        // se não tiver nenhum registro retorna um dto vazio pra não dar erro no front
        if (recentLogs.isEmpty()) {
            return new WeeklySleepReportDTO(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        // aqui a gente calcula a média de cada campo usando stream do java
        // orElse(0.0) é pra caso a lista esteja vazia mas a gente já tratou isso antes
        double avgTimeInBed = recentLogs.stream().mapToDouble(SleepLog::getTimeInBed).average().orElse(0.0);
        double avgTimeToFallAsleep = recentLogs.stream().mapToInt(SleepLog::getTimeToFallAsleep).average().orElse(0.0);
        double avgTimesWokenUp = recentLogs.stream().mapToInt(SleepLog::getTimesWokenUp).average().orElse(0.0);
        double avgTotalTimeAwake = recentLogs.stream().mapToInt(SleepLog::getTotalTimeAwake).average().orElse(0.0);
        double avgTotalSleepTime = recentLogs.stream().mapToDouble(SleepLog::getTotalSleepTime).average().orElse(0.0);
        double avgFatigue = recentLogs.stream().mapToInt(SleepLog::getFatigue).average().orElse(0.0);
        double avgStress = recentLogs.stream().mapToInt(SleepLog::getStress).average().orElse(0.0);
        double avgDaytimeSleepiness = recentLogs.stream().mapToInt(SleepLog::getDaytimeSleepiness).average().orElse(0.0);
        double avgInattention = recentLogs.stream().mapToInt(SleepLog::getInattention).average().orElse(0.0);
        double avgIrritability = recentLogs.stream().mapToInt(SleepLog::getIrritability).average().orElse(0.0);
        double avgPain = recentLogs.stream().mapToInt(SleepLog::getPain).average().orElse(0.0);

        // monta e retorna o dto com as médias calculadas
        return new WeeklySleepReportDTO(
                avgTimeInBed,
                avgTimeToFallAsleep,
                avgTimesWokenUp,
                avgTotalTimeAwake,
                avgTotalSleepTime,
                avgFatigue,
                avgStress,
                avgDaytimeSleepiness,
                avgInattention,
                avgIrritability,
                avgPain,
                recentLogs.size()
        );
    }
}