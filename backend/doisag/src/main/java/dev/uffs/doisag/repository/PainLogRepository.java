package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.PainLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PainLogRepository extends JpaRepository<PainLog, Long> {
}
