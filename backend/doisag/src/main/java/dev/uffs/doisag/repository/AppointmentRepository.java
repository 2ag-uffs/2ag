package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByPrescriberIdAndDateTimeBetween(Long prescriberId, LocalDateTime startOfDay, LocalDateTime endOfDay);

    // consultas de um paciente num intervalo, pro grafico de evolucao
    List<Appointment> findByPatientIdAndDateTimeBetweenOrderByDateTimeAsc(
            Long patientId, LocalDateTime inicio, LocalDateTime fim);

    // consultas de um prescritor, pra n listar as do sistema inteiro
    List<Appointment> findByPrescriberId(Long prescriberId);

    // historico de um paciente da consulta mais recente pra mais antiga
    List<Appointment> findByPatientIdOrderByDateTimeDesc(Long patientId);
/**
 * aqui a gente caça no banco as consultas de um médico específico, num dia específico
 * o spring data jpa é divo e cria a query sozinho só pelo nome do método
 */
}
