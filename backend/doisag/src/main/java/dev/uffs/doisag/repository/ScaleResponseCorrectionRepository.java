package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.ScaleResponseCorrection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// as versoes q as correcoes substituiram, da mais velha pra mais nova (revisao das regras R4)
public interface ScaleResponseCorrectionRepository extends JpaRepository<ScaleResponseCorrection, Long> {

    List<ScaleResponseCorrection> findByResponseIdOrderByCorrectedAtAscIdAsc(Long responseId);

    @Query("select correction from ScaleResponseCorrection correction "
            + "where correction.response.patient.id = :patientId "
            + "order by correction.correctedAt asc, correction.id asc")
    List<ScaleResponseCorrection> findByPatientId(@Param("patientId") Long patientId);

    // a ultima correcao de cada resposta do paciente, pro historico dizer corrigida em e o resultado de antes
    default Map<Long, ScaleResponseCorrection> findLastByResponseOfPatient(Long patientId) {
        Map<Long, ScaleResponseCorrection> lastByResponse = new HashMap<>();
        for (ScaleResponseCorrection correction : findByPatientId(patientId)) {
            lastByResponse.put(correction.getResponse().getId(), correction);
        }
        return lastByResponse;
    }
}
