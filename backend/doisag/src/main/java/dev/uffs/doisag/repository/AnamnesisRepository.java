package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.Anamnesis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository

public interface AnamnesisRepository extends JpaRepository<Anamnesis, Long> {
}
