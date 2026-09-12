package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.SleepLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SleepLogRepository extends JpaRepository<SleepLog, Long> {

    // aqui busca os 7 registros de sono mais recentes de um paciente
    // ordenando pela data pra pegar os últimos direitinho
    List<SleepLog> findTop7ByPatientIdOrderByAssessmentDateDesc(Long patientId);
}
