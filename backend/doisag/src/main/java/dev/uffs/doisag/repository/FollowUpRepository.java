package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.FollowUp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface FollowUpRepository extends JpaRepository<FollowUp, Long> {
    List<FollowUp> findByPatientIdAndAssessmentDateAfterOrderByAssessmentDateAsc(Long patientId, LocalDate startDate);
    // busca os acompanhamentos de um paciente a partir de uma data
    // e já ordena pela data pra o gráfico ficar certinho

}
