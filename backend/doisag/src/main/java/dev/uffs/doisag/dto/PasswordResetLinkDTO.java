package dev.uffs.doisag.dto;

// o link de senha nova q o administrador entrega pro prescritor
// no piloto pode n ter smtp, entao o link volta na resposta em vez de so no e-mail
public record PasswordResetLinkDTO(
        Long prescriberId,
        String prescriberName,
        String resetLink,
        int validMinutes
) {
}
