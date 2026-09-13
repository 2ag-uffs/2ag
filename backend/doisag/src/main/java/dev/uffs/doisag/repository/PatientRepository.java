package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

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
}
