package dev.uffs.doisag.repository;

import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.model.ScaleTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ScaleTaskRepository extends JpaRepository<ScaleTask, Long> {

    List<ScaleTask> findByPatientIdAndStatusOrderByPeriodEndAsc(Long patientId, ScaleTaskStatus status);

    List<ScaleTask> findByPrescriberIdAndStatusOrderByPeriodEndAsc(Long prescriberId, ScaleTaskStatus status);

    List<ScaleTask> findByPatientIdOrderByPeriodStartDesc(Long patientId);

    // a tarefa aberta de uma escala, q recebe a resposta do paciente
    Optional<ScaleTask> findFirstByPatientIdAndScaleTypeAndStatusOrderByPeriodStartDesc(
            Long patientId, ScaleType scaleType, ScaleTaskStatus status);

    // a ultima vez q essa escala foi enviada, respondida ou n
    Optional<ScaleTask> findFirstByPatientIdAndScaleTypeOrderByPeriodStartDesc(Long patientId, ScaleType scaleType);

    // as pendencias q passaram do prazo e o job precisa fechar
    List<ScaleTask> findByStatusAndPeriodEndBefore(ScaleTaskStatus status, LocalDate day);

    long countByPatientIdAndStatus(Long patientId, ScaleTaskStatus status);
}
