package dev.uffs.doisag.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// o que a api responde pra quem n esta autenticado.
// falta de credencial eh 401, n 403: 403 quer dizer "voce esta logado
// mas n pode", e o front precisa distinguir os dois pra saber quando
// mandar a pessoa pro login
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProtectedRoutesTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${api.security.token.secret}")
    private String secret;

    @Test
    void semTokenDevolve401() throws Exception {
        mockMvc.perform(get("/paciente"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/escala-hamilton"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/anamnese"))
                .andExpect(status().isUnauthorized());
    }

    // o corpo do erro tem q ter o mesmo formato do resto da api, senao
    // o front n consegue ler a mensagem
    @Test
    void erroDeAutenticacaoVemEmJsonNoFormatoPadrao() throws Exception {
        mockMvc.perform(get("/paciente"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/paciente"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void tokenComTextoQueNaoEhJwtDevolve401() throws Exception {
        mockMvc.perform(get("/paciente").header("Authorization", "Bearer isso-nao-e-um-jwt"))
                .andExpect(status().isUnauthorized());
    }

    // esses dois davam 500 antes: a excecao do jjwt subia pela cadeia de
    // filtros, onde o @RestControllerAdvice n alcanca
    @Test
    void tokenComAssinaturaErradaDevolve401() throws Exception {
        String outraChave = "chave-diferente-da-que-a-aplicacao-usa-pra-assinar-0123456789";
        String token = Jwts.builder()
                .setSubject("qualquer@email.com")
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor(outraChave.getBytes(StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(get("/paciente").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenExpiradoDevolve401() throws Exception {
        String token = Jwts.builder()
                .setSubject("qualquer@email.com")
                .setIssuedAt(new Date(System.currentTimeMillis() - 7200000))
                .setExpiration(new Date(System.currentTimeMillis() - 3600000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(get("/paciente").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
