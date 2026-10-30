package dev.uffs.doisag.repository;

import dev.uffs.doisag.enums.ConsentType;
import dev.uffs.doisag.model.ConsentAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConsentAcceptanceRepository extends JpaRepository<ConsentAcceptance, Long> {

    // aceites de uma pessoa do mais novo pro mais antigo
    List<ConsentAcceptance> findAllByUserIdOrderByAcceptedAtDesc(Long userId);

    boolean existsByUserIdAndTermType(Long userId, ConsentType termType);

    // o ultimo aceite de uma versao, o id desempata dois aceites no msm instante
    Optional<ConsentAcceptance> findFirstByUserIdAndTermTypeAndTermVersionOrderByAcceptedAtDescIdDesc(
            Long userId, ConsentType termType, String termVersion);

    boolean existsByUserIdAndTermTypeAndTermVersionAndRevokedAtIsNull(Long userId, ConsentType termType,
                                                                      String termVersion);

    List<ConsentAcceptance> findAllByUserIdAndTermTypeAndRevokedAtIsNull(Long userId, ConsentType termType);

    Optional<ConsentAcceptance> findFirstByUserIdAndTermTypeAndTermVersionAndRevokedAtIsNullOrderByAcceptedAtDesc(
            Long userId, ConsentType termType, String termVersion);
}
