package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.ChangePasswordDTO;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.UsersRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// troca a senha do usuario que esta logado. n existe troca de senha de
// terceiro aqui de proposito: prescritor n mexe na senha do paciente
@Service
public class PasswordService {

    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordService(UsersRepository usersRepository, PasswordEncoder passwordEncoder) {
        this.usersRepository = usersRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void trocarSenha(Users usuarioLogado, ChangePasswordDTO dados) {
        // a senha atual eh conferida contra o hash do banco, n contra o
        // objeto da sessao, senao um token velho ja bastaria
        Users usuario = usersRepository.findById(usuarioLogado.getId())
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        if (!passwordEncoder.matches(dados.senhaAtual(), usuario.getPassword())) {
            throw new IllegalArgumentException("A senha atual está incorreta");
        }

        if (passwordEncoder.matches(dados.novaSenha(), usuario.getPassword())) {
            throw new IllegalArgumentException("A nova senha precisa ser diferente da atual");
        }

        usuario.setPassword(passwordEncoder.encode(dados.novaSenha()));
        usersRepository.save(usuario);
    }
}
