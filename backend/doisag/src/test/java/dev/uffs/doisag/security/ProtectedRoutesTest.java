package dev.uffs.doisag.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.oneOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// teste da camada web usando o perfil de teste, ou seja h2 em memoria.
// prova q a api sobe e responde sem nenhum banco instalado
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProtectedRoutesTest {

    @Autowired
    private MockMvc mockMvc;

    // esse eh o invariante q tem q valer sempre: sem token, n passa.
    // vale pra 401 e pra 403, ai o teste sobrevive a correcao do RF29
    @Test
    void shouldDenyAccessWithoutToken() throws Exception {
        mockMvc.perform(get("/paciente"))
                .andExpect(status().is(oneOf(401, 403)));

        mockMvc.perform(get("/escala-hamilton"))
                .andExpect(status().is(oneOf(401, 403)));

        mockMvc.perform(get("/anamnese"))
                .andExpect(status().is(oneOf(401, 403)));
    }

    // sentinela: hoje a api devolve 403 pra requisicao sem credencial,
    // mas 403 significa "autenticado e sem permissao". faltando
    // credencial o certo eh 401, q eh o q o RF29 exige.
    // quando a Fase 1 configurar o AuthenticationEntryPoint este teste
    // vai quebrar de proposito, ai eh so trocar pra 401
    @Test
    void semTokenAindaDevolve403EmVezDe401() throws Exception {
        mockMvc.perform(get("/paciente"))
                .andExpect(status().is(is(403)));
    }
}
