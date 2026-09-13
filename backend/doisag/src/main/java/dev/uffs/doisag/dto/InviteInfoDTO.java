package dev.uffs.doisag.dto;

import java.time.LocalDateTime;

// o q a tela de cadastro mostra sobre o convite antes do formulario
public record InviteInfoDTO(String prescriberName, String prescriberProfession, LocalDateTime expiresAt) {
}
