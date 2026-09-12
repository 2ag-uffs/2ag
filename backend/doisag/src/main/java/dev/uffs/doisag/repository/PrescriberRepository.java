package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.Prescriber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PrescriberRepository extends JpaRepository<Prescriber, Long> {

    // método para buscar um prescritor pelo código profissional
    Optional<Prescriber> findByProfessionalCode(String professionalCode);
    // método pra checar se um código já está em uso
    boolean existsByProfessionalCode(String professionalCode);

    // checa se a combinação de tipo e número de registro já existe no banco
    boolean existsByRegistryTypeAndRegistryNumber(String registryType, String registryNumber);

    // achar o prescritor pelo email dele que vem da autenticacao
    Optional<Prescriber> findByEmail(String email);

}
