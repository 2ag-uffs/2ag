package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.Prescriber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PrescriberRepository extends JpaRepository<Prescriber, Long> {

    // checa se a combinação de tipo e número de registro já existe no banco
    boolean existsByRegistryTypeAndRegistryNumber(String registryType, String registryNumber);

    // achar o prescritor pelo email dele que vem da autenticacao
    Optional<Prescriber> findByEmail(String email);

}
