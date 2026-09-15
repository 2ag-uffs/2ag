package dev.uffs.doisag.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

// conta as senhas erradas no login pra barrar quem fica tentando adivinhar
//
// a contagem eh por e-mail junto c/ o endereco de quem tenta e tbm so pelo endereco
// assim errar a senha de outra pessoa n trava o login dela de outro lugar
// e como e-mail sem cadastro conta igual a resposta n revela quem tem conta
//
// fica em memoria pq a api roda numa instancia so e reiniciar a api zera tudo
@Component
public class LoginAttemptLimiter {

    private final int maxFailures;
    private final int maxFailuresPerAddress;
    private final Duration blockTime;
    private final Clock clock;
    private final Map<String, FailureCount> failures = new HashMap<>();

    @Autowired
    public LoginAttemptLimiter(@Value("${api.security.login.max-failures:5}") int maxFailures,
                               @Value("${api.security.login.max-failures-per-address:20}") int maxFailuresPerAddress,
                               @Value("${api.security.login.block-minutes:15}") int blockMinutes) {
        this(maxFailures, maxFailuresPerAddress, Duration.ofMinutes(blockMinutes), Clock.systemDefaultZone());
    }

    // o teste passa um relogio proprio pra conferir o fim do bloqueio sem esperar
    LoginAttemptLimiter(int maxFailures, int maxFailuresPerAddress, Duration blockTime, Clock clock) {
        this.maxFailures = maxFailures;
        this.maxFailuresPerAddress = maxFailuresPerAddress;
        this.blockTime = blockTime;
        this.clock = clock;
    }

    public synchronized boolean isBlocked(String email, String address) {
        Instant now = clock.instant();
        return isBlocked(emailKey(email, address), now) || isBlocked(addressKey(address), now);
    }

    public synchronized void registerFailure(String email, String address) {
        Instant now = clock.instant();
        removeFinished(now);
        addFailure(emailKey(email, address), maxFailures, now);
        addFailure(addressKey(address), maxFailuresPerAddress, now);
    }

    // senha certa zera a contagem daquele e-mail naquele endereco
    public synchronized void registerSuccess(String email, String address) {
        failures.remove(emailKey(email, address));
    }

    // senha nova pelo link de recuperacao libera o e-mail de qualquer endereco
    public synchronized void forgetEmail(String email) {
        failures.keySet().removeIf(key -> key.startsWith("email:" + email + "|"));
    }

    // os testes comecam sem nenhuma tentativa guardada
    public synchronized void clear() {
        failures.clear();
    }

    public long getBlockMinutes() {
        return blockTime.toMinutes();
    }

    private boolean isBlocked(String key, Instant now) {
        FailureCount count = failures.get(key);
        return count != null && count.blockedUntil != null && count.blockedUntil.isAfter(now);
    }

    private void addFailure(String key, int limit, Instant now) {
        FailureCount count = failures.get(key);
        if (count == null || count.hasFinished(now, blockTime)) {
            count = new FailureCount(now);
            failures.put(key, count);
        }
        count.total = count.total + 1;
        if (count.total >= limit) {
            count.blockedUntil = now.plus(blockTime);
        }
    }

    // tira da memoria o q ja n bloqueia nem conta mais
    private void removeFinished(Instant now) {
        failures.values().removeIf(count -> count.hasFinished(now, blockTime));
    }

    private String emailKey(String email, String address) {
        return "email:" + email + "|" + address;
    }

    private String addressKey(String address) {
        return "address:" + address;
    }

    // erros de uma chave desde o primeiro erro e ate quando ela fica bloqueada
    private static class FailureCount {
        private final Instant firstFailureAt;
        private int total;
        private Instant blockedUntil;

        private FailureCount(Instant firstFailureAt) {
            this.firstFailureAt = firstFailureAt;
        }

        // erro antigo sem bloqueio some depois do tempo de bloqueio e bloqueio acaba no prazo
        private boolean hasFinished(Instant now, Duration blockTime) {
            if (blockedUntil != null) {
                return !blockedUntil.isAfter(now);
            }
            return !firstFailureAt.plus(blockTime).isAfter(now);
        }
    }
}
