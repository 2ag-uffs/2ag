package dev.uffs.doisag.security;

import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// requisicao q muda dado c/ o cookie da sessao so passa vindo do endereco do sistema (issue 46)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OriginCheckTest {

    // o PUBLIC_URL do perfil de teste
    private static final String SYSTEM_ORIGIN = "http://localhost:5173";
    private static final String OTHER_SITE = "https://outro-site.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private PatientRepository patientRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private TokenService tokenService;

    private Patient patient;
    private Cookie sessionCookie;

    @BeforeEach
    void createPatientWithSession() {
        Prescriber prescriber = new Prescriber();
        prescriber.setName("Prescritora da origem");
        prescriber.setEmail("origem-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente da origem");
        patient.setEmail("origem-paciente@email.com");
        patient.setPassword("hash");
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);

        sessionCookie = new Cookie(SessionCookieService.COOKIE_NAME, tokenService.generateToken(patient));
    }

    @Test
    void anotherSiteCannotUseTheSession() throws Exception {
        mockMvc.perform(post("/auth/logout").cookie(sessionCookie).header("Origin", OTHER_SITE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(OriginCheckFilter.BLOCKED_MESSAGE));

        // a saida n aconteceu e a sessao continua valendo
        mockMvc.perform(get("/auth/me").cookie(sessionCookie)).andExpect(status().isOk());
    }

    @Test
    void addressThatOnlyStartsLikeTheSystemIsBlocked() throws Exception {
        mockMvc.perform(post("/auth/logout").cookie(sessionCookie).header("Origin", SYSTEM_ORIGIN + ".outro-site.com"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/auth/logout").cookie(sessionCookie).header("Referer", SYSTEM_ORIGIN + ".outro-site.com/"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestWithoutOriginAndRefererIsBlocked() throws Exception {
        mockMvc.perform(post("/auth/logout").cookie(sessionCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void everyMethodThatChangesDataIsChecked() throws Exception {
        for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
            mockMvc.perform(request(method, "/profile").cookie(sessionCookie).header("Origin", OTHER_SITE))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value(OriginCheckFilter.BLOCKED_MESSAGE));
        }
    }

    @Test
    void requestFromTheSystemWorks() throws Exception {
        mockMvc.perform(post("/auth/logout").cookie(sessionCookie).header("Origin", SYSTEM_ORIGIN))
                .andExpect(status().isNoContent());
    }

    @Test
    void refererFromTheSystemIsEnoughWhenTheOriginIsMissing() throws Exception {
        mockMvc.perform(post("/auth/logout").cookie(sessionCookie).header("Referer", SYSTEM_ORIGIN + "/painel-paciente"))
                .andExpect(status().isNoContent());
    }

    @Test
    void readingDoesNotNeedOrigin() throws Exception {
        mockMvc.perform(get("/auth/me").cookie(sessionCookie))
                .andExpect(status().isOk());
    }

    // o token no cabecalho o navegador n manda sozinho entao outro site n tem como usar
    @Test
    void bearerTokenDoesNotNeedOrigin() throws Exception {
        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + tokenService.generateToken(patient)))
                .andExpect(status().isNoContent());
    }

    // sem cookie n tem sessao pra aproveitar
    @Test
    void requestWithoutSessionCookieDoesNotNeedOrigin() throws Exception {
        mockMvc.perform(post("/auth/logout").header("Origin", OTHER_SITE))
                .andExpect(status().isNoContent());
    }
}
