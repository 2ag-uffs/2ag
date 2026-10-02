package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.DailyCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DailyCycleRepository extends JpaRepository<DailyCycle, Long> {
}
