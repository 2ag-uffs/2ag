package dev.uffs.doisag.controller;

import dev.uffs.doisag.enums.ConsentType;
import dev.uffs.doisag.model.Admin;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.ConsentTermService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// baixar um arquivo do prontuario tem operacao propria na trilha (revisao das regras R19):
// uma linha por arquivo, sem a janela de 30 minutos da abertura de tela, com o tipo do arquivo e a marca
// da pesquisa. senao a clinica n sabia dizer quem tirou o prontuario do sistema nem se foi pro estudo
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ExportAuditTest {

    private static final LocalDate TODAY = LocalDate.now();
    private static final String EXPORTS = "$.events[?(@.operation == 'EXPORTACAO')]";

    @Autowired private MockMvc mockMvc;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private UsersRepository usersRepository;
    @Autowired private ConsentTermService consentTermService;
    @Autowired private TokenService tokenService;

    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createAccounts() {
        prescriber = new Prescriber();
        prescriber.setName("Dra. Trilha da Exportação");
        prescriber.setEmail("exporta-trilha-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente da Trilha da Exportação");
        patient.setEmail("exporta-trilha-paciente@email.com");
        patient.setPassword("hash");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    // a abertura da tela segura a janela de 30 minutos, mas cada arquivo baixado vira uma linha
    @Test
    void eachDownloadedFileIsOneExportLine() throws Exception {
        mockMvc.perform(get("/patients/" + patient.getId()).header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk());
        download("appointments.csv", prescriber).andExpect(status().isOk());
        download("appointments.csv", prescriber).andExpect(status().isOk());
        download("scales.csv", prescriber).andExpect(status().isOk());
        download("prescriptions.csv", prescriber).andExpect(status().isOk());
        download("anamneses.csv", prescriber).andExpect(status().isOk());

        readTrail()
                .andExpect(jsonPath("$.events[?(@.operation == 'VISUALIZACAO')]", hasSize(1)))
                .andExpect(jsonPath(EXPORTS + ".operationLabel", contains("Exportação", "Exportação",
                        "Exportação", "Exportação", "Exportação")))
                .andExpect(jsonPath(EXPORTS + ".actorName", contains("Dra. Trilha da Exportação",
                        "Dra. Trilha da Exportação", "Dra. Trilha da Exportação", "Dra. Trilha da Exportação",
                        "Dra. Trilha da Exportação")))
                .andExpect(jsonPath(EXPORTS + ".detail", containsInAnyOrder(
                        "Consultas (appointments.csv), com identificação",
                        "Consultas (appointments.csv), com identificação",
                        "Escalas respondidas (scales.csv), com identificação",
                        "Prescrições (prescriptions.csv), com identificação",
                        "Anamnese (anamneses.csv), com identificação")));
    }

    // a marca da pesquisa responde depois se os dados de alguem ja foram pro estudo
    @Test
    void deidentifiedFileCarriesTheResearchMark() throws Exception {
        consentTermService.registerAcceptance(patient, ConsentType.RESEARCH, ConsentTermService.RESEARCH_VERSION);

        mockMvc.perform(get("/patients/" + patient.getId() + "/export/scales.csv")
                        .param("deidentified", "true")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk());

        readTrail().andExpect(jsonPath(EXPORTS + ".detail",
                contains("Escalas respondidas (scales.csv), sem identificação, para pesquisa")));
    }

    // a serie do grafico baixada eh exportacao e n mais uma abertura de tela
    @Test
    void progressFileIsAnExportAndNotAChartView() throws Exception {
        mockMvc.perform(get("/patients/" + patient.getId() + "/export/progress.csv")
                        .param("attribute", "DOR").param("period", "DIAS_30")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk());

        readTrail()
                .andExpect(jsonPath("$.events", hasSize(1)))
                .andExpect(jsonPath(EXPORTS + ".detail", contains("Evolução (progress.csv), com identificação")));
    }

    // o arquivo q o proprio paciente baixa tbm sai do sistema, entao entra na trilha
    @Test
    void patientDownloadingTheOwnFileIsRecordedToo() throws Exception {
        download("appointments.csv", patient).andExpect(status().isOk());

        readTrail()
                .andExpect(jsonPath(EXPORTS + ".actorRole", contains("PATIENT")))
                .andExpect(jsonPath(EXPORTS + ".detail", contains("Consultas (appointments.csv), com identificação")));
    }

    // o arquivo recusado n saiu, entao n tem linha
    @Test
    void refusedFileRecordsNothing() throws Exception {
        mockMvc.perform(get("/patients/" + patient.getId() + "/export/appointments.csv")
                        .param("deidentified", "true")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isBadRequest());

        readTrail().andExpect(jsonPath("$.events", hasSize(0)));
    }

    // o MEEM q o paciente pede eh recusado antes de gravar a linha, sem depender da transacao desfeita
    @Test
    void mentalStateExamRefusedToThePatientRecordsNothing() throws Exception {
        mockMvc.perform(get("/patients/" + patient.getId() + "/export/progress.csv")
                        .param("attribute", "ESCORE_MEEM").param("period", "DIAS_30")
                        .header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isForbidden());

        readTrail().andExpect(jsonPath("$.events", hasSize(0)));
    }

    // a administracao ve q houve exportacao, sem o tipo do arquivo, q diria o q foi do prontuario
    @Test
    void adminSeesTheExportWithoutTheFileType() throws Exception {
        download("anamneses.csv", prescriber).andExpect(status().isOk());
        Admin admin = new Admin();
        admin.setName("Administração da Exportação");
        admin.setEmail("exporta-trilha-admin@email.com");
        admin.setPassword("hash");
        Users savedAdmin = usersRepository.save(admin);

        String body = mockMvc.perform(get("/admin/audit-events")
                        .param("from", TODAY.toString()).param("to", TODAY.toString())
                        .header("Authorization", bearerTokenOf(savedAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(EXPORTS + ".recordType", contains("PRONTUARIO")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("anamneses.csv").doesNotContain("Anamnese");
    }

    private ResultActions download(String file, Users user) throws Exception {
        return mockMvc.perform(get("/patients/" + patient.getId() + "/export/" + file)
                .header("Authorization", bearerTokenOf(user)));
    }

    private ResultActions readTrail() throws Exception {
        return mockMvc.perform(get("/patients/" + patient.getId() + "/audit-events")
                        .param("from", TODAY.toString()).param("to", TODAY.toString())
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk());
    }

    private String bearerTokenOf(Users user) {
        return "Bearer " + tokenService.generateToken(user);
    }
}
