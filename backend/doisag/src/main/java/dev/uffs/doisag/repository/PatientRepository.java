package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {
    long countByPrescriberId(Long prescriberId);
    // conta quantos pacientes um prescritor específico tem na carteira dele
    // vai ser bom pro card de pacientes ativos no dashboard

    List<Patient> findAllByPrescriberId(Long prescriberId);


}
