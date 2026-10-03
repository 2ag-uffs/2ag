package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {

    // quantos pacientes ativos o prescritor tem pro card do painel
    long countByPrescriberIdAndArchivedAtIsNull(Long prescriberId);

    // carteira do prescritor em ordem alfabetica separada entre ativos e arquivados
    List<Patient> findAllByPrescriberIdAndArchivedAtIsNullOrderByNameAsc(Long prescriberId);

    List<Patient> findAllByPrescriberIdAndArchivedAtIsNotNullOrderByNameAsc(Long prescriberId);

    // checa o vinculo numa consulta so sem carregar o paciente inteiro
    // nem depender de lazy loading e eh usado pelo PatientAccessService
    boolean existsByIdAndPrescriberId(Long id, Long prescriberId);

    // a administracao acha a conta pelo e-mail e ve as q ela desativou
    Optional<Patient> findByEmail(String email);

    List<Patient> findAllByActiveFalseOrderByNameAsc();

    // trava a linha do paciente ate o fim da transacao: ele responde uma escala de cada vez
    // sql direto e MANDATORY pelo mesmo motivo do PrescriberRepository.lockById
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "select id from patient where id = :id for update", nativeQuery = true)
    Long lockById(@Param("id") Long id);
}
