package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.Users;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsersRepository extends JpaRepository<Users, Long> {

    // o e-mail eh o login e eh unico no sistema
    Optional<Users> findByEmail(String email);

    // e-mail e cpf n se repetem em nenhuma conta
    boolean existsByEmail(String email);

    boolean existsByCpf(String cpf);
}
