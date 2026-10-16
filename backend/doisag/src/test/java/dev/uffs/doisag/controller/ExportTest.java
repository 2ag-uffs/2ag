package dev.uffs.doisag.controller;

import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.ConsentType;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.model.Anamnesis;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Prescription;
import dev.uffs.doisag.model.ScaleResponse;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AnamnesisRepository;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.ConsentAcceptanceRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.PrescriptionRepository;
import dev.uffs.doisag.repository.ScaleResponseRepository;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.ConsentTermService;
import dev.uffs.doisag.service.ExportService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// exportacao em csv (RF33)
//
// o paciente leva os proprios dados e o prescritor os dos pacientes
// dele. o modo sem identificacao eh pra pesquisa e n pode levar nome nem contato
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ExportTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired private MockMvc mockMvc;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private PrescriptionRepository prescriptionRepository;
    @Autowired private AnamnesisRepository anamnesisRepository;
    @Autowired private ScaleResponseRepository responseRepository;
    @Autowired private TokenService tokenService;
    @Autowired private ConsentTermService consentTermService;
    @Autowired private ConsentAcceptanceRepository consentAcceptanceRepository;

    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createClinicalRecords() {
        prescriber = new Prescriber();
        prescriber.setName("Dra. Exportação");
        prescriber.setEmail("export-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente da exportação");
        patient.setEmail("export-paciente@email.com");
        patient.setPassword("hash");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
        // o arquivo sem identificacao eh o de pesquisa e so sai de quem aceitou (RF37)
        consentTermService.registerAcceptance(patient, ConsentType.RESEARCH, ConsentTermService.RESEARCH_VERSION);

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setPrescriber(prescriber);
        appointment.setDateTime(TODAY.minusDays(10).atTime(9, 0));
        appointment.setModality(AppointmentModality.PRESENCIAL);
        appointment.setStatus(AppointmentStatus.CONCLUIDA);
        appointment.setDurationMinutes(60);
        appointment.setDiagnosis("Dor lombar crônica");
        appointment.setTherapeuticPlan("Manter a dose; reavaliar em 30 dias");
        appointment = appointmentRepository.save(appointment);

        Prescription prescription = new Prescription();
        prescription.setAppointment(appointment);
        prescription.setProductDescription("Óleo de CBD 10%");
        prescription.setPosology("2 gotas pela manhã");
        prescription.setObservation("Guardar o frasco longe da luz");
        prescriptionRepository.save(prescription);

        Anamnesis anamnesis = new Anamnesis();
        anamnesis.setPatient(patient);
        anamnesis.setAssessmentDate(TODAY.minusDays(20));
        anamnesis.setReasonForVisit("Dor nas costas há dois anos");
        anamnesis.setSleepHabits("Durmo mal");
        anamnesisRepository.save(anamnesis);

        ScaleResponse response = new ScaleResponse();
        response.setPatient(patient);
        response.setScaleType(ScaleType.ACOMPANHAMENTO_SEMANAL);
        response.setPeriodStart(TODAY.minusDays(3));
        response.setPeriodEnd(TODAY.minusDays(3));
        response.setAnswers(new LinkedHashMap<>(Map.of("dor", 7, "comentario", "Semana difícil")));
        responseRepository.save(response);
    }

    @Test
    void oPacienteBaixaAsProprias() throws Exception {
        String csv = download("appointments.csv", "", patient);

        assertThat(csv).contains("\"Paciente\";\"Data\";\"Hora\"");
        assertThat(csv).contains("Paciente da exportação");
        assertThat(csv).contains("Dor lombar crônica");
        assertThat(csv).contains("Dra. Exportação");
    }

    // o arquivo tem q vir como anexo, senao o navegador abre em vez de baixar
    @Test
    void oArquivoVemComoAnexoEEmCsv() throws Exception {
        mockMvc.perform(get("/patients/" + patient.getId() + "/export/appointments.csv")
                        .header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("consultas-paciente-" + patient.getId())));
    }

    // RNF04 no modo sem identificacao n sai nome nem contato
    @Test
    void oModoSemIdentificacaoTiraONomeDoPaciente() throws Exception {
        String csv = download("appointments.csv", "?deidentified=true", prescriber);

        assertThat(csv).contains("\"Identificador\"");
        assertThat(csv).contains("paciente " + patient.getId());
        assertThat(csv).doesNotContain("Paciente da exportação");
        assertThat(csv).doesNotContain("export-paciente@email.com");
        // o dado clinico continua, q eh o que interessa pra pesquisa
        assertThat(csv).contains("Dor lombar crônica");
    }

    // com um prescritor so na clinica o nome dele ja diz de onde o arquivo veio
    @Test
    void oModoSemIdentificacaoTiraONomeDoPrescritor() throws Exception {
        String csv = download("appointments.csv", "?deidentified=true", prescriber);

        assertThat(csv).contains("prescritor " + prescriber.getId());
        assertThat(csv).doesNotContain("Dra. Exportação");
        assertThat(csv).doesNotContain("export-prescritora@email.com");
    }

    @Test
    void deidentifiedFileNeedsTheResearchConsent() throws Exception {
        consentTermService.revokeResearch(patient);

        mockMvc.perform(get("/patients/" + patient.getId() + "/export/appointments.csv")
                        .param("deidentified", "true")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ExportService.NO_RESEARCH_CONSENT_MESSAGE));
        mockMvc.perform(get("/patients/" + patient.getId() + "/export/progress.csv")
                        .param("attribute", "DOR").param("period", "DIAS_30").param("deidentified", "true")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isBadRequest());

        // o arquivo com nome continua saindo pro prescritor do paciente
        assertThat(download("appointments.csv", "", prescriber)).contains("Paciente da exportação");
    }

    // a pesquisa n abre pra menor de 18, entao a data de nascimento nova retira o aceite
    @Test
    void birthDateOfAMinorRevokesTheResearchConsent() throws Exception {
        mockMvc.perform(put("/profile")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Paciente da exportação","birthDate":"%s","phone":"49999887766",
                                 "address":{"street":"Rua Nova","number":"45","city":"Chapeco","state":"SC"},
                                 "guardian":{"name":"Joana Souza","relationship":"mãe"}}
                                """.formatted(TODAY.minusYears(10))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.researchConsent.active").value(false));

        assertThat(consentAcceptanceRepository.findAllByUserIdOrderByAcceptedAtDesc(patient.getId()))
                .singleElement()
                .satisfies(acceptance -> assertThat(acceptance.getRevokedAt()).isNotNull());
        mockMvc.perform(get("/patients/" + patient.getId() + "/export/appointments.csv")
                        .param("deidentified", "true")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ExportService.NO_RESEARCH_CONSENT_MESSAGE));
    }

    // defesa: menor com aceite antigo valendo tbm n sai no arquivo de pesquisa
    @Test
    void minorWithAnOldResearchConsentStaysOutOfTheDeidentifiedFile() throws Exception {
        patient.setBirthDate(TODAY.minusYears(10));
        patientRepository.save(patient);

        mockMvc.perform(get("/patients/" + patient.getId() + "/export/appointments.csv")
                        .param("deidentified", "true")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ExportService.NO_RESEARCH_CONSENT_MESSAGE));
        mockMvc.perform(get("/patients/" + patient.getId()).header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(jsonPath("$.researchConsent").value(false));
    }

    @Test
    void consentToAnOlderResearchTextDoesNotCount() throws Exception {
        consentTermService.revokeResearch(patient);
        consentTermService.registerAcceptance(patient, ConsentType.RESEARCH, "2025-01-texto-antigo");

        mockMvc.perform(get("/patients/" + patient.getId() + "/export/scales.csv")
                        .param("deidentified", "true")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oModoSemIdentificacaoEhSoDoPrescritor() throws Exception {
        mockMvc.perform(get("/patients/" + patient.getId() + "/export/appointments.csv")
                        .param("deidentified", "true")
                        .header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ExportService.DEIDENTIFIED_IS_FOR_PRESCRIBER_MESSAGE));
    }

    // o nome do arquivo tbm n pode levar o numero do paciente
    @Test
    void oArquivoSemIdentificacaoTemNomeProprio() throws Exception {
        mockMvc.perform(get("/patients/" + patient.getId() + "/export/appointments.csv")
                        .param("deidentified", "true")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("consultas-sem-identificacao-")));
    }

    // o parametro antigo era ignorado e saia o arquivo com nome
    @Test
    void oParametroAntigoEhRecusado() throws Exception {
        mockMvc.perform(get("/patients/" + patient.getId() + "/export/appointments.csv")
                        .param("anonymous", "true")
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ExportController.OLD_PARAM_MESSAGE));
    }

    @Test
    void asEscalasSaemComUmaLinhaPorItem() throws Exception {
        String csv = download("scales.csv", "", patient);

        assertThat(csv).contains("\"Escala\";\"Início do período\"");
        assertThat(csv).contains("Acompanhamento semanal");
        assertThat(csv).contains("\"Dor\";\"7\"");
        assertThat(csv).contains("Semana difícil");
    }

    // o csv precisa sair com o texto q a clinica le na tela, e o codigo fica na coluna do lado
    @Test
    void asEscalasSaemComOTextoDaRespostaEOCodigoAoLado() throws Exception {
        ScaleResponse diary = new ScaleResponse();
        diary.setPatient(patient);
        diary.setScaleType(ScaleType.REGISTRO_SONO);
        diary.setPeriodStart(TODAY.minusDays(1));
        diary.setPeriodEnd(TODAY.minusDays(1));
        diary.setAnswers(new LinkedHashMap<>(Map.of("tempoTotalSono", 450, "diaComum", true)));
        responseRepository.save(diary);

        ScaleResponse pittsburgh = new ScaleResponse();
        pittsburgh.setPatient(patient);
        pittsburgh.setScaleType(ScaleType.ESCALA_PITTSBURGH);
        pittsburgh.setPeriodStart(TODAY.minusDays(2));
        pittsburgh.setPeriodEnd(TODAY.minusDays(2));
        pittsburgh.setAnswers(new LinkedHashMap<>(Map.of("qualidadeGeral", 2)));
        responseRepository.save(pittsburgh);

        String csv = download("scales.csv", "", patient);

        assertThat(csv).contains("\"Item\";\"Resposta\";\"Código\"");
        // minuto vira hora e minuto, sim e nao viram palavra e a escolha vira o rotulo dela
        assertThat(csv).contains("\"7h30\";\"450\"");
        assertThat(csv).contains("\"Sim\";\"true\"");
        assertThat(csv).contains("\"Ruim\";\"2\"");
    }

    @Test
    void aAnamneseSaiComPerguntaEResposta() throws Exception {
        String csv = download("anamneses.csv", "", patient);

        assertThat(csv).contains("\"Motivo principal\";\"Dor nas costas há dois anos\"");
        assertThat(csv).contains("\"Sono\";\"Durmo mal\"");
    }

    @Test
    void aEvolucaoSaiComDataEValor() throws Exception {
        String csv = download("progress.csv", "?attribute=DOR&period=DIAS_30", patient);

        assertThat(csv).contains("\"Atributo\";\"Data\";\"Valor\"");
        assertThat(csv).contains("\"DOR\"");
        assertThat(csv).contains("\"7\"");
    }

    @Test
    void aEvolucaoSemIdentificacaoSaiSemONome() throws Exception {
        String csv = download("progress.csv", "?attribute=DOR&period=DIAS_30&deidentified=true", prescriber);

        assertThat(csv).contains("\"Identificador\";\"Escala\";\"Atributo\"");
        assertThat(csv).contains("paciente " + patient.getId());
        assertThat(csv).doesNotContain("Paciente da exportação");
        assertThat(csv).contains("\"7\"");
    }

    // RN14 o escore sai com a faixa do lado, igual no scales.csv. o MEEM leva a
    // faixa da escolaridade do exame, e o arquivo de pesquisa continua sem nome
    @Test
    void aEvolucaoDeUmEscoreSaiComAFaixa() throws Exception {
        saveScore(ScaleType.ESCALA_HAMILTON, TODAY.minusDays(2), 30, "Ansiedade grave");
        saveScore(ScaleType.MINI_EXAME_ESTADO_MENTAL, TODAY.minusDays(4), 24,
                "Abaixo do esperado para a escolaridade (corte 25)");

        String csv = download("progress.csv", "?attribute=ESCORE_HAMILTON&period=DIAS_30", patient);
        assertThat(csv).contains("\"Paciente\";\"Escala\";\"Atributo\";\"Data\";\"Valor\";\"Faixa\"");
        assertThat(csv).contains("\"ESCORE_HAMILTON\"").contains("\"30\";\"Ansiedade grave\"");

        String deidentified = download("progress.csv", "?attribute=ESCORE_MEEM&period=DIAS_30&deidentified=true",
                prescriber);
        assertThat(deidentified).contains("\"Identificador\";\"Escala\";\"Atributo\";\"Data\";\"Valor\";\"Faixa\"");
        assertThat(deidentified).contains("\"24\";\"Abaixo do esperado para a escolaridade (corte 25)\"");
        assertThat(deidentified).contains("paciente " + patient.getId());
        assertThat(deidentified).doesNotContain("Paciente da exportação");
    }

    // item da ficha n tem faixa publicada e a coluna fica em branco, sem faixa inventada (RN13)
    @Test
    void aEvolucaoDeUmItemDaFichaSaiComAFaixaEmBranco() throws Exception {
        String csv = download("progress.csv", "?attribute=DOR&period=DIAS_30", patient);

        assertThat(csv).contains("\"Valor\";\"Faixa\"");
        assertThat(csv).contains("\"7\";\"\"");
    }

    // responsavel eh dado de outra pessoa e n sai no arquivo de pesquisa (RF38)
    @Test
    void deidentifiedFilesLeaveTheGuardianOut() throws Exception {
        patient.setGuardianName("Joana Responsável");
        patient.setGuardianRelationship("filha");
        patient.setGuardianPhone("49988776655");
        patientRepository.save(patient);

        List<String> files = List.of("appointments.csv?deidentified=true", "prescriptions.csv?deidentified=true",
                "scales.csv?deidentified=true", "anamneses.csv?deidentified=true",
                "progress.csv?attribute=DOR&period=DIAS_30&deidentified=true");
        for (String file : files) {
            String csv = download(file, "", prescriber);
            assertThat(csv).doesNotContain("Joana").doesNotContain("49988776655");
        }
    }

    // o arquivo com nome identifica a pessoa entao leva o nome social e o civil na mesma celula (RN15)
    @Test
    void identifiedFileHasTheSocialNameWithTheCivilName() throws Exception {
        patient.setSocialName("Ana Social");
        patientRepository.save(patient);

        String csv = download("appointments.csv", "", prescriber);

        assertThat(csv).contains("\"Paciente\";\"Data\";\"Hora\"");
        assertThat(csv).contains("\"Ana Social (nome civil: Paciente da exportação)\"");
    }

    // o arquivo de pesquisa n leva nem o nome social nem o civil
    @Test
    void deidentifiedFilesLeaveBothNamesOut() throws Exception {
        patient.setSocialName("Ana Social");
        patientRepository.save(patient);

        List<String> files = List.of("appointments.csv?deidentified=true", "prescriptions.csv?deidentified=true",
                "scales.csv?deidentified=true", "anamneses.csv?deidentified=true",
                "progress.csv?attribute=DOR&period=DIAS_30&deidentified=true");
        for (String file : files) {
            String csv = download(file, "", prescriber);
            assertThat(csv).doesNotContain("Ana Social").doesNotContain("Paciente da exportação");
        }
    }

    @Test
    void asPrescricoesSaemComPosologia() throws Exception {
        String csv = download("prescriptions.csv", "", patient);

        assertThat(csv).contains("Óleo de CBD 10%");
        assertThat(csv).contains("2 gotas pela manhã");
        assertThat(csv).contains("Observações");
        assertThat(csv).contains("Guardar o frasco longe da luz");
    }

    // RF30 ninguem exporta paciente de outro prescritor
    @Test
    void outroPrescritorNaoExporta() throws Exception {
        Prescriber otherPrescriber = new Prescriber();
        otherPrescriber.setName("Outra prescritora");
        otherPrescriber.setEmail("export-outra@email.com");
        otherPrescriber.setPassword("hash");
        Users saved = prescriberRepository.save(otherPrescriber);

        mockMvc.perform(get("/patients/" + patient.getId() + "/export/appointments.csv")
                        .header("Authorization", bearerTokenOf(saved)))
                .andExpect(status().isForbidden());
    }

    private void saveScore(ScaleType scaleType, LocalDate day, int score, String band) {
        ScaleResponse response = new ScaleResponse();
        response.setPatient(patient);
        response.setScaleType(scaleType);
        response.setPeriodStart(day);
        response.setPeriodEnd(day);
        response.setAnswers(new LinkedHashMap<>());
        response.setScore(score);
        response.setScoreBand(band);
        responseRepository.save(response);
    }

    private String download(String file, String query, Users loggedUser) throws Exception {
        return mockMvc.perform(get("/patients/" + patient.getId() + "/export/" + file + query)
                        .header("Authorization", bearerTokenOf(loggedUser)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String bearerTokenOf(Users user) {
        return "Bearer " + tokenService.generateToken(user);
    }
}
