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
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(5, 20, Duration.ofMinutes(15), clock);

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
