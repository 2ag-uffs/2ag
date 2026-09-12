package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.HamiltonScale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HamiltonScaleRepository extends JpaRepository<HamiltonScale, Long> {
}
