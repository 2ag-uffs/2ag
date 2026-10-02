package dev.uffs.doisag.dto;

import java.time.LocalDateTime;

// convite recem criado com o codigo q vai no link
// o codigo so aparece nessa resposta pq o banco guarda apenas o hash dele
public record InviteCreatedDTO(Long id, String token, LocalDateTime expiresAt) {
}
