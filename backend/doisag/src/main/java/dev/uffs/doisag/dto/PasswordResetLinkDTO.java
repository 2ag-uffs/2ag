package dev.uffs.doisag.dto;

// o link de senha nova ou de primeiro acesso q o administrador entrega pro prescritor
// resetLink vem nulo qnd o e-mail saiu, pq ai o link vai so pra pessoa
public record PasswordResetLinkDTO(
        Long prescriberId,
        String prescriberName,
        String resetLink,
        int validMinutes
) {
}
