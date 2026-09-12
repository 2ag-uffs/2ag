package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.AssignedScale;
import dev.uffs.doisag.enums.AssignmentStatus;
import dev.uffs.doisag.enums.ScaleType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AssignedScaleRepository extends JpaRepository<AssignedScale, Long> {

    // busca todas as escalas pendentes de um paciente específico
    List<AssignedScale> findByPatientIdAndStatus(Long patientId, AssignmentStatus status);

    // busca todas as escalas pendentes designadas por um prescritor
    List<AssignedScale> findByPrescriberIdAndStatus(Long prescriberId, AssignmentStatus status);

    // busca a tarefa pendente mais recente de um tipo para um paciente
    Optional<AssignedScale> findFirstByPatientIdAndScaleTypeAndStatusOrderByAssignedDateDesc(
            Long patientId,
            ScaleType scaleType,
            AssignmentStatus status
    );
    // busca todas as escalas de um paciente, ordenando pela data que foram designadas
    List<AssignedScale> findByPatientIdOrderByAssignedDateDesc(Long patientId);

}