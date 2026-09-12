package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.TEALog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TEALogRepository extends JpaRepository<TEALog, Long> {
}