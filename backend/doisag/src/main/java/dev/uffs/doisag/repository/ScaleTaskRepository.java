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

    List<ScaleTask> findByPatientIdOrderByPeriodStartDesc(Long patientId);

    // a tarefa aberta de uma escala, q recebe a resposta do paciente
    Optional<ScaleTask> findFirstByPatientIdAndScaleTypeAndStatusOrderByPeriodStartDesc(
            Long patientId, ScaleType scaleType, ScaleTaskStatus status);

    // a ultima tarefa de uma escala, seja qual for a situacao: a resposta atrasada so fica com ela
    Optional<ScaleTask> findFirstByPatientIdAndScaleTypeOrderByPeriodStartDescIdDesc(Long patientId,
                                                                                     ScaleType scaleType);

    // todas as tarefas abertas de uma escala, pq no diario pode ter a antiga e a atual ao mesmo tempo
    List<ScaleTask> findByPatientIdAndScaleTypeAndStatusOrderByPeriodStartDesc(
            Long patientId, ScaleType scaleType, ScaleTaskStatus status);

    // as pendencias q passaram do prazo e o job precisa fechar
    List<ScaleTask> findByStatusAndPeriodEndBefore(ScaleTaskStatus status, LocalDate day);

    // as tarefas q vencem logo e ainda n receberam lembrete (RF34)
    List<ScaleTask> findByStatusAndPeriodEndLessThanEqualAndReminderSentAtIsNull(
            ScaleTaskStatus status, LocalDate day);

    // so o paciente da tarefa, sem carregar ela: o job trava o paciente antes de ler a tarefa travada
    @Query("select task.patient.id from ScaleTask task where task.id = :id")
    Optional<Long> findPatientIdById(@Param("id") Long id);

    // as escalas vencidas sem resposta q o prescritor ve no painel (RF03 e RF32)
    // entram as q o job ja fechou e as q venceram antes de ele rodar
    // paciente arquivado fica de fora: o arquivamento fecha as escalas abertas no dia, e elas
    // viravam alerta de atraso de quem ja saiu do acompanhamento
    // a nao respondida sai qnd ganha resposta atrasada: a de escala fica ligada nela e a anamnese, q n
    // tem ligacao, marca o dia em answeredAt
    //
    // a conta de quem n tem resposta fica no banco e o corte vem no Pageable:
    // antes o painel carregava todas as vencidas e contava uma por uma pra
    // jogar fora tudo menos cinco
    @Query("select task from ScaleTask task "
            + "where task.prescriber.id = :prescriberId and task.status in :statuses "
            + "and task.periodEnd < :day and task.patient.archivedAt is null and task.answeredAt is null "
            + "and not exists (select response.id from ScaleResponse response "
            + "where response.task = task and response.annulment.annulledAt is null) "
            + "order by task.periodEnd desc")
    List<ScaleTask> findLateWithoutAnswers(@Param("prescriberId") Long prescriberId,
                                           @Param("statuses") Collection<ScaleTaskStatus> statuses,
                                           @Param("day") LocalDate day,
                                           Pageable pageable);

    // o total das vencidas com a mesma regra da lista, pq o painel mostra so cinco
    @Query("select count(task) from ScaleTask task "
            + "where task.prescriber.id = :prescriberId and task.status in :statuses "
            + "and task.periodEnd < :day and task.patient.archivedAt is null and task.answeredAt is null "
            + "and not exists (select response.id from ScaleResponse response "
            + "where response.task = task and response.annulment.annulledAt is null)")
    long countLateWithoutAnswers(@Param("prescriberId") Long prescriberId,
                                 @Param("statuses") Collection<ScaleTaskStatus> statuses,
                                 @Param("day") LocalDate day);

    long countByPatientIdAndStatus(Long patientId, ScaleTaskStatus status);

    // as tarefas com prazo no periodo dos indicadores, respondidas ou n (RF39)
    List<ScaleTask> findByPrescriberIdAndPeriodEndBetween(Long prescriberId, LocalDate from, LocalDate to);
}
