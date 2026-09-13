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
    private final AuditService auditService;

    public SleepReportService(SleepLogRepository sleepLogRepository, AuditService auditService) {
        this.sleepLogRepository = sleepLogRepository;
        this.auditService = auditService;
    }

    public WeeklySleepReportDTO generateWeeklyReport(Long patientId) {
        auditService.recordChartView(patientId);
        // busca os últimos 7 registros no banco
        List<SleepLog> recentLogs = sleepLogRepository.findTop7ByPatientIdOrderByAssessmentDateDesc(patientId);

        // se não tiver nenhum registro retorna um dto vazio pra não dar erro no front
        if (recentLogs.isEmpty()) {
            return new WeeklySleepReportDTO(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        // aqui a gente calcula a média de cada campo usando stream do java
        // quem n respondeu fica de fora da media, em vez de entrar como zero
        double avgTimeInBed = recentLogs.stream().map(SleepLog::getTimeInBed).filter(java.util.Objects::nonNull).mapToDouble(Float::floatValue).average().orElse(0.0);
        double avgTimeToFallAsleep = ScoreHelper.averageIgnoringNulls(recentLogs.stream().map(SleepLog::getTimeToFallAsleep).toList());
        double avgTimesWokenUp = ScoreHelper.averageIgnoringNulls(recentLogs.stream().map(SleepLog::getTimesWokenUp).toList());
        double avgTotalTimeAwake = ScoreHelper.averageIgnoringNulls(recentLogs.stream().map(SleepLog::getTotalTimeAwake).toList());
        double avgTotalSleepTime = recentLogs.stream().map(SleepLog::getTotalSleepTime).filter(java.util.Objects::nonNull).mapToDouble(Float::floatValue).average().orElse(0.0);
        double avgFatigue = ScoreHelper.averageIgnoringNulls(recentLogs.stream().map(SleepLog::getFatigue).toList());
        double avgStress = ScoreHelper.averageIgnoringNulls(recentLogs.stream().map(SleepLog::getStress).toList());
        double avgDaytimeSleepiness = ScoreHelper.averageIgnoringNulls(recentLogs.stream().map(SleepLog::getDaytimeSleepiness).toList());
        double avgInattention = ScoreHelper.averageIgnoringNulls(recentLogs.stream().map(SleepLog::getInattention).toList());
        double avgIrritability = ScoreHelper.averageIgnoringNulls(recentLogs.stream().map(SleepLog::getIrritability).toList());
        double avgPain = ScoreHelper.averageIgnoringNulls(recentLogs.stream().map(SleepLog::getPain).toList());

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