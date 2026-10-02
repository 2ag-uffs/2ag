package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.PatientInvite;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PatientInviteRepository extends JpaRepository<PatientInvite, Long> {

    // o link traz o codigo e a busca eh pelo hash dele
    Optional<PatientInvite> findByTokenHash(String tokenHash);

    // os convites do prescritor q ainda podem ser usados, do mais novo pro mais velho
    List<PatientInvite> findByPrescriberIdAndUsedAtIsNullAndCancelledAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            Long prescriberId, LocalDateTime now);

    // cancelar trava a linha tbm, senao um cancelamento no meio de um cadastro apagava o uso
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invite from PatientInvite invite where invite.id = :id")
    Optional<PatientInvite> findByIdForUpdate(@Param("id") Long id);

    // mesma busca travando a linha do convite ate o cadastro terminar
    // se dois cadastros usarem o mesmo link ao mesmo tempo o segundo espera e encontra o convite ja usado
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invite from PatientInvite invite where invite.tokenHash = :tokenHash")
    Optional<PatientInvite> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
