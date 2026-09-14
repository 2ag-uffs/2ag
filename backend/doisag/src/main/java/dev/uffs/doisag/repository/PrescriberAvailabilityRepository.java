package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.PrescriberAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PrescriberAvailabilityRepository extends JpaRepository<PrescriberAvailability, Long> {

    // a semana do prescritor na ordem dos dias e dos horarios
    List<PrescriberAvailability> findByPrescriberIdOrderByDayOfWeekAscStartTimeAsc(Long prescriberId);

    // os periodos de um dia da semana
    List<PrescriberAvailability> findByPrescriberIdAndDayOfWeekOrderByStartTimeAsc(Long prescriberId, Integer dayOfWeek);

    // a semana nova substitui a antiga inteira
    void deleteByPrescriberId(Long prescriberId);
}
