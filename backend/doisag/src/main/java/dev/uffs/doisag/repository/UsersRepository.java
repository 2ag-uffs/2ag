package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.userdetails.UserDetails;

public interface UsersRepository extends JpaRepository<Users, Long> {
    UserDetails findByEmail(String email);  // método para buscar usuário pelo email
}