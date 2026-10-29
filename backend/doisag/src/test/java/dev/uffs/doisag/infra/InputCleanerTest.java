package dev.uffs.doisag.infra;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// o endereco do sistema vai na frente dos links dos e-mails
class InputCleanerTest {

    @Test
    void trailingSlashIsRemovedOnce() {
        assertThat(InputCleaner.withoutTrailingSlash("https://2ag.example/")).isEqualTo("https://2ag.example");
        assertThat(InputCleaner.withoutTrailingSlash("https://2ag.example")).isEqualTo("https://2ag.example");
    }
}
