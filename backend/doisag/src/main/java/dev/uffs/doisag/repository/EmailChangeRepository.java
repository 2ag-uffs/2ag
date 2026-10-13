package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.EmailChange;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EmailChangeRepository extends JpaRepository<EmailChange, Long> {

    // trava o pedido ate a confirmacao terminar pra o mesmo link n valer duas vezes ao mesmo tempo
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select emailChange from EmailChange emailChange where emailChange.tokenHash = :tokenHash")
    Optional<EmailChange> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    // so o dono do pedido, sem carregar nem travar a linha: a confirmacao precisa saber de quem
    // eh o link antes de pegar as travas na ordem certa
    @Query("select emailChange.user.id from EmailChange emailChange where emailChange.tokenHash = :tokenHash")
    Optional<Long> findUserIdByTokenHash(@Param("tokenHash") String tokenHash);

    // pedidos da conta q ainda n foram usados, travados ate o fim da transacao: quem esta
    // confirmando um deles nesse exato momento (findByTokenHashForUpdate) espera, ou eh esperado
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select emailChange from EmailChange emailChange"
            + " where emailChange.user.id = :userId and emailChange.usedAt is null")
    List<EmailChange> findPendingForUpdate(@Param("userId") Long userId);

    // o endereco q esta esperando confirmacao, pra tela de perfil mostrar
    @Query("select emailChange.newEmail from EmailChange emailChange"
            + " where emailChange.user.id = :userId and emailChange.usedAt is null and emailChange.expiresAt > :moment"
            + " order by emailChange.createdAt desc")
    List<String> findPendingEmails(@Param("userId") Long userId, @Param("moment") LocalDateTime moment);
}
