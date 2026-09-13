package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.ConsentAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsentAcceptanceRepository extends JpaRepository<ConsentAcceptance, Long> {

    // aceites de uma pessoa do mais novo pro mais antigo
    List<ConsentAcceptance> findAllByUserIdOrderByAcceptedAtDesc(Long userId);
}
