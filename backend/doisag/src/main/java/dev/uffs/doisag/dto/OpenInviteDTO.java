package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.PatientInvite;

import java.time.LocalDateTime;

// um convite em aberto na lista do prescritor
// sem o codigo, pq o banco guarda so o hash dele
public record OpenInviteDTO(Long id, LocalDateTime createdAt, LocalDateTime expiresAt) {

    public OpenInviteDTO(PatientInvite invite) {
        this(invite.getId(), invite.getCreatedAt(), invite.getExpiresAt());
    }
}
