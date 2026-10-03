package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.Prescriber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface PrescriberRepository extends JpaRepository<Prescriber, Long> {

    // checa se a combinação de tipo e número de registro já existe no banco
    boolean existsByRegistryTypeAndRegistryNumber(String registryType, String registryNumber);

    // achar o prescritor pelo email dele que vem da autenticacao
    Optional<Prescriber> findByEmail(String email);

    // trava a linha do prescritor ate o fim da transacao: a agenda dele recebe uma marcacao de cada vez
    // sql direto de proposito: com jpql e @Lock o hibernate trava o prescritor (q herda de users) numa
    // consulta separada no h2, e pula essa trava qnd o prescritor ja foi carregado antes na transacao
    // MANDATORY: fora de uma transacao a trava soltaria no fim do select sem ninguem perceber
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "select id from prescriber where id = :id for update", nativeQuery = true)
    Long lockById(@Param("id") Long id);
}
