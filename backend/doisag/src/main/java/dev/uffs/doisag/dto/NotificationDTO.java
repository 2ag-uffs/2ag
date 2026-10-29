package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.Notification;

import java.time.LocalDateTime;

// so o q a tela precisa sem o usuario inteiro pendurado
public record NotificationDTO(
        Long id,
        String title,
        String message,
        boolean isRead,
        LocalDateTime createdAt,
        String type,
        String link
) {
    public NotificationDTO(Notification notification) {
        this(
                notification.getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt(),
                notification.getType(),
                notification.getLink()
        );
    }
}