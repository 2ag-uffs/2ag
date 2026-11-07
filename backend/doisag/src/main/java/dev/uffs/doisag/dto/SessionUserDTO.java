package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.Users;

// o q o front precisa saber de quem esta logado
// email: qnd a sessao vence com uma tela aberta o front entra de novo por cima dela so nessa conta (#151)
// termPending: paciente q precisa aceitar o termo de novo, versao antiga ou aceite de outra faixa de idade
// termPendingByAge: o texto atual ja foi aceito e so a faixa mudou, entao a data de nascimento errada
// ainda pode ser corrigida sem aceitar de novo (#163)
public record SessionUserDTO(Long id, String name, String email, String role, boolean termPending,
                             boolean termPendingByAge) {

    public SessionUserDTO(Users user) {
        this(user, false, false);
    }

    public SessionUserDTO(Users user, boolean termPending, boolean termPendingByAge) {
        this(user.getId(), user.getDisplayName(), user.getEmail(), user.getRole().name(), termPending,
                termPendingByAge);
    }
}
