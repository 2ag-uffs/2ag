package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.Anamnesis;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnamnesisRepository extends AssessmentRepository<Anamnesis> {

    // anamneses de um paciente da mais recente pra mais antiga, com o paciente junto
    @EntityGraph(attributePaths = {"patient"})
    List<Anamnesis> findByPatientIdOrderByAssessmentDateDesc(Long patientId);
}
