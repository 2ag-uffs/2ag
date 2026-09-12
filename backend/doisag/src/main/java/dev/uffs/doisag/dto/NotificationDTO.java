package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.Notification;

import java.time.LocalDateTime;

// so tem os dados que a tela precisa, sem o objeto de usuario inteiro
public record NotificationDTO(
        Long id,
        String title,
        String message,
        boolean isRead,
        LocalDateTime createdAt,
        String type,
        String link
) {
    // um construtor que facilita a conversao da nossa entidade para este dto
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