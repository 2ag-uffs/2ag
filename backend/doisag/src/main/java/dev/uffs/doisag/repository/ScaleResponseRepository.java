package dev.uffs.doisag.repository;

import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.model.ScaleResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ScaleResponseRepository extends JpaRepository<ScaleResponse, Long> {

    // so o paciente da resposta, sem carregar ela: quem vai mudar a resposta trava o paciente
    // primeiro e so dps le a resposta travada, pra ler o q o outro lado acabou de gravar
    @Query("select response.patient.id from ScaleResponse response where response.id = :id")
    Optional<Long> findPatientIdById(@Param("id") Long id);

    List<ScaleResponse> findByPatientIdOrderByPeriodStartDesc(Long patientId);

    List<ScaleResponse> findByPatientIdAndScaleTypeOrderByPeriodStartDesc(Long patientId, ScaleType scaleType);

    // as respostas de um periodo, q o grafico de evolucao usa
    List<ScaleResponse> findByPatientIdAndScaleTypeAndPeriodStartBetweenOrderByPeriodStartAsc(
            Long patientId, ScaleType scaleType, LocalDate from, LocalDate to);

    // as respostas de qualquer escala no periodo, pros comentarios (RF07)
    List<ScaleResponse> findByPatientIdAndPeriodStartBetweenOrderByPeriodStartAsc(
            Long patientId, LocalDate from, LocalDate to);

    // o dia q ja foi preenchido no diario, pq o mesmo dia n vira dois registros
    // a anulada fica de fora: ela vira historico e o dia pode ser respondido de novo
    Optional<ScaleResponse> findByPatientIdAndScaleTypeAndPeriodStartAndAnnulmentAnnulledAtIsNull(
            Long patientId, ScaleType scaleType, LocalDate periodStart);

    List<ScaleResponse> findByAppointmentIdOrderByPeriodStartAsc(Long appointmentId);

    // o mini exame valido daquela consulta: barra o segundo exame e a anulacao da consulta
    boolean existsByAppointmentIdAndAnnulmentAnnulledAtIsNull(Long appointmentId);

    // resposta anulada n conta como respondida em lugar nenhum
    long countByTaskIdAndAnnulmentAnnulledAtIsNull(Long taskId);
}
