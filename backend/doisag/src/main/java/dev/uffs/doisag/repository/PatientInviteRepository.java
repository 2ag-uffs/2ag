package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.PatientInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PatientInviteRepository extends JpaRepository<PatientInvite, Long> {

    // o link traz o codigo e a busca eh pelo hash dele
    Optional<PatientInvite> findByTokenHash(String tokenHash);
}
