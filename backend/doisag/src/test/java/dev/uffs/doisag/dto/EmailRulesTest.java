package dev.uffs.doisag.dto;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

// regra do ponto depois do @ no e-mail q cria ou troca o acesso de uma conta
class EmailRulesTest {

    private static boolean hasDotAfterAt(String email) {
        return email.matches(EmailRules.DOMAIN_WITH_DOT);
    }

    @Test
    void domainWithoutDotIsRefused() {
        assertThat(hasDotAfterAt("maria@gmail")).isFalse();
        assertThat(hasDotAfterAt("maria@gmailcom")).isFalse();
        assertThat(hasDotAfterAt("maria@localhost")).isFalse();
        assertThat(hasDotAfterAt("a@b@c")).isFalse();
    }

    @Test
    void domainWithDotPasses() {
        assertThat(hasDotAfterAt("maria@gmail.com")).isTrue();
        assertThat(hasDotAfterAt("maria@sub.gmail.com")).isTrue();
        assertThat(hasDotAfterAt("a@b@c.d")).isTrue();
    }

    // ponto no lugar errado e endereco sem @ sao problema do @Email, n dessa regra
    @Test
    void theOtherShapesAreLeftToTheEmailCheck() {
        assertThat(hasDotAfterAt("maria@gmail.")).isTrue();
        assertThat(hasDotAfterAt("maria@.com")).isTrue();
        assertThat(hasDotAfterAt("")).isTrue();
        assertThat(hasDotAfterAt("mariagmail.com")).isTrue();
    }

    // o cadastro eh aberto, entao a regra n pode demorar c/ e-mail montado pra travar a api
    @Test
    void aRunOfDotsBeforeAnotherAtFinishesFast() {
        String email250 = "a@" + ".".repeat(247) + "@";
        String hugeEmail = "a@" + ".".repeat(200_000) + "@";

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            assertThat(hasDotAfterAt(email250)).isFalse();
            assertThat(hasDotAfterAt(hugeEmail)).isFalse();
        });
    }
}
