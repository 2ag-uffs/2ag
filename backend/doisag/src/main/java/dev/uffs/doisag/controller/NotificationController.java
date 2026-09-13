package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.NotificationDTO;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// avisos da propria conta e o servico confere se cada aviso eh de quem pediu
@RestController
@RequestMapping("/notifications")
@PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER')")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // o endpoint retorna uma lista de DTOs
    @GetMapping
    public List<NotificationDTO> getUserNotifications(@AuthenticationPrincipal Users user) {
        return notificationService.getNotificationsForUser(user.getId());
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
