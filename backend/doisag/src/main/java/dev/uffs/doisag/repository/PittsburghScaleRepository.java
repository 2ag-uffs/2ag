package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.PittsburghScale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PittsburghScaleRepository extends JpaRepository<PittsburghScale, Long> {
}
