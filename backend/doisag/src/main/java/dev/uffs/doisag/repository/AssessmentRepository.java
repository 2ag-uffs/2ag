package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.BaseAssessment;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

// molde dos repositorios de ficha q herdam de BaseAssessment e hj so a anamnese usa
// toda ficha tem paciente e data entao a busca por periodo fica aqui uma vez so
@NoRepositoryBean
public interface AssessmentRepository<T extends BaseAssessment> extends JpaRepository<T, Long> {

    List<T> findByPatientIdAndAssessmentDateBetweenOrderByAssessmentDateAsc(
            Long patientId, LocalDate start, LocalDate end);
}
