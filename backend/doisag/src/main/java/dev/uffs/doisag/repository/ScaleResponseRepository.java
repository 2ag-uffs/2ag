package dev.uffs.doisag.repository;

import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.model.ScaleResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ScaleResponseRepository extends JpaRepository<ScaleResponse, Long> {

    // so o paciente da resposta, sem carregar ela: quem vai mudar a resposta trava o paciente
    // primeiro e so dps le a resposta travada, pra ler o q o outro lado acabou de gravar
    @Query("select response.patient.id from ScaleResponse response where response.id = :id")
    Optional<Long> findPatientIdById(@Param("id") Long id);

    // so a escala da resposta, sem carregar ela: o paciente n abre o meem
    @Query("select response.scaleType from ScaleResponse response where response.id = :id")
    Optional<ScaleType> findScaleTypeById(@Param("id") Long id);

    // a consulta do meem, sem carregar a resposta: a anulacao dele trava a consulta antes do paciente
    // nas outras escalas vem vazio
    @Query("select response.appointment.id from ScaleResponse response where response.id = :id")
    Optional<Long> findAppointmentIdById(@Param("id") Long id);

    // no mesmo dia a mais nova vem antes: corrigir eh anular e responder de novo, e a anulada vinha em cima
    List<ScaleResponse> findByPatientIdOrderByPeriodStartDescIdDesc(Long patientId);

    List<ScaleResponse> findByPatientIdAndScaleTypeOrderByPeriodStartDescIdDesc(Long patientId, ScaleType scaleType);

    // as respostas q comecam dentro de um periodo, pros dias do diario de uma tarefa
    List<ScaleResponse> findByPatientIdAndScaleTypeAndPeriodStartBetweenOrderByPeriodStartAsc(
            Long patientId, ScaleType scaleType, LocalDate from, LocalDate to);

    // as respostas com ao menos um dia dentro do periodo, q o grafico de evolucao usa: a semana de dor ou de TEA
    // q comecou antes do corte e terminou dps dele entra (decisao da dona do projeto, novembro de 2026)
    @Query("select response from ScaleResponse response "
            + "where response.patient.id = :patientId and response.scaleType = :scaleType "
            + "and response.periodEnd >= :from and response.periodStart <= :to "
            + "order by response.periodStart asc, response.id asc")
    List<ScaleResponse> findOfScaleTouchingPeriod(@Param("patientId") Long patientId,
                                                  @Param("scaleType") ScaleType scaleType,
                                                  @Param("from") LocalDate from,
                                                  @Param("to") LocalDate to);

    // o msm, de qualquer escala, pros comentarios (RF07)
    @Query("select response from ScaleResponse response "
            + "where response.patient.id = :patientId "
            + "and response.periodEnd >= :from and response.periodStart <= :to "
            + "order by response.periodStart asc, response.id asc")
    List<ScaleResponse> findTouchingPeriod(@Param("patientId") Long patientId,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    // o dia q ja foi preenchido no diario, pq o mesmo dia n vira dois registros
    // a anulada fica de fora: ela vira historico e o dia pode ser respondido de novo
    Optional<ScaleResponse> findByPatientIdAndScaleTypeAndPeriodStartAndAnnulmentAnnulledAtIsNull(
            Long patientId, ScaleType scaleType, LocalDate periodStart);

    // a semana respondida q termina naquele dia, na escala de periodo
    boolean existsByPatientIdAndScaleTypeAndPeriodEndAndAnnulmentAnnulledAtIsNull(
            Long patientId, ScaleType scaleType, LocalDate periodEnd);

    List<ScaleResponse> findByAppointmentIdOrderByPeriodStartAsc(Long appointmentId);

    // o mini exame valido daquela consulta: barra o segundo exame e a anulacao da consulta
    boolean existsByAppointmentIdAndAnnulmentAnnulledAtIsNull(Long appointmentId);

    // resposta anulada n conta como respondida em lugar nenhum
    long countByTaskIdAndAnnulmentAnnulledAtIsNull(Long taskId);

    // so as q chegaram com a tarefa aberta: o dia atrasado do diario n deixa a semana respondida
    long countByTaskIdAndFilledLateFalseAndAnnulmentAnnulledAtIsNull(Long taskId);

    // a tarefa teve uma resposta anulada desde aquela hora, pro job n fechar na mesma manha a semana reaberta
    boolean existsByTaskIdAndAnnulmentAnnulledAtGreaterThanEqual(Long taskId, LocalDateTime from);

    // a ultima resposta anulada da tarefa, q da a data em q a tela abre a tarefa reaberta
    Optional<ScaleResponse> findFirstByTaskIdAndAnnulmentAnnulledAtIsNotNullOrderByAnnulmentAnnulledAtDesc(
            Long taskId);

    // um id de tarefa por resposta valida ligada a ela, pros indicadores contarem os dias do diario (RF39)
    @Query("select response.task.id from ScaleResponse response "
            + "where response.task.prescriber.id = :prescriberId "
            + "and response.task.periodEnd between :from and :to "
            + "and response.annulment.annulledAt is null")
    List<Long> findTaskIdsOfValidAnswers(@Param("prescriberId") Long prescriberId,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to);

    // o mesmo, sem o dia preenchido dps de a tarefa fechar, q conta como atraso (revisao das regras R6)
    @Query("select response.task.id from ScaleResponse response "
            + "where response.task.prescriber.id = :prescriberId "
            + "and response.task.periodEnd between :from and :to "
            + "and response.annulment.annulledAt is null and response.filledLate = false")
    List<Long> findTaskIdsOfOnTimeAnswers(@Param("prescriberId") Long prescriberId,
                                          @Param("from") LocalDate from,
                                          @Param("to") LocalDate to);
}
