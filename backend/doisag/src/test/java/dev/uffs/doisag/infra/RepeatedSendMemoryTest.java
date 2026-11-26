package dev.uffs.doisag.infra;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

// memoria do envio repetido (decisao de novembro/2026)
class RepeatedSendMemoryTest {

    private static final String PLACE = "receita da consulta 7";

    private final MovableClock clock = new MovableClock();
    private final RepeatedSendMemory memory = new RepeatedSendMemory(clock);

    @Test
    void sameKeyAndContentInTheSamePlaceFindsTheSavedRecord() {
        memory.remember(PLACE, "chave", "conteudo", 15L);

        assertThat(memory.findSaved(PLACE, "chave", "conteudo")).isEqualTo(15L);
    }

    @Test
    void anotherKeyContentOrPlaceIsANewSend() {
        memory.remember(PLACE, "chave", "conteudo", 15L);

        assertThat(memory.findSaved(PLACE, "outra chave", "conteudo")).isNull();
        assertThat(memory.findSaved(PLACE, "chave", "outro conteudo")).isNull();
        assertThat(memory.findSaved("receita da consulta 8", "chave", "conteudo")).isNull();
    }

    // sao poucos minutos: depois deles o msm envio volta a criar registro
    @Test
    void savedSendIsForgottenAfterTenMinutes() {
        memory.remember(PLACE, "chave", "conteudo", 15L);

        clock.moveMinutes(9);
        assertThat(memory.findSaved(PLACE, "chave", "conteudo")).isEqualTo(15L);
        clock.moveMinutes(1);
        assertThat(memory.findSaved(PLACE, "chave", "conteudo")).isNull();
    }

    // sem chave, com ela em branco ou grande demais nada eh guardado e todo envio eh novo
    @Test
    void sendWithoutAUsableKeyIsNeverRemembered() {
        String hugeKey = "a".repeat(101);
        memory.remember(PLACE, null, "conteudo", 15L);
        memory.remember(PLACE, "  ", "conteudo", 15L);
        memory.remember(PLACE, hugeKey, "conteudo", 15L);

        assertThat(memory.findSaved(PLACE, null, "conteudo")).isNull();
        assertThat(memory.findSaved(PLACE, "  ", "conteudo")).isNull();
        assertThat(memory.findSaved(PLACE, hugeKey, "conteudo")).isNull();
    }

    // relogio q so anda quando o teste manda
    private static class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-11-25T12:00:00Z");

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
