package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.GuardianDTO;
import dev.uffs.doisag.enums.ConsentType;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.PendingTermException;
import dev.uffs.doisag.model.Address;
import dev.uffs.doisag.model.ConsentAcceptance;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.ConsentAcceptanceRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.ConsentTermService;
import dev.uffs.doisag.service.PatientInviteService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @Autowired private PatientInviteService patientInviteService;
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

    private ConsentAcceptance acceptanceOfVersion(String termVersion) {
        return consentAcceptanceRepository.findAllByUserIdOrderByAcceptedAtDesc(patient.getId()).stream()
                .filter(acceptance -> acceptance.getTermVersion().equals(termVersion))
                .findFirst()
                .orElseThrow();
    }

    // paciente de 10 anos, com o responsavel legal ou sem ele (conta de antes da regra)
    private void makePatientTenYearsOld(String guardianName) {
        patient.setBirthDate(LocalDate.now().minusYears(10));
        patient.setGuardianName(guardianName);
        patient.setGuardianRelationship(guardianName == null ? null : "mãe");
        patientRepository.save(patient);
    }

    @Test
    void anyoneCanReadTheCurrentTermBeforeHavingAnAccount() throws Exception {
        mockMvc.perform(get("/consent-term"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(ConsentTermService.CURRENT_VERSION))
                .andExpect(jsonPath("$.text").value(containsString("13.709/2018")));
    }

    // clausula em destaque pro responsavel legal do menor de 18 (LGPD art. 14 e Codigo Civil)
    @Test
    void termExplainsThatTheLegalGuardianAcceptsForMinors() throws Exception {
        mockMvc.perform(get("/consent-term"))
                .andExpect(jsonPath("$.text").value(containsString("9. Paciente com menos de 18 anos")))
                .andExpect(jsonPath("$.text").value(containsString("ATENÇÃO")))
                .andExpect(jsonPath("$.text").value(containsString("a LGPD (art. 14) e o Código Civil")))
                .andExpect(jsonPath("$.text").value(containsString("menos de 16 anos")))
                .andExpect(jsonPath("$.text").value(containsString("16 ou 17 anos")))
                .andExpect(jsonPath("$.text").value(containsString("A clínica explica ao paciente")))
                .andExpect(jsonPath("$.text").value(containsString("nome social")));
    }

    @Test
    void clinicDataFromTheServerReplacesTheMarkers() {
        String text = new ConsentTermService(null, null, " Instituto Exemplo Ltda ", " 00.000.000/0001-91 ",
                "dados@exemplo.com.br").getCurrentTerm().text();

        assertThat(text).contains("Instituto Exemplo Ltda, CNPJ 00.000.000/0001-91, responsável pelo seu atendimento");
        assertThat(text).contains("dados@exemplo.com.br");
        assertThat(text).doesNotContain("[NOME DA CLÍNICA]").doesNotContain("[CNPJ DA CLÍNICA]")
                .doesNotContain("[CONTATO DO ENCARREGADO");
    }

    // sem as variaveis a api sobe e o termo continua com os colchetes
    @Test
    void blankClinicDataKeepsTheMarkers() {
        String text = new ConsentTermService(null, null, "", null, null).getCurrentTerm().text();

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

    // o responsavel com o termo pendente na conta de um filho abre o convite do outro (RF38):
    // a tela de cadastro confere o convite antes de pedir pra sair da conta, e isso n pode dar 403
    @Test
    void patientWithPendingTermStillChecksAnInvite() throws Exception {
        consentTermService.registerAcceptance(patient, "2026-01-versao-antiga");
        String token = patientInviteService.createInvite(prescriber.getId()).token();

        mockMvc.perform(post("/invites/lookup")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(PendingTermException.HEADER))
                .andExpect(jsonPath("$.prescriberName").value("Prescritora do Termo"));
        mockMvc.perform(post("/invites/lookup")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"convite-que-nao-existe\"}"))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist(PendingTermException.HEADER))
                .andExpect(jsonPath("$.message").value(PatientInviteService.INVALID_INVITE_MESSAGE));
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

    // o aceite de novo do menor tbm guarda o responsavel q aceitou
    @Test
    void minorAcceptingTheNewVersionRecordsTheLegalGuardian() throws Exception {
        consentTermService.registerAcceptance(patient, "2026-01-versao-antiga");
        makePatientTenYearsOld("Joana Souza");

        mockMvc.perform(post("/consent-term/accept")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termVersion\":\"" + ConsentTermService.CURRENT_VERSION + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termPending").value(false));

        ConsentAcceptance acceptance = acceptanceOfVersion(ConsentTermService.CURRENT_VERSION);
        assertThat(acceptance.getGuardianName()).isEqualTo("Joana Souza");
        assertThat(acceptance.getGuardianRelationship()).isEqualTo("mãe");
        assertThat(acceptanceOfVersion("2026-01-versao-antiga").getGuardianName()).isNull();
    }

    // conta de menor sem responsavel so existe de antes da regra, e ai o aceite n vale sozinho
    @Test
    void minorWithoutLegalGuardianCannotAcceptAlone() throws Exception {
        consentTermService.registerAcceptance(patient, "2026-01-versao-antiga");
        makePatientTenYearsOld(null);

        mockMvc.perform(post("/consent-term/accept")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termVersion\":\"" + ConsentTermService.CURRENT_VERSION + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("guardian.name"))
                .andExpect(jsonPath("$.errors[0].message").value(GuardianDTO.LEGAL_GUARDIAN_NAME_MESSAGE));
        mockMvc.perform(post("/consent-term/accept")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termVersion\":\"" + ConsentTermService.CURRENT_VERSION
                                + "\",\"guardian\":{\"name\":\"Joana Souza\",\"relationship\":\" \"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("guardian.relationship"));

        assertThat(consentAcceptanceRepository.findAllByUserIdOrderByAcceptedAtDesc(patient.getId()))
                .extracting("termVersion")
                .containsExactly("2026-01-versao-antiga");
        assertThat(patientRepository.findById(patient.getId()).orElseThrow().getGuardianName()).isNull();
    }

    // o termo pendente trava o perfil, entao o responsavel vem junto do aceite
    @Test
    void minorWithoutLegalGuardianSendsItWithTheAcceptance() throws Exception {
        consentTermService.registerAcceptance(patient, "2026-01-versao-antiga");
        makePatientTenYearsOld(null);

        mockMvc.perform(post("/consent-term/accept")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termVersion\":\"" + ConsentTermService.CURRENT_VERSION
                                + "\",\"guardian\":{\"name\":\" Joana Souza \",\"relationship\":\"mãe\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termPending").value(false));

        Patient savedPatient = patientRepository.findById(patient.getId()).orElseThrow();
        assertThat(savedPatient.getGuardianName()).isEqualTo("Joana Souza");
        assertThat(savedPatient.getGuardianRelationship()).isEqualTo("mãe");
        ConsentAcceptance acceptance = acceptanceOfVersion(ConsentTermService.CURRENT_VERSION);
        assertThat(acceptance.getGuardianName()).isEqualTo("Joana Souza");
        assertThat(acceptance.getGuardianRelationship()).isEqualTo("mãe");
    }

    // conta de menor feita qnd o parentesco ainda era opcional tem o nome do responsavel e mais nada
    // o aceite guarda o nome e o parentesco, entao o parentesco vem junto do aceite
    @Test
    void minorWhoseGuardianHasNoRelationshipSendsItWithTheAcceptance() throws Exception {
        consentTermService.registerAcceptance(patient, "2026-01-versao-antiga");
        patient.setBirthDate(LocalDate.now().minusYears(10));
        patient.setGuardianName("Joana Souza");
        patient.setGuardianPhone("49988776655");
        patientRepository.save(patient);

        mockMvc.perform(post("/consent-term/accept")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termVersion\":\"" + ConsentTermService.CURRENT_VERSION + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("guardian.relationship"))
                .andExpect(jsonPath("$.errors[0].message").value(GuardianDTO.LEGAL_GUARDIAN_RELATIONSHIP_MESSAGE));
        assertThat(consentAcceptanceRepository.findAllByUserIdOrderByAcceptedAtDesc(patient.getId()))
                .extracting("termVersion")
                .containsExactly("2026-01-versao-antiga");

        mockMvc.perform(post("/consent-term/accept")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termVersion\":\"" + ConsentTermService.CURRENT_VERSION
                                + "\",\"guardian\":{\"name\":\"Joana Souza\",\"relationship\":\"mãe\","
                                + "\"phone\":\"49988776655\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termPending").value(false));

        Patient savedPatient = patientRepository.findById(patient.getId()).orElseThrow();
        assertThat(savedPatient.getGuardianRelationship()).isEqualTo("mãe");
        assertThat(savedPatient.getGuardianPhone()).isEqualTo("49988776655");
        ConsentAcceptance acceptance = acceptanceOfVersion(ConsentTermService.CURRENT_VERSION);
        assertThat(acceptance.getGuardianName()).isEqualTo("Joana Souza");
        assertThat(acceptance.getGuardianRelationship()).isEqualTo("mãe");
    }

    // nenhum caminho grava o aceite de menor sem o parentesco do responsavel
    @Test
    void minorAcceptanceIsNotSavedWithoutTheGuardianRelationship() {
        patient.setBirthDate(LocalDate.now().minusYears(10));
        patient.setGuardianName("Joana Souza");
        patientRepository.save(patient);

        assertThatThrownBy(() -> consentTermService.registerAcceptance(patient, ConsentTermService.CURRENT_VERSION))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ConsentTermService.GUARDIAN_REQUIRED_MESSAGE);
        assertThat(consentAcceptanceRepository.findAllByUserIdOrderByAcceptedAtDesc(patient.getId())).isEmpty();
    }

    // o responsavel q ja estava salvo n eh trocado pelo q vier no aceite
    @Test
    void guardianSentWithTheAcceptanceDoesNotReplaceTheSavedOne() throws Exception {
        consentTermService.registerAcceptance(patient, "2026-01-versao-antiga");
        makePatientTenYearsOld("Joana Souza");

        mockMvc.perform(post("/consent-term/accept")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termVersion\":\"" + ConsentTermService.CURRENT_VERSION
                                + "\",\"guardian\":{\"name\":\"Outra Pessoa\",\"relationship\":\"tia\"}}"))
                .andExpect(status().isOk());

        assertThat(patientRepository.findById(patient.getId()).orElseThrow().getGuardianName())
                .isEqualTo("Joana Souza");
        assertThat(acceptanceOfVersion(ConsentTermService.CURRENT_VERSION).getGuardianName()).isEqualTo("Joana Souza");
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

    // a pesquisa com menor espera o comite de etica definir o assentimento dele
    @Test
    void minorCannotAcceptTheResearchYet() throws Exception {
        makePatientTenYearsOld("Joana Souza");

        mockMvc.perform(post("/consent-term/research/accept")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termVersion\":\"" + ConsentTermService.RESEARCH_VERSION + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ConsentTermService.MINOR_RESEARCH_MESSAGE));

        assertThat(consentAcceptanceRepository.findAllByUserIdOrderByAcceptedAtDesc(patient.getId())).isEmpty();
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
