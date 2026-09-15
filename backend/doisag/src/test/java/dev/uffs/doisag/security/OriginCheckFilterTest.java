package dev.uffs.doisag.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// o PUBLIC_URL vira o origin do jeito q o navegador manda
class OriginCheckFilterTest {

    @Test
    void publicUrlBecomesTheOriginTheBrowserSends() {
        assertThat(OriginCheckFilter.originOf("http://localhost:5173")).isEqualTo("http://localhost:5173");
        assertThat(OriginCheckFilter.originOf("https://2ag.exemplo.com.br/")).isEqualTo("https://2ag.exemplo.com.br");
        assertThat(OriginCheckFilter.originOf("HTTPS://2AG.Exemplo.com.br/entrar")).isEqualTo("https://2ag.exemplo.com.br");
        assertThat(OriginCheckFilter.originOf("https://2ag.exemplo.com.br:443")).isEqualTo("https://2ag.exemplo.com.br");
        assertThat(OriginCheckFilter.originOf("http://192.168.0.10:8080/")).isEqualTo("http://192.168.0.10:8080");
    }

    @Test
    void addressWithoutSchemeIsNotAnOrigin() {
        assertThat(OriginCheckFilter.originOf("2ag.exemplo.com.br")).isNull();
        assertThat(OriginCheckFilter.originOf("")).isNull();
        assertThat(OriginCheckFilter.originOf(null)).isNull();
    }

    @Test
    void apiDoesNotStartWithAnIncompletePublicUrl() {
        assertThatThrownBy(() -> new OriginCheckFilter(null, null, "2ag.exemplo.com.br"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PUBLIC_URL");
    }
}
