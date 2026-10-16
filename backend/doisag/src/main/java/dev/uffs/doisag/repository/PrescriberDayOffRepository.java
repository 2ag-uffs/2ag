package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.PrescriberDayOff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PrescriberDayOffRepository extends JpaRepository<PrescriberDayOff, Long> {

    boolean existsByPrescriberIdAndOffDate(Long prescriberId, LocalDate offDate);

    // as folgas de hoje em diante na ordem das datas
    List<PrescriberDayOff> findByPrescriberIdAndOffDateGreaterThanEqualOrderByOffDateAsc(Long prescriberId,
                                                                                       LocalDate firstDay);

    Optional<PrescriberDayOff> findByPrescriberIdAndOffDate(Long prescriberId, LocalDate offDate);
}
