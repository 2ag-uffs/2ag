package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.BaseAssessment;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

// interface base das 7 escalas. todas herdam de BaseAssessment, entao
// todas tem paciente e data de preenchimento, e a busca por periodo
// pode ser declarada uma vez so aqui em vez de sete vezes.
//
// o NoRepositoryBean diz pro spring q isso aqui n eh um repositorio de
// verdade, eh so o molde dos outros
@NoRepositoryBean
public interface AssessmentRepository<T extends BaseAssessment> extends JpaRepository<T, Long> {

    List<T> findByPatientIdAndAssessmentDateBetweenOrderByAssessmentDateAsc(
            Long patientId, LocalDate inicio, LocalDate fim);
}
