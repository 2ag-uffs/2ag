package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.PasswordReset;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PasswordResetRepository extends JpaRepository<PasswordReset, Long> {

    // trava o pedido ate a troca terminar pra o mesmo link n valer duas vezes ao mesmo tempo
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reset from PasswordReset reset where reset.tokenHash = :tokenHash")
    Optional<PasswordReset> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    // quantos links a conta pediu desde um momento
    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime moment);

    // links da conta q ainda n foram usados
    List<PasswordReset> findAllByUserIdAndUsedAtIsNull(Long userId);
}
