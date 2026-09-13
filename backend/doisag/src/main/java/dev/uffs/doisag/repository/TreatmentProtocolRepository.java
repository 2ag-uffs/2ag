package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.TreatmentProtocol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TreatmentProtocolRepository extends JpaRepository<TreatmentProtocol, Long> {

    // o protocolo que esta valendo pra um paciente
    Optional<TreatmentProtocol> findFirstByPatientIdAndActiveTrue(Long patientId);

    // os protocolos que o job diario precisa olhar
    List<TreatmentProtocol> findByActiveTrue();
}
