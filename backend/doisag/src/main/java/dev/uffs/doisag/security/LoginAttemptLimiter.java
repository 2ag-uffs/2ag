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
// a contagem eh por e-mail junto com o endereco de quem tenta e tbm so pelo endereco
// assim errar a senha de outra pessoa n trava o login dela de outro lugar
// so o teto alto por e-mail, pensado pra ataque de muitos enderecos, bloqueia de todo lugar
// e como e-mail sem cadastro conta igual a resposta n revela quem tem conta
//
// fica em memoria pq a api roda numa instancia so e reiniciar a api zera tudo
@Component
public class LoginAttemptLimiter {

    private final int maxFailures;
    private final int maxFailuresPerAddress;
    private final int maxFailuresPerEmail;
    private final int maxResetRequestsPerAddress;
    private final Duration blockTime;
    private final Clock clock;
    private final Map<String, FailureCount> failures = new HashMap<>();

    @Autowired
    public LoginAttemptLimiter(
            @Value("${api.security.login.max-failures:5}") int maxFailures,
            @Value("${api.security.login.max-failures-per-address:20}") int maxFailuresPerAddress,
            @Value("${api.security.login.max-failures-per-email:100}") int maxFailuresPerEmail,
            @Value("${api.security.password-reset.max-requests-per-address:10}") int maxResetRequestsPerAddress,
            @Value("${api.security.login.block-minutes:15}") int blockMinutes) {
        this(maxFailures, maxFailuresPerAddress, maxFailuresPerEmail, maxResetRequestsPerAddress,
                Duration.ofMinutes(blockMinutes), Clock.systemDefaultZone());
    }

    // o teste passa um relogio proprio pra conferir o fim do bloqueio sem esperar
    LoginAttemptLimiter(int maxFailures, int maxFailuresPerAddress, int maxFailuresPerEmail,
                        int maxResetRequestsPerAddress, Duration blockTime, Clock clock) {
        this.maxFailures = maxFailures;
        this.maxFailuresPerAddress = maxFailuresPerAddress;
        this.maxFailuresPerEmail = maxFailuresPerEmail;
        this.maxResetRequestsPerAddress = maxResetRequestsPerAddress;
        this.blockTime = blockTime;
        this.clock = clock;
    }

    public synchronized boolean isBlocked(String email, String address) {
        Instant now = clock.instant();
        return isBlocked(emailKey(email, address), now)
                || isBlocked(addressKey(address), now)
                || isBlocked(emailTotalKey(email), now);
    }

    public synchronized void registerFailure(String email, String address) {
        Instant now = clock.instant();
        removeFinished(now);
        addFailure(emailKey(email, address), maxFailures, now);
        addFailure(addressKey(address), maxFailuresPerAddress, now);
        // teto alto somando todo endereco, pra frear quem troca de endereco a cada chute
        addFailure(emailTotalKey(email), maxFailuresPerEmail, now);
    }

    // senha certa zera a contagem daquele e-mail naquele endereco
    public synchronized void registerSuccess(String email, String address) {
        failures.remove(emailKey(email, address));
    }

    // senha nova pelo link de recuperacao libera o e-mail de qualquer endereco
    public synchronized void forgetEmail(String email) {
        failures.keySet().removeIf(key -> key.startsWith("email:" + email + "|"));
        failures.remove(emailTotalKey(email));
    }

    // pedido de link de senha nova conta por endereco, exista ou n a conta
    public synchronized boolean isResetBlocked(String address) {
        return isBlocked(resetKey(address), clock.instant());
    }

    public synchronized void registerResetRequest(String address) {
        Instant now = clock.instant();
        removeFinished(now);
        addFailure(resetKey(address), maxResetRequestsPerAddress, now);
    }

    // trocar pra um e-mail q ja tem conta responde 409, e sem limite isso contaria quem eh da clinica
    public synchronized boolean isEmailChangeBlocked(Long userId) {
        return isBlocked(emailChangeKey(userId), clock.instant());
    }

    public synchronized void registerEmailChangeFailure(Long userId) {
        Instant now = clock.instant();
        removeFinished(now);
        addFailure(emailChangeKey(userId), maxFailures, now);
    }

    public String blockedMessage() {
        return "Muitas tentativas erradas. Tente de novo em " + getBlockMinutes() + " minutos";
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

    private String emailTotalKey(String email) {
        return "email-total:" + email;
    }

    private String resetKey(String address) {
        return "reset:" + address;
    }

    private String emailChangeKey(Long userId) {
        return "email-change:" + userId;
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
