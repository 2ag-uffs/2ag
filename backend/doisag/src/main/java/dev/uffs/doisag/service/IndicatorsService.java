package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.PilotIndicatorsDTO;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.model.PatientInvite;
import dev.uffs.doisag.model.ScaleTask;
import dev.uffs.doisag.repository.PatientInviteRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.ScaleResponseRepository;
import dev.uffs.doisag.repository.ScaleTaskRepository;
import dev.uffs.doisag.repository.TreatmentProtocolRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// indicadores do piloto (RF39): pacientes no ciclo e formularios respondidos no prazo
// so conta tarefa com prazo ja encerrado, entao o periodo termina no maximo ontem
@Service
public class IndicatorsService {

    public static final int GOAL_PERCENT = 70;
    // um ciclo inteiro de acompanhamento (RN01)
    public static final int DEFAULT_DAYS = 90;

    public static final String INCOMPLETE_RANGE_MESSAGE = "Informe as duas datas do período, ou nenhuma das duas";
    public static final String INVALID_RANGE_MESSAGE = "A data inicial precisa ser igual ou anterior à data final";
    public static final String OPEN_DEADLINE_MESSAGE =
            "O período precisa terminar até ontem, porque o prazo de hoje ainda está correndo";

    private final PatientRepository patientRepository;
    private final TreatmentProtocolRepository protocolRepository;
    private final PatientInviteRepository inviteRepository;
    private final ScaleTaskRepository taskRepository;
    private final ScaleResponseRepository responseRepository;
    private final ScaleTaskService scaleTaskService;

    public IndicatorsService(PatientRepository patientRepository,
                             TreatmentProtocolRepository protocolRepository,
                             PatientInviteRepository inviteRepository,
                             ScaleTaskRepository taskRepository,
                             ScaleResponseRepository responseRepository,
                             ScaleTaskService scaleTaskService) {
        this.patientRepository = patientRepository;
        this.protocolRepository = protocolRepository;
        this.inviteRepository = inviteRepository;
        this.taskRepository = taskRepository;
        this.responseRepository = responseRepository;
        this.scaleTaskService = scaleTaskService;
    }

    @Transactional(readOnly = true)
    public PilotIndicatorsDTO getIndicators(Long prescriberId, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        if (from == null && to == null) {
            to = today.minusDays(1);
            from = to.minusDays(DEFAULT_DAYS - 1L);
        }
        if (from == null || to == null) {
            throw new BusinessException(INCOMPLETE_RANGE_MESSAGE);
        }
        if (from.isAfter(to)) {
            throw new BusinessException(INVALID_RANGE_MESSAGE);
        }
        if (!to.isBefore(today)) {
            throw new BusinessException(OPEN_DEADLINE_MESSAGE);
        }

        // foto de hoje
        long activePatients = patientRepository.countByPrescriberIdAndArchivedAtIsNullAndActiveTrue(prescriberId);
        long patientsInCycle = protocolRepository.countPatientsInCycle(prescriberId, today);

        // convites do periodo, sem os cancelados
        Set<Long> patientsWithProtocol = new HashSet<>(protocolRepository.findPatientIdsWithProtocol(prescriberId));
        List<PatientInvite> invites = inviteRepository.findByPrescriberIdAndCreatedAtBetween(prescriberId,
                from.atStartOfDay(), to.atTime(LocalTime.MAX));
        LocalDateTime now = LocalDateTime.now();
        long invitesSent = 0;
        long invitesRegistered = 0;
        long invitesInCycle = 0;
        long invitesStillOpen = 0;
        for (PatientInvite invite : invites) {
            if (invite.getCancelledAt() != null) {
                continue;
            }
            invitesSent++;
            if (invite.getPatient() != null) {
                invitesRegistered++;
                if (patientsWithProtocol.contains(invite.getPatient().getId())) {
                    invitesInCycle++;
                }
            } else if (invite.isUsableAt(now)) {
                invitesStillOpen++;
            }
        }

        // quantos dias validos cada tarefa tem ligados nela
        Map<Long, Integer> validDaysByTask = new HashMap<>();
        for (Long taskId : responseRepository.findTaskIdsOfValidAnswers(prescriberId, from, to)) {
            validDaysByTask.merge(taskId, 1, Integer::sum);
        }

        Map<ScaleType, ScaleCount> countsByScale = new EnumMap<>(ScaleType.class);
        Set<Long> patientsWithForms = new HashSet<>();
        for (ScaleTask task : taskRepository.findByPrescriberIdAndPeriodEndBetween(prescriberId, from, to)) {
            patientsWithForms.add(task.getPatient().getId());
            ScaleCount count = countsByScale.computeIfAbsent(task.getScaleType(), scaleType -> new ScaleCount());
            count.formsDue++;
            int validDays = validDaysByTask.getOrDefault(task.getId(), 0);
            boolean onTime;
            if (scaleTaskService.isDaily(task.getScaleType())) {
                // no diario basta um dia, a msm regra q fecha a tarefa como respondida
                onTime = validDays > 0;
                count.daysExpected += ChronoUnit.DAYS.between(task.getPeriodStart(), task.getPeriodEnd()) + 1;
                count.daysFilled += validDays;
            } else {
                // reabrir apaga o answeredAt, entao resposta anulada sem outra no lugar n conta
                onTime = task.getAnsweredAt() != null && !task.getAnsweredAt().isAfter(task.getPeriodEnd());
            }
            if (onTime) {
                count.formsOnTime++;
            }
        }

        long formsDue = 0;
        long formsOnTime = 0;
        long diaryDaysExpected = 0;
        long diaryDaysFilled = 0;
        List<PilotIndicatorsDTO.ScaleRowDTO> scales = new ArrayList<>();
        for (Map.Entry<ScaleType, ScaleCount> entry : countsByScale.entrySet()) {
            ScaleType scaleType = entry.getKey();
            ScaleCount count = entry.getValue();
            formsDue += count.formsDue;
            formsOnTime += count.formsOnTime;
            diaryDaysExpected += count.daysExpected;
            diaryDaysFilled += count.daysFilled;
            scales.add(new PilotIndicatorsDTO.ScaleRowDTO(
                    scaleType,
                    scaleType.getDisplayName(),
                    scaleTaskService.isDaily(scaleType),
                    count.formsDue,
                    count.formsOnTime,
                    percentOf(count.formsOnTime, count.formsDue),
                    count.daysExpected,
                    count.daysFilled));
        }

        return new PilotIndicatorsDTO(from, to, GOAL_PERCENT,
                activePatients, patientsInCycle, patientsWithForms.size(),
                invitesSent, invitesRegistered, invitesInCycle, invitesStillOpen,
                formsDue, formsOnTime, percentOf(formsOnTime, formsDue),
                diaryDaysExpected, diaryDaysFilled, percentOf(diaryDaysFilled, diaryDaysExpected),
                scales);
    }

    // arredonda pra baixo pra 69,6 n aparecer como meta batida
    private Integer percentOf(long part, long total) {
        if (total == 0) {
            return null;
        }
        return (int) (part * 100 / total);
    }

    private static class ScaleCount {
        long formsDue;
        long formsOnTime;
        long daysExpected;
        long daysFilled;
    }
}
