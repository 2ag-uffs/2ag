package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.Anamnesis;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnamnesisRepository extends AssessmentRepository<Anamnesis> {

    // anamneses de um paciente da mais recente pra mais antiga
    List<Anamnesis> findByPatientIdOrderByAssessmentDateDesc(Long patientId);
}
