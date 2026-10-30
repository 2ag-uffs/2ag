package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.Users;

// o q o front precisa saber de quem esta logado
// termPending: paciente q precisa aceitar o termo de novo, versao antiga ou aceite de outra faixa de idade
// termPendingByAge: o texto atual ja foi aceito e so a faixa mudou, entao a data de nascimento errada
// ainda pode ser corrigida sem aceitar de novo (#163)
public record SessionUserDTO(Long id, String name, String role, boolean termPending, boolean termPendingByAge) {

    public SessionUserDTO(Users user) {
        this(user, false, false);
    }

    public SessionUserDTO(Users user, boolean termPending, boolean termPendingByAge) {
        this(user.getId(), user.getDisplayName(), user.getRole().name(), termPending, termPendingByAge);
    }
}
