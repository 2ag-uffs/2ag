package dev.uffs.doisag.repository;

import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

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

    // proximos pedidos e consultas do paciente
    List<Appointment> findByPatientIdAndDateTimeAfterOrderByDateTimeAsc(Long patientId, LocalDateTime moment);

    // consultas de um paciente num intervalo pro grafico de evolucao
    List<Appointment> findByPatientIdAndDateTimeBetweenOrderByDateTimeAsc(Long patientId, LocalDateTime start,
                                                                           LocalDateTime end);

    // historico de um paciente da consulta mais recente pra mais antiga
    List<Appointment> findByPatientIdOrderByDateTimeDesc(Long patientId);
}
