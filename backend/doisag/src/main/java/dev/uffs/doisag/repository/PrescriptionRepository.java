package dev.uffs.doisag.repository;

import dev.uffs.doisag.enums.PrescriptionStatus;
import dev.uffs.doisag.model.Prescription;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    // consulta com prescricao q ainda n foi anulada n pode ser anulada
    boolean existsByAppointmentIdAndAnnulmentAnnulledAtIsNull(Long appointmentId);

    // prescricoes de um paciente da consulta mais recente pra mais antiga
    // a consulta e quem a fez vem junto. as duas listas da receita vem em lote, pelo BatchSize
    @EntityGraph(attributePaths = {"appointment", "appointment.patient", "appointment.prescriber"})
    List<Prescription> findByAppointmentPatientIdOrderByAppointmentDateTimeDescCreatedAtDesc(Long patientId);

    // a prescricao q esta valendo pro paciente
    List<Prescription> findByAppointmentPatientIdAndStatusAndAnnulmentAnnulledAtIsNull(Long patientId,
                                                                                     PrescriptionStatus status);
}
