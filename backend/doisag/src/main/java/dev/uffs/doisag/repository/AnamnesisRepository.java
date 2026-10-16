package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.Anamnesis;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnamnesisRepository extends AssessmentRepository<Anamnesis> {

    // anamneses de um paciente da mais recente pra mais antiga, com o paciente junto
    @EntityGraph(attributePaths = {"patient"})
    List<Anamnesis> findByPatientIdOrderByAssessmentDateDesc(Long patientId);

    // so o paciente da anamnese, sem carregar ela: quem vai mudar a anamnese trava o paciente
    // primeiro e so dps le a anamnese travada
    @Query("select anamnesis.patient.id from Anamnesis anamnesis where anamnesis.id = :id")
    Optional<Long> findPatientIdById(@Param("id") Long id);
}
