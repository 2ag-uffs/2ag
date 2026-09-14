package dev.uffs.doisag.repository;

import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.model.ScaleResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ScaleResponseRepository extends JpaRepository<ScaleResponse, Long> {

    List<ScaleResponse> findByPatientIdOrderByPeriodStartDesc(Long patientId);

    List<ScaleResponse> findByPatientIdAndScaleTypeOrderByPeriodStartDesc(Long patientId, ScaleType scaleType);

    // as respostas de um periodo, q o grafico de evolucao usa
    List<ScaleResponse> findByPatientIdAndScaleTypeAndPeriodStartBetweenOrderByPeriodStartAsc(
            Long patientId, ScaleType scaleType, LocalDate from, LocalDate to);

    // as respostas de qualquer escala no periodo, pros comentarios (RF07)
    List<ScaleResponse> findByPatientIdAndPeriodStartBetweenOrderByPeriodStartAsc(
            Long patientId, LocalDate from, LocalDate to);

    // o dia q ja foi preenchido no diario, pq o mesmo dia n vira dois registros
    Optional<ScaleResponse> findByPatientIdAndScaleTypeAndPeriodStart(
            Long patientId, ScaleType scaleType, LocalDate periodStart);

    List<ScaleResponse> findByTaskIdOrderByPeriodStartAsc(Long taskId);

    List<ScaleResponse> findByAppointmentIdOrderByPeriodStartAsc(Long appointmentId);

    long countByTaskId(Long taskId);
}
