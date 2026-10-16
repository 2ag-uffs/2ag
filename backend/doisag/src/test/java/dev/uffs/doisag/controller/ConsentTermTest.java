package dev.uffs.doisag.controller;

import dev.uffs.doisag.enums.ConsentType;
import dev.uffs.doisag.infra.PendingTermException;
import dev.uffs.doisag.model.Address;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.ConsentAcceptanceRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.ConsentTermService;
import dev.uffs.doisag.service.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// termo de consentimento do cadastro (RF36), o aceite de novo qnd a versao muda (#58)
// e o consentimento de pesquisa separado (RF37)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ConsentTermTest {

    private static final String PROFILE_JSON = """
            {"name":"Paciente do Termo","birthDate":"1990-04-12","phone":"49999887766",
             "address":{"street":"Rua das Flores","number":"120","city":"Chapeco","state":"SC"}}
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private PatientRepository patientRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private ConsentAcceptanceRepository consentAcceptanceRepository;
    @Autowired private ConsentTermService consentTermService;
    @Autowired private TokenService tokenService;

    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createAccounts() {
        prescriber = new Prescriber();
        prescriber.setName("Prescritora do Termo");
        prescriber.setEmail("termo-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente do Termo");
        patient.setEmail("termo-paciente@email.com");
        patient.setPassword("hash");
        patient.setCpf("52998224725");
        patient.setBirthDate(LocalDate.of(1990, 4, 12));
        patient.setPhone("49999887766");
        patient.setAddress(new Address("Rua das Flores", "120", "Chapeco", "SC", "Brasil"));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    private String bearerTokenOf(Users user) {
        return "Bearer " + tokenService.generateToken(user);
    }

    @Test
    void anyoneCanReadTheCurrentTermBeforeHavingAnAccount() throws Exception {
        mockMvc.perform(get("/consent-term"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(ConsentTermService.CURRENT_VERSION))
                .andExpect(jsonPath("$.text").value(containsString("13.709/2018")));
    }

    @Test
    void clinicDataFromTheServerReplacesTheMarkers() {
        String text = new ConsentTermService(null, " Instituto Exemplo Ltda ", " 00.000.000/0001-91 ",
                "dados@exemplo.com.br").getCurrentTerm().text();

        assertThat(text).contains("Instituto Exemplo Ltda, CNPJ 00.000.000/0001-91, responsável pelo seu atendimento");
        assertThat(text).contains("dados@exemplo.com.br");
        assertThat(text).doesNotContain("[NOME DA CLÍNICA]").doesNotContain("[CNPJ DA CLÍNICA]")
                .doesNotContain("[CONTATO DO ENCARREGADO");
    }

    // sem as variaveis a api sobe e o termo continua com os colchetes
    @Test
    void blankClinicDataKeepsTheMarkers() {
        String text = new ConsentTermService(null, "", null, null).getCurrentTerm().text();

        assertThat(text).contains("[NOME DA CLÍNICA]").contains("[CNPJ DA CLÍNICA]")
                .contains("[CONTATO DO ENCARREGADO DE DADOS DA CLÍNICA]");
    }

    @Test
    void patientWhoAcceptedTheCurrentVersionHasNothingPending() throws Exception {
        consentTermService.registerAcceptance(patient, ConsentTermService.CURRENT_VERSION);

        mockMvc.perform(get("/auth/me").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termPending").value(false));
    }

    @Test
    void patientWithAnOldVersionOnlyReadsUntilAcceptingTheNewOne() throws Exception {
        consentTermService.registerAcceptance(patient, "2026-01-versao-antiga");

        mockMvc.perform(get("/auth/me").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termPending").value(true));
        mockMvc.perform(get("/profile").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/profile")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PROFILE_JSON))
                .andExpect(status().isForbidden())
                .andExpect(header().string(PendingTermException.HEADER, "true"))
                .andExpect(jsonPath("$.message").value(PendingTermException.MESSAGE));

        mockMvc.perform(post("/consent-term/accept")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termVersion\":\"" + ConsentTermService.CURRENT_VERSION + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(jsonPath("$.termPending").value(false));

        mockMvc.perform(put("/profile")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PROFILE_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(get("/auth/me").header("Authorization", bearerTokenOf(patient)))
                .andExpect(jsonPath("$.termPending").value(false));
    }

    @Test
    void acceptingTwiceKeepsOneRecordOfTheVersion() throws Exception {
        consentTermService.registerAcceptance(patient, "2026-01-versao-antiga");
        String body = "{\"termVersion\":\"" + ConsentTermService.CURRENT_VERSION + "\"}";

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/consent-term/accept")
                            .header("Authorization", bearerTokenOf(patient))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        assertThat(consentAcceptanceRepository.findAllByUserIdOrderByAcceptedAtDesc(patient.getId()))
                .extracting("termVersion")
                .containsExactlyInAnyOrder("2026-01-versao-antiga", ConsentTermService.CURRENT_VERSION);
    }

    // a tela pode ter ficado aberta enquanto o texto mudou de novo
    @Test
    void acceptingAVersionThatIsNoLongerCurrentIsRefused() throws Exception {
        consentTermService.registerAcceptance(patient, "2026-01-versao-antiga");

        mockMvc.perform(post("/consent-term/accept")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termVersion\":\"2026-01-versao-antiga\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PatientService.OUTDATED_TERM_MESSAGE));
    }

    // conta so com o aceite de pesquisa n fica presa no aceite do termo do cadastro
    @Test
    void researchConsentAloneDoesNotBlockThePatient() throws Exception {
        consentTermService.registerAcceptance(patient, ConsentType.RESEARCH, ConsentTermService.RESEARCH_VERSION);

        mockMvc.perform(get("/auth/me").header("Authorization", bearerTokenOf(patient)))
                .andExpect(jsonPath("$.termPending").value(false));
        mockMvc.perform(put("/profile")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PROFILE_JSON))
                .andExpect(status().isOk());
    }

    // o aceite de pesquisa n conta como aceite do termo do cadastro, nem com a msm versao no texto
    @Test
    void researchConsentDoesNotSatisfyTheRegistrationTerm() throws Exception {
        consentTermService.registerAcceptance(patient, ConsentType.RESEARCH, ConsentTermService.CURRENT_VERSION);

        consentTermService.registerAcceptance(patient, "2026-01-versao-antiga");

        mockMvc.perform(get("/auth/me").header("Authorization", bearerTokenOf(patient)))
                .andExpect(jsonPath("$.termPending").value(true));
        mockMvc.perform(post("/consent-term/accept")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termVersion\":\"" + ConsentTermService.CURRENT_VERSION + "\"}"))
                .andExpect(jsonPath("$.termPending").value(false));

        assertThat(consentAcceptanceRepository.findAllByUserIdOrderByAcceptedAtDesc(patient.getId()))
                .filteredOn(acceptance -> acceptance.getTermType() == ConsentType.REGISTRATION)
                .extracting("termVersion")
                .containsExactlyInAnyOrder("2026-01-versao-antiga", ConsentTermService.CURRENT_VERSION);
    }

    @Test
    void anyoneCanReadTheResearchTermBeforeHavingAnAccount() throws Exception {
        mockMvc.perform(get("/consent-term/research"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(ConsentTermService.RESEARCH_VERSION))
                .andExpect(jsonPath("$.text").value(containsString("opcional")));
    }

    @Test
    void patientAcceptsAndRevokesTheResearchAndTheHistoryStays() throws Exception {
        String body = "{\"termVersion\":\"" + ConsentTermService.RESEARCH_VERSION + "\"}";

        mockMvc.perform(post("/consent-term/research/accept")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.acceptedAt").exists());
        mockMvc.perform(get("/patients/" + patient.getId()).header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.researchConsent").value(true));

        mockMvc.perform(post("/consent-term/research/revoke").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get("/patients/" + patient.getId()).header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.researchConsent").value(false));
        mockMvc.perform(get("/profile").header("Authorization", bearerTokenOf(patient)))
                .andExpect(jsonPath("$.researchConsent.active").value(false))
                .andExpect(jsonPath("$.researchConsent.termChanged").value(false));

        assertThat(consentAcceptanceRepository.findAllByUserIdOrderByAcceptedAtDesc(patient.getId()))
                .singleElement()
                .satisfies(acceptance -> {
                    assertThat(acceptance.getTermType()).isEqualTo(ConsentType.RESEARCH);
                    assertThat(acceptance.getRevokedAt()).isNotNull();
                });
    }

    @Test
    void researchTextThatChangedAsksAgain() throws Exception {
        consentTermService.registerAcceptance(patient, ConsentType.RESEARCH, "2025-01-texto-antigo");

        mockMvc.perform(get("/profile").header("Authorization", bearerTokenOf(patient)))
                .andExpect(jsonPath("$.researchConsent.active").value(false))
                .andExpect(jsonPath("$.researchConsent.termChanged").value(true));
        mockMvc.perform(post("/consent-term/research/accept")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termVersion\":\"2025-01-texto-antigo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ConsentTermService.OUTDATED_RESEARCH_MESSAGE));
    }

    @Test
    void onlyPatientsUseTheResearchConsent() throws Exception {
        mockMvc.perform(post("/consent-term/research/accept")
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termVersion\":\"" + ConsentTermService.RESEARCH_VERSION + "\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/consent-term/research/revoke").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/profile").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.researchConsent").doesNotExist());
    }

    @Test
    void onlyPatientsAcceptTheTerm() throws Exception {
        mockMvc.perform(post("/consent-term/accept")
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termVersion\":\"" + ConsentTermService.CURRENT_VERSION + "\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/consent-term/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termVersion\":\"" + ConsentTermService.CURRENT_VERSION + "\"}"))
                .andExpect(status().isUnauthorized());
    }
}
