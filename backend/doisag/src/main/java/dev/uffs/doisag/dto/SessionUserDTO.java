package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.Users;

// o q o front precisa saber de quem esta logado
// termPending: paciente q aceitou uma versao antiga do termo e precisa aceitar a nova
public record SessionUserDTO(Long id, String name, String role, boolean termPending) {

    public SessionUserDTO(Users user) {
        this(user, false);
    }

    public SessionUserDTO(Users user, boolean termPending) {
        this(user.getId(), user.getDisplayName(), user.getRole().name(), termPending);
    }
}
