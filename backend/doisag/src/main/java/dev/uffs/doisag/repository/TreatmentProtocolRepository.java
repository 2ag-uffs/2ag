package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.TreatmentProtocol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TreatmentProtocolRepository extends JpaRepository<TreatmentProtocol, Long> {

    // o protocolo que esta valendo pra um paciente
    Optional<TreatmentProtocol> findFirstByPatientIdAndActiveTrue(Long patientId);

    List<TreatmentProtocol> findByActiveTrue();

    // os protocolos que o job diario precisa olhar, so o id pq cada um eh lido na propria transacao
    @Query("select protocol.id from TreatmentProtocol protocol where protocol.active = true order by protocol.id")
    List<Long> findActiveIds();

    // so o paciente do protocolo, sem carregar ele: o job trava o prescritor e o paciente antes de ler o protocolo
    @Query("select protocol.patient.id from TreatmentProtocol protocol where protocol.id = :id")
    Optional<Long> findPatientIdById(@Param("id") Long id);

    // quem esta no ciclo hoje: acompanhamento valendo, conta ativa e fora do arquivo (RF39)
    @Query("select count(distinct protocol.patient.id) from TreatmentProtocol protocol "
            + "where protocol.patient.prescriber.id = :prescriberId and protocol.active = true "
            + "and protocol.startDate <= :today and protocol.endDate >= :today "
            + "and protocol.patient.archivedAt is null and protocol.patient.active = true")
    long countPatientsInCycle(@Param("prescriberId") Long prescriberId, @Param("today") LocalDate today);

    // quem ja teve acompanhamento alguma vez, encerrado ou n
    @Query("select distinct protocol.patient.id from TreatmentProtocol protocol "
            + "where protocol.patient.prescriber.id = :prescriberId")
    List<Long> findPatientIdsWithProtocol(@Param("prescriberId") Long prescriberId);
}
