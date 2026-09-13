package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {
    // método para buscar prescrições por consulta
    List<Prescription> findByAppointmentId(Long appointmentId);

    // prescricoes de um paciente da consulta mais recente pra mais antiga
    List<Prescription> findByAppointmentPatientIdOrderByAppointmentDateTimeDesc(Long patientId);
}
