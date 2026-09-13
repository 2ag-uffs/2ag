package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.PatientInvite;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PatientInviteRepository extends JpaRepository<PatientInvite, Long> {

    // o link traz o codigo e a busca eh pelo hash dele
    Optional<PatientInvite> findByTokenHash(String tokenHash);

    // mesma busca travando a linha do convite ate o cadastro terminar
    // se dois cadastros usarem o mesmo link ao mesmo tempo o segundo espera e encontra o convite ja usado
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invite from PatientInvite invite where invite.tokenHash = :tokenHash")
    Optional<PatientInvite> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
