package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.NotificationDTO;
import dev.uffs.doisag.model.Notification;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.NotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    // o metodo retorna uma lista de DTOs
    public List<NotificationDTO> getNotificationsForUser(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream() // a gente transforma a lista em um fluxo
                .map(NotificationDTO::new) // para cada notificacao, cria um dto novo
                .collect(Collectors.toList()); // junta tudo numa lista de dtos
    }

    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new EntityNotFoundException("Notificação não encontrada"));

        if (!notification.getUser().getId().equals(userId)) {
            throw new SecurityException("Acesso negado à notificação");
        }
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    public void markAllAsRead(Long userId) {
        List<Notification> unreadNotifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        unreadNotifications.stream()
                .filter(n -> !n.isRead())
                .forEach(n -> {
                    n.setRead(true);
                    notificationRepository.save(n);
                });
    }

    public void deleteNotification(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new EntityNotFoundException("Notificação não encontrada"));

        if (!notification.getUser().getId().equals(userId)) {
            throw new SecurityException("Acesso negado à notificação");
        }
        notificationRepository.deleteById(notificationId);
    }

    public Notification createNotification(Users user, String title, String message, String type, String link) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setLink(link);
        return notificationRepository.save(notification);
    }
}
