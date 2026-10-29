package dev.uffs.doisag.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// o token guarda a emissao em milissegundos entao os dois cortes da conta tbm
class UsersTimestampTest {

    @Test
    void endSessionsNowKeepsOnlyMilliseconds() {
        Patient user = new Patient();
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        user.endSessionsNow();

        assertThat(user.getSessionsEndedAt()).isAfter(before);
        assertThat(user.getSessionsEndedAt().getNano() % 1_000_000).isZero();
    }

    @Test
    void markPasswordChangedNowKeepsOnlyMilliseconds() {
        Patient user = new Patient();
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        user.markPasswordChangedNow();

        assertThat(user.getPasswordChangedAt()).isAfter(before);
        assertThat(user.getPasswordChangedAt().getNano() % 1_000_000).isZero();
    }
}
