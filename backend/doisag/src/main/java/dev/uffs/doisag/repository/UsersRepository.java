package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface UsersRepository extends JpaRepository<Users, Long> {

    // o e-mail eh o login e eh unico no sistema
    Optional<Users> findByEmail(String email);

    // e-mail e cpf n se repetem em nenhuma conta
    boolean existsByEmail(String email);

    boolean existsByCpf(String cpf);

    // trava a linha da conta ate o fim da transacao: o pedido de e-mail novo, a troca de senha
    // e a confirmacao do e-mail passam um de cada vez, entao um pedido feito no mesmo instante
    // da troca de senha espera e encontra a senha nova
    // ordem das travas por conta: password_reset, dps users, dps email_change
    // MANDATORY: fora de uma transacao a trava soltaria no fim do select sem ninguem perceber
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "select id from users where id = :id for update", nativeQuery = true)
    Long lockById(@Param("id") Long id);
}
