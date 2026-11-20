package dev.uffs.doisag.infra;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Component;

import java.util.Optional;

// trava a linha de um registro e le ela de novo
//
// o servico pode ja ter o registro na sessao dele antes de chegar na trava: leu ele numa busca
// anterior da mesma transacao, ou trava outro registro primeiro, na ordem das travas. uma
// consulta com @Lock trava a linha mas n atualiza o q ja estava carregado, entao o servico
// decidia em cima do registro velho (o cancelamento gravado no meio da confirmacao, a analise
// gravada no meio da correcao). aqui a copia velha sai da sessao e o registro eh lido de novo
// com a trava, q espera quem estiver mexendo nele terminar
//
// so vale dentro de uma transacao, senao o jpa recusa na hora
@Component
public class RowLock {

    private final EntityManager entityManager;

    public RowLock(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public <T> Optional<T> reload(Class<T> type, Long id) {
        T stale = entityManager.find(type, id);
        if (stale == null) {
            return Optional.empty();
        }
        // o q ainda n foi gravado vai pro banco antes, senao tirar a copia da sessao perderia isso
        entityManager.flush();
        entityManager.detach(stale);
        return Optional.ofNullable(entityManager.find(type, id, LockModeType.PESSIMISTIC_WRITE));
    }
}
