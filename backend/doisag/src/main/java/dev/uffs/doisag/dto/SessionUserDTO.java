package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.Users;

// o q o front precisa saber de quem esta logado
public record SessionUserDTO(Long id, String name, String role) {

    public SessionUserDTO(Users user) {
        this(user.getId(), user.getName(), user.getRole().name());
    }
}
