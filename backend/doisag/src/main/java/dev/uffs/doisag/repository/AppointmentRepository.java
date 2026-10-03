package dev.uffs.doisag.repository;

import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.model.Appointment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    // trava a linha da consulta ate o fim da transacao: uma mudanca, um mini exame ou uma
    // receita de cada vez nela. MANDATORY: fora de uma transacao a trava soltaria na hora
    @Transactional(propagation = Propagation.MANDATORY)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select appointment from Appointment appointment where appointment.id = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") Long id);

    // consultas de um prescritor num intervalo pro painel e pra conta de horario ocupado
    // as consultas de um dia inteiro, q o lembrete do dia seguinte usa (RF34)
    List<Appointment> findByDateTimeBetween(LocalDateTime start, LocalDateTime end);

    List<Appointment> findByPrescriberIdAndDateTimeBetween(Long prescriberId, LocalDateTime start, LocalDateTime end);

    // agenda de um prescritor num intervalo em ordem de horario
    List<Appointment> findByPrescriberIdAndDateTimeBetweenOrderByDateTimeAsc(Long prescriberId, LocalDateTime start,
                                                                              LocalDateTime end);

    // agenda inteira de um prescritor
    List<Appointment> findByPrescriberIdOrderByDateTimeAsc(Long prescriberId);

    // pedidos de um prescritor numa situacao a partir de um momento tipo os q esperam resposta
    List<Appointment> findByPrescriberIdAndStatusAndDateTimeAfterOrderByDateTimeAsc(Long prescriberId,
                                                                                    AppointmentStatus status,
                                                                                    LocalDateTime moment);

    // a mesma lista cortada pelo Pageable, q eh o q o painel precisa
    List<Appointment> findByPrescriberIdAndStatusAndDateTimeAfterOrderByDateTimeAsc(Long prescriberId,
                                                                                    AppointmentStatus status,
                                                                                    LocalDateTime moment,
                                                                                    Pageable pageable);

    // proximos pedidos e consultas do paciente
    List<Appointment> findByPatientIdAndDateTimeAfterOrderByDateTimeAsc(Long patientId, LocalDateTime moment);

    // pedidos q passaram da data e ninguem respondeu, q o job do dia fecha (RF11)
    List<Appointment> findByStatusAndDateTimeBefore(AppointmentStatus status, LocalDateTime moment);

    // quantos pedidos o paciente ja deixou em aberto, pq cada um segura um horario
    long countByPatientIdAndStatusAndDateTimeAfter(Long patientId, AppointmentStatus status, LocalDateTime moment);

    // consultas de um paciente num intervalo pro grafico de evolucao
    List<Appointment> findByPatientIdAndDateTimeBetweenOrderByDateTimeAsc(Long patientId, LocalDateTime start,
                                                                           LocalDateTime end);

    // historico de um paciente da consulta mais recente pra mais antiga
    // paciente e prescritor vem na mesma consulta, senao cada linha fazia mais um select
    @EntityGraph(attributePaths = {"patient", "prescriber"})
    List<Appointment> findByPatientIdOrderByDateTimeDesc(Long patientId);
}
