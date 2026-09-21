package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // busca todas as notificacoes de um usuario, as mais novas primeiro
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    // a mesma lista paginada, q eh como a tela mostra (RNF06)
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // os avisos n lidos mais novos, q eh o q o painel mostra
    // o corte vem do Pageable pq antes o painel trazia a caixa inteira pra ficar com cinco
    List<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // o numero de avisos q a pessoa ainda n abriu
    @Query("select count(notification) from Notification notification "
            + "where notification.user.id = :userId and notification.isRead = false")
    long countUnread(@Param("userId") Long userId);
}
