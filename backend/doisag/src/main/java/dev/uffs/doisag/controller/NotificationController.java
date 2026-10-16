package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.NotificationPageDTO;
import dev.uffs.doisag.dto.UnreadCountDTO;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.service.NotificationService;
import jakarta.validation.constraints.Max;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// avisos da propria conta e o servico confere se cada aviso eh de quem pediu
@RestController
@RequestMapping("/notifications")
@PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER')")
public class NotificationController {

    // pagina gigante estourava a conta do deslocamento no banco e virava 500
    private static final int MAX_PAGE = 100000;

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public NotificationPageDTO getUserNotifications(
            @RequestParam(name = "page", defaultValue = "0") @Max(MAX_PAGE) int page,
            @AuthenticationPrincipal Users user) {
        return notificationService.getNotificationsForUser(user.getId(), page);
    }

    @GetMapping("/unread-count")
    public UnreadCountDTO countUnread(@AuthenticationPrincipal Users user) {
        return new UnreadCountDTO(notificationService.countUnread(user.getId()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id, @AuthenticationPrincipal Users user) {
        notificationService.markAsRead(id, user.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal Users user) {
        notificationService.markAllAsRead(user.getId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id, @AuthenticationPrincipal Users user) {
        notificationService.deleteNotification(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
