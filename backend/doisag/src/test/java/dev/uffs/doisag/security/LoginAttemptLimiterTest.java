package dev.uffs.doisag.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

// contagem de senhas erradas no login (issue 48)
class LoginAttemptLimiterTest {

    private static final String EMAIL = "maria@email.com";
    private static final String MARIA_ADDRESS = "200.1.1.1";
    private static final String OTHER_ADDRESS = "177.9.9.9";

    private final MovableClock clock = new MovableClock();
    private final LoginAttemptLimiter limiter =
            new LoginAttemptLimiter(5, 20, 100, 10, Duration.ofMinutes(15), clock);

    @Test
    void fiveFailuresBlockThatEmailOnlyFromThatAddress() {
        failTimes(EMAIL, OTHER_ADDRESS, 5);

        assertThat(limiter.isBlocked(EMAIL, OTHER_ADDRESS)).isTrue();
        assertThat(limiter.isBlocked(EMAIL, MARIA_ADDRESS)).isFalse();
    }

    @Test
    void theBlockEndsAfterTheBlockTime() {
        failTimes(EMAIL, OTHER_ADDRESS, 5);

        clock.moveMinutes(14);
        assertThat(limiter.isBlocked(EMAIL, OTHER_ADDRESS)).isTrue();

        clock.moveMinutes(2);
        assertThat(limiter.isBlocked(EMAIL, OTHER_ADDRESS)).isFalse();
    }

    @Test
    void oldFailuresStopCountingAfterTheBlockTime() {
        failTimes(EMAIL, OTHER_ADDRESS, 4);

        clock.moveMinutes(16);
        failTimes(EMAIL, OTHER_ADDRESS, 1);

        assertThat(limiter.isBlocked(EMAIL, OTHER_ADDRESS)).isFalse();
    }

    @Test
    void theBlockEndsRightAtTheBlockTime() {
        failTimes(EMAIL, OTHER_ADDRESS, 5);

        clock.moveMinutes(15);
        assertThat(limiter.isBlocked(EMAIL, OTHER_ADDRESS)).isFalse();
    }

    @Test
    void oldFailuresStopCountingRightAtTheBlockTime() {
        failTimes(EMAIL, OTHER_ADDRESS, 4);

        clock.moveMinutes(15);
        failTimes(EMAIL, OTHER_ADDRESS, 1);

        assertThat(limiter.isBlocked(EMAIL, OTHER_ADDRESS)).isFalse();
    }

    // o bloqueio conta do erro q bateu o limite e n do primeiro erro
    @Test
    void theBlockCountsFromTheFailureThatReachedTheLimit() {
        failTimes(EMAIL, OTHER_ADDRESS, 4);

        clock.moveMinutes(10);
        failTimes(EMAIL, OTHER_ADDRESS, 1);

        clock.moveMinutes(14);
        assertThat(limiter.isBlocked(EMAIL, OTHER_ADDRESS)).isTrue();

        clock.moveMinutes(2);
        assertThat(limiter.isBlocked(EMAIL, OTHER_ADDRESS)).isFalse();
    }

    // o erro de outra pessoa limpa a memoria mas n pode levar junto um bloqueio q ainda vale
    @Test
    void aLateBlockSurvivesTheCleanupOfOldFailures() {
        failTimes(EMAIL, OTHER_ADDRESS, 4);

        clock.moveMinutes(10);
        failTimes(EMAIL, OTHER_ADDRESS, 1);

        clock.moveMinutes(6);
        limiter.registerFailure("outra@email.com", MARIA_ADDRESS);

        assertThat(limiter.isBlocked(EMAIL, OTHER_ADDRESS)).isTrue();
    }

    @Test
    void manyEmailsFromTheSameAddressBlockTheAddress() {
        for (int index = 0; index < 20; index++) {
            limiter.registerFailure("pessoa" + index + "@email.com", OTHER_ADDRESS);
        }

        assertThat(limiter.isBlocked("mais-uma@email.com", OTHER_ADDRESS)).isTrue();
        assertThat(limiter.isBlocked("mais-uma@email.com", MARIA_ADDRESS)).isFalse();
    }

    @Test
    void rightPasswordResetsTheCountForThatEmailAndAddress() {
        failTimes(EMAIL, MARIA_ADDRESS, 4);
        limiter.registerSuccess(EMAIL, MARIA_ADDRESS);
        failTimes(EMAIL, MARIA_ADDRESS, 4);

        assertThat(limiter.isBlocked(EMAIL, MARIA_ADDRESS)).isFalse();
    }

    @Test
    void newPasswordByTheResetLinkReleasesTheEmailFromEveryAddress() {
        failTimes(EMAIL, MARIA_ADDRESS, 5);
        failTimes(EMAIL, OTHER_ADDRESS, 5);

        limiter.forgetEmail(EMAIL);

        assertThat(limiter.isBlocked(EMAIL, MARIA_ADDRESS)).isFalse();
        assertThat(limiter.isBlocked(EMAIL, OTHER_ADDRESS)).isFalse();
    }

    // o e-mail entra na chave como hash, entao o tamanho dele n muda o q fica na memoria
    @Test
    void aHugeEmailBecomesAShortKeyAndStillCounts() {
        String hugeEmail = "a".repeat(100_000) + "@email.com";
        failTimes(hugeEmail, OTHER_ADDRESS, 5);

        assertThat(limiter.isBlocked(hugeEmail, OTHER_ADDRESS)).isTrue();
        assertThat(limiter.isBlocked(EMAIL, OTHER_ADDRESS)).isFalse();
        assertThat(limiter.longestKeyLength()).isLessThan(200);
    }

    // so da pra olhar a memoria pelo tamanho da maior chave, por isso o endereco antigo eh mais comprido
    // conta com o endereco entrando cru na chave, se um dia virar hash esse teste precisa de outro jeito de olhar
    @Test
    void finishedFailuresLeaveTheMemoryOnTheNextFailure() {
        limiter.registerFailure(EMAIL, "2804:14c:5b80:8000:1234:5678:9abc:def0");
        int longestWithTheOldAddress = limiter.longestKeyLength();

        clock.moveMinutes(16);
        limiter.registerFailure(EMAIL, MARIA_ADDRESS);

        assertThat(limiter.longestKeyLength()).isLessThan(longestWithTheOldAddress);
    }

    // quem troca de endereco a cada chute escapa da conta por endereco, mas n do teto do e-mail
    @Test
    void manyAddressesAgainstOneEmailBlockThatEmailEverywhere() {
        for (int index = 0; index < 100; index++) {
            limiter.registerFailure(EMAIL, "10.0.0." + index);
        }

        assertThat(limiter.isBlocked(EMAIL, MARIA_ADDRESS)).isTrue();

        limiter.forgetEmail(EMAIL);
        assertThat(limiter.isBlocked(EMAIL, MARIA_ADDRESS)).isFalse();
    }

    @Test
    void lessThanAHundredFailuresFromManyAddressesLeaveTheEmailFree() {
        for (int index = 0; index < 99; index++) {
            limiter.registerFailure(EMAIL, "10.0.0." + index);
        }

        assertThat(limiter.isBlocked(EMAIL, MARIA_ADDRESS)).isFalse();

        limiter.registerFailure(EMAIL, OTHER_ADDRESS);
        assertThat(limiter.isBlocked(EMAIL, MARIA_ADDRESS)).isTrue();
    }

    @Test
    void tenResetRequestsBlockOnlyThatAddressUntilTheBlockTimeEnds() {
        for (int request = 0; request < 10; request++) {
            limiter.registerResetRequest(OTHER_ADDRESS);
        }

        assertThat(limiter.isResetBlocked(OTHER_ADDRESS)).isTrue();
        assertThat(limiter.isResetBlocked(MARIA_ADDRESS)).isFalse();

        clock.moveMinutes(16);
        assertThat(limiter.isResetBlocked(OTHER_ADDRESS)).isFalse();
    }

    @Test
    void fiveEmailChangeRequestsBlockThatAccountOnly() {
        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.registerEmailChangeRequest(1L);
        }

        assertThat(limiter.isEmailChangeBlocked(1L)).isTrue();
        assertThat(limiter.isEmailChangeBlocked(2L)).isFalse();
    }

    @Test
    void fiveSignUpAttemptsBlockThatInviteFromEveryAddress() {
        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.registerSignUpAttempt("convite-a");
        }

        assertThat(limiter.isSignUpBlocked("convite-a", MARIA_ADDRESS)).isTrue();
        assertThat(limiter.isSignUpBlocked("convite-b", OTHER_ADDRESS)).isFalse();

        clock.moveMinutes(16);
        assertThat(limiter.isSignUpBlocked("convite-a", MARIA_ADDRESS)).isFalse();
    }

    @Test
    void twentyRefusedSignUpsBlockTheAddressForEveryInvite() {
        for (int refusal = 0; refusal < 20; refusal++) {
            limiter.registerSignUpRefusal(OTHER_ADDRESS);
        }

        assertThat(limiter.isSignUpBlocked("convite-novo", OTHER_ADDRESS)).isTrue();
        assertThat(limiter.isSignUpBlocked("convite-novo", MARIA_ADDRESS)).isFalse();
    }

    private void failTimes(String email, String address, int times) {
        for (int attempt = 0; attempt < times; attempt++) {
            limiter.registerFailure(email, address);
        }
    }

    // relogio q so anda quando o teste manda
    private static class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-09-15T12:00:00Z");

        void moveMinutes(int minutes) {
            now = now.plus(Duration.ofMinutes(minutes));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("America/Sao_Paulo");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
