package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    // busca todas as notificacoes de um usuario, as mais novas primeiro
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);
}