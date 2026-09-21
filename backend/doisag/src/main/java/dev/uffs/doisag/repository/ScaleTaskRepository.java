package dev.uffs.doisag.repository;

import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.model.ScaleTask;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ScaleTaskRepository extends JpaRepository<ScaleTask, Long> {

    List<ScaleTask> findByPatientIdAndStatusOrderByPeriodEndAsc(Long patientId, ScaleTaskStatus status);

    // a mesma lista cortada pelo Pageable, q eh o q o painel precisa
    List<ScaleTask> findByPatientIdAndStatusOrderByPeriodEndAsc(Long patientId, ScaleTaskStatus status,
                                                               Pageable pageable);

    List<ScaleTask> findByPrescriberIdAndStatusOrderByPeriodEndAsc(Long prescriberId, ScaleTaskStatus status);

    List<ScaleTask> findByPatientIdOrderByPeriodStartDesc(Long patientId);

    // a tarefa aberta de uma escala, q recebe a resposta do paciente
    Optional<ScaleTask> findFirstByPatientIdAndScaleTypeAndStatusOrderByPeriodStartDesc(
            Long patientId, ScaleType scaleType, ScaleTaskStatus status);

    // a ultima vez q essa escala foi enviada, respondida ou n
    Optional<ScaleTask> findFirstByPatientIdAndScaleTypeOrderByPeriodStartDesc(Long patientId, ScaleType scaleType);

    // as pendencias q passaram do prazo e o job precisa fechar
    List<ScaleTask> findByStatusAndPeriodEndBefore(ScaleTaskStatus status, LocalDate day);

    // as tarefas q vencem logo e ainda n receberam lembrete (RF34)
    List<ScaleTask> findByStatusAndPeriodEndLessThanEqualAndReminderSentAtIsNull(
            ScaleTaskStatus status, LocalDate day);

    // as escalas vencidas sem resposta q o prescritor ve no painel (RF03 e RF32)
    // entram as q o job ja fechou e as q venceram antes de ele rodar
    //
    // a conta de quem n tem resposta fica no banco e o corte vem no Pageable:
    // antes o painel carregava todas as vencidas e contava uma por uma pra
    // jogar fora tudo menos cinco
    @Query("select task from ScaleTask task "
            + "where task.prescriber.id = :prescriberId and task.status in :statuses "
            + "and task.periodEnd < :day "
            + "and not exists (select response.id from ScaleResponse response "
            + "where response.task = task and response.annulment.annulledAt is null) "
            + "order by task.periodEnd desc")
    List<ScaleTask> findLateWithoutAnswers(@Param("prescriberId") Long prescriberId,
                                           @Param("statuses") Collection<ScaleTaskStatus> statuses,
                                           @Param("day") LocalDate day,
                                           Pageable pageable);

    long countByPatientIdAndStatus(Long patientId, ScaleTaskStatus status);
}
