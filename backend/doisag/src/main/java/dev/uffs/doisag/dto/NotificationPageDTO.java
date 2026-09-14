package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.Notification;
import org.springframework.data.domain.Page;

import java.util.List;

// uma pagina de avisos com os totais pra tela montar a navegacao
//
// a lista de aviso cresce sem limite, entao ela vem paginada (RNF06)
public record NotificationPageDTO(
        List<NotificationDTO> notifications,
        int page,
        int totalPages,
        long totalNotifications,
        long unread
) {
    public static NotificationPageDTO from(Page<Notification> notifications, long unread) {
        return new NotificationPageDTO(
                notifications.getContent().stream().map(NotificationDTO::new).toList(),
                notifications.getNumber(),
                notifications.getTotalPages(),
                notifications.getTotalElements(),
                unread);
    }
}
