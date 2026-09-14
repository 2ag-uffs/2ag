package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.NotificationPageDTO;
import dev.uffs.doisag.infra.ForbiddenException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Notification;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.NotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// os avisos de cada conta (RF14 e RF15)
@Service
public class NotificationService {

    // a lista vem de 20 em 20, pq ela cresce sem limite (RNF06)
    private static final int PAGE_SIZE = 20;

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public NotificationPageDTO getNotificationsForUser(Long userId, int page) {
        return NotificationPageDTO.from(
                notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageRequest(page)),
                notificationRepository.countUnread(userId));
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = findOwnNotification(notificationId, userId);
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        List<Notification> unread = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .filter(notification -> !notification.isRead())
                .toList();
        unread.forEach(notification -> notification.setRead(true));
        notificationRepository.saveAll(unread);
    }

    @Transactional
    public void deleteNotification(Long notificationId, Long userId) {
        Notification notification = findOwnNotification(notificationId, userId);
        notificationRepository.delete(notification);
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

    // o aviso eh sempre da propria conta, entao mexer no de outra pessoa da 403
    private Notification findOwnNotification(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("Aviso não encontrado com o id: " + notificationId));
        if (!notification.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Acesso negado ao aviso");
        }
        return notification;
    }

    private PageRequest pageRequest(int page) {
        return PageRequest.of(Math.max(page, 0), PAGE_SIZE);
    }
}
