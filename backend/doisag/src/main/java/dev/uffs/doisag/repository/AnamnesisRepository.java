package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.Anamnesis;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnamnesisRepository extends AssessmentRepository<Anamnesis> {

    // anamneses de um paciente da mais recente pra mais antiga, com o paciente junto
    // no mesmo dia a mais nova vem antes: corrigir eh anular e preencher de novo, e a anulada vinha em cima
    @EntityGraph(attributePaths = {"patient"})
    List<Anamnesis> findByPatientIdOrderByAssessmentDateDescIdDesc(Long patientId);

    // a ficha valida enviada a partir daquele momento, q segura o envio da avaliacao inicial no mesmo dia
    // conta o dia do envio e n a data de preenchimento, q o paciente pode trocar
    boolean existsByPatientIdAndCreatedAtGreaterThanEqualAndAnnulmentAnnulledAtIsNull(
            Long patientId, LocalDateTime from);

    // so o paciente da anamnese, sem carregar ela: quem vai mudar a anamnese trava o paciente
    // primeiro e so dps le a anamnese travada
    @Query("select anamnesis.patient.id from Anamnesis anamnesis where anamnesis.id = :id")
    Optional<Long> findPatientIdById(@Param("id") Long id);
}
