package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.AnamnesisCorrection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

// as versoes da anamnese q as correcoes substituiram, da mais velha pra mais nova (revisao das regras R4)
public interface AnamnesisCorrectionRepository extends JpaRepository<AnamnesisCorrection, Long> {

    List<AnamnesisCorrection> findByAnamnesisIdOrderByCorrectedAtAscIdAsc(Long anamnesisId);

    @Query("select correction from AnamnesisCorrection correction "
            + "where correction.anamnesis.patient.id = :patientId "
            + "order by correction.correctedAt asc, correction.id asc")
    List<AnamnesisCorrection> findByPatientId(@Param("patientId") Long patientId);
}
