package dev.uffs.doisag.controller;

import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.Cannabinoid;
import dev.uffs.doisag.enums.ConcentrationUnit;
import dev.uffs.doisag.enums.ConsentType;
import dev.uffs.doisag.enums.PrescriptionStatus;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.enums.Spectrum;
import dev.uffs.doisag.infra.BrazilianFormat;
import dev.uffs.doisag.model.Anamnesis;
import dev.uffs.doisag.model.Annulment;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.DoseEscalationStep;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Prescription;
import dev.uffs.doisag.model.PrescriptionComponent;
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
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    @Autowired private EntityManager entityManager;

    private Prescriber prescriber;
    private Patient patient;
    private Appointment appointment;

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

        appointment = new Appointment();
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

    // o csv segue a mesma regra do historico e da impressao: consulta q ja passou e foi atendida,
    // estava marcada ou virou falta. pedido sem resposta, recusado, cancelada e consulta futura
    // n sao atendimento e ficam fora, senao o arquivo mistura pedido com atendimento feito
    // a anulada entra marcada com o motivo, como no historico
    @Test
    void appointmentsCsvFollowsTheHistoryRule() throws Exception {
        Appointment annulled = saveAppointment(TODAY.minusDays(6).atTime(10, 0), AppointmentStatus.CONCLUIDA,
                "atendimento anulado");
        annulled.setAnnulment(new Annulment(prescriber, "Registro de outro paciente"));
        appointmentRepository.save(annulled);
        saveAppointment(TODAY.minusDays(5).atTime(10, 0), AppointmentStatus.NAO_COMPARECEU, "faltou na consulta");
        saveAppointment(TODAY.minusDays(4).atTime(10, 0), AppointmentStatus.AGENDADA, "marcada e ja passou");
        saveAppointment(TODAY.minusDays(3).atTime(10, 0), AppointmentStatus.CANCELADA, "consulta cancelada");
        saveAppointment(TODAY.minusDays(2).atTime(10, 0), AppointmentStatus.RECUSADA, "pedido recusado");
        saveAppointment(TODAY.plusDays(2).atTime(10, 0), AppointmentStatus.SOLICITADA, "pedido esperando resposta");
        saveAppointment(TODAY.plusDays(3).atTime(10, 0), AppointmentStatus.AGENDADA, "marcada pro futuro");

        String csv = download("appointments.csv", "", patient);

        assertThat(csv).contains("Dor lombar crônica").contains("faltou na consulta").contains("marcada e ja passou");
        assertThat(csv).contains("atendimento anulado").contains("\"sim\";\"Registro de outro paciente\"");
        // a anulacao n troca a situacao da consulta, entao a anulada segue concluida e a marca fica na coluna Anulada
        assertThat(csv).contains("\"Remota\";\"Concluída\";\"Dra. Exportação\";\"atendimento anulado\"");
        assertThat(csv).doesNotContain("consulta cancelada").doesNotContain("pedido recusado")
                .doesNotContain("pedido esperando resposta").doesNotContain("marcada pro futuro");
        // o cabecalho e as quatro consultas q entram no historico
        assertThat(csv.lines().count()).isEqualTo(5);
    }

    // o csv traz o texto q a tela mostra e n o codigo do banco, igual as respostas do scales.csv (#96)
    @Test
    void appointmentsCsvHasTheScreenText() throws Exception {
        saveAppointment(TODAY.minusDays(5).atTime(10, 0), AppointmentStatus.NAO_COMPARECEU, "faltou na consulta");

        String csv = download("appointments.csv", "", prescriber);

        assertThat(csv).contains("\"09:00\";\"Presencial\";\"Concluída\"");
        assertThat(csv).contains("\"10:00\";\"Remota\";\"Falta\"");
        assertThat(csv).doesNotContain("PRESENCIAL").doesNotContain("REMOTA").doesNotContain("CONCLUIDA")
                .doesNotContain("NAO_COMPARECEU");
    }

    // com ponto a planilha em portugues le 72.5 como texto e n soma nem tira media do peso
    @Test
    void weightComesOutWithADecimalComma() throws Exception {
        appointment.setWeight(72.5f);
        appointment.setHeight(170);
        appointmentRepository.save(appointment);
        Appointment other = saveAppointment(TODAY.minusDays(5).atTime(10, 0), AppointmentStatus.CONCLUIDA,
                "retorno");
        other.setWeight(80f);
        appointmentRepository.save(other);
        entityManager.flush();
        entityManager.clear();

        String csv = download("appointments.csv", "", prescriber);

        assertThat(csv).contains("\"72,5\";\"170\"");
        // sem o zero a toa do 80.0
        assertThat(csv).contains("\"80\";\"\"");
        assertThat(csv).doesNotContain("72.5").doesNotContain("80.0");
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

    // escala e anamnese anuladas levam o motivo, igual consulta e prescricao, pq eh ele
    // q separa erro de digitacao de correcao clinica
    @Test
    void annulledScaleAndAnamnesisKeepTheReason() throws Exception {
        ScaleResponse response = new ScaleResponse();
        response.setPatient(patient);
        response.setScaleType(ScaleType.ACOMPANHAMENTO_SEMANAL);
        response.setPeriodStart(TODAY.minusDays(2));
        response.setPeriodEnd(TODAY.minusDays(2));
        response.setAnswers(new LinkedHashMap<>(Map.of("dor", 3)));
        response.setAnnulment(new Annulment(prescriber, "Preenchida no dia errado"));
        responseRepository.save(response);

        Anamnesis anamnesis = new Anamnesis();
        anamnesis.setPatient(patient);
        anamnesis.setAssessmentDate(TODAY.minusDays(15));
        anamnesis.setReasonForVisit("Ansiedade");
        anamnesis.setAnnulment(new Annulment(prescriber, "Ficha de outra pessoa"));
        anamnesisRepository.save(anamnesis);
        entityManager.flush();
        entityManager.clear();

        String scales = download("scales.csv", "", patient);
        assertThat(scales).contains("\"Código\";\"Anulada\";\"Motivo da anulação\"");
        assertThat(scales).contains("\"Dor\";\"3\";\"3\";\"sim\";\"Preenchida no dia errado\"");
        assertThat(scales).contains("\"Dor\";\"7\";\"7\";\"não\";\"\"");

        String anamneses = download("anamneses.csv", "", patient);
        assertThat(anamneses).contains("\"Resposta\";\"Anulada\";\"Motivo da anulação\"");
        assertThat(anamneses).contains("\"Motivo principal\";\"Ansiedade\";\"sim\";\"Ficha de outra pessoa\"");
        assertThat(anamneses).contains("\"Sono\";\"Durmo mal\";\"não\";\"\"");
    }

    @Test
    void aEvolucaoSaiComDataEValor() throws Exception {
        String csv = download("progress.csv", "?attribute=DOR&period=DIAS_30", patient);

        assertThat(csv).contains("\"Atributo\";\"Data\";\"Valor\"");
        // o atributo com o nome q o seletor do grafico mostra, n o codigo
        assertThat(csv).contains("\"Acompanhamento semanal\";\"Dor\";");
        assertThat(csv).doesNotContain("\"DOR\"");
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
        assertThat(csv).contains("\"Escala de ansiedade de Hamilton\";\"Escore total\";")
                .contains("\"30\";\"Ansiedade grave\"");
        assertThat(csv).doesNotContain("ESCORE_HAMILTON");

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

    // a composicao sai como a tela mostra. o flush e o clear fazem o valor voltar do banco
    // como no download de vdd, e o banco devolve 20.000, q no brasil eh vinte mil
    @Test
    void compositionComesOutLikeTheScreenShowsIt() throws Exception {
        Prescription prescription = new Prescription();
        prescription.setAppointment(appointment);
        prescription.setProductDescription("Óleo full spectrum");
        prescription.setPosology("3 gotas à noite");
        prescription.addComponent(new PrescriptionComponent(Cannabinoid.CBD, new BigDecimal("20"),
                ConcentrationUnit.PERCENTUAL));
        prescription.addComponent(new PrescriptionComponent(Cannabinoid.THC, new BigDecimal("0.3"),
                ConcentrationUnit.PERCENTUAL));
        prescription.addComponent(new PrescriptionComponent(Cannabinoid.CBG, new BigDecimal("25"),
                ConcentrationUnit.MG_POR_ML));
        prescription.addComponent(new PrescriptionComponent(Cannabinoid.OUTRO, new BigDecimal("2.5"),
                ConcentrationUnit.MG_POR_ML));
        prescriptionRepository.save(prescription);
        entityManager.flush();
        entityManager.clear();

        String csv = download("prescriptions.csv", "", patient);

        assertThat(csv).contains("CBD 20%").contains("THC 0,3%").contains("CBG 25 mg/mL")
                .contains("Outro 2,5 mg/mL");
        assertThat(csv).doesNotContain("20.000").doesNotContain("0.300")
                .doesNotContain("PERCENTUAL").doesNotContain("MG_POR_ML").doesNotContain("OUTRO");
    }

    // a receita tem mais q oleo, composicao e posologia (RF05): qnd a posologia eh "conforme o plano"
    // a linha ficava sem dose nenhuma, e instrucoes, cuidados, efeitos esperados e quem prescreveu
    // so saiam na impressao. a linha inteira eh conferida pra coluna nenhuma sair do lugar
    @Test
    void prescriptionWithADosePlanComesOutWithTheWholeLine() throws Exception {
        Prescription prescription = new Prescription();
        prescription.setAppointment(appointment);
        prescription.setProductDescription("Óleo de CBD 20%");
        prescription.setPosology("Conforme o plano");
        prescription.setAdministrationRoute("Sublingual");
        prescription.setTreatmentDurationDays(90);
        prescription.setInstructions("Pingar embaixo da língua e esperar um minuto");
        prescription.setPrecautions("Não dirigir nas primeiras semanas");
        prescription.setExpectedEffects("Sono melhor em duas semanas");
        prescription.setObservation("Rever a dose no retorno");
        prescription.addEscalationStep(stepOf(1, "2 gotas à noite", "começar devagar"));
        prescription.addEscalationStep(stepOf(2, "4 gotas à noite", null));
        prescriptionRepository.save(prescription);
        entityManager.flush();
        entityManager.clear();

        String csv = download("prescriptions.csv", "", patient);

        assertThat(csv).contains("\"Paciente\";\"Data da consulta\";\"Situação\";\"Prescritor\";\"Produto\";"
                + "\"Espectro\";\"Composição\";\"Marca\";\"Lote\";\"Volume\";\"Posologia\";\"Plano de dose\";\"Via\";"
                + "\"Duração em dias\";\"Próxima consulta\";\"Instruções de uso\";\"Precauções e contraindicações\";"
                + "\"Efeitos esperados\";\"Observações\";\"Anulada\";\"Motivo da anulação\"");
        assertThat(csv).contains("\"Paciente da exportação\";\"" + BrazilianFormat.date(TODAY.minusDays(10))
                + "\";\"Vigente\";\"Dra. Exportação\";\"Óleo de CBD 20%\";\"\";\"\";\"\";\"\";\"\";\"Conforme o plano\";"
                + "\"Semana 1: 2 gotas à noite · começar devagar; Semana 2: 4 gotas à noite\";\"Sublingual\";\"90\";"
                + "\"\";\"Pingar embaixo da língua e esperar um minuto\";\"Não dirigir nas primeiras semanas\";"
                + "\"Sono melhor em duas semanas\";\"Rever a dose no retorno\";\"não\";\"\"");
    }

    // situacao e espectro com o texto da tela, sem o codigo do banco
    @Test
    void prescriptionsCsvHasTheScreenText() throws Exception {
        Prescription replaced = new Prescription();
        replaced.setAppointment(appointment);
        replaced.setProductDescription("Óleo full spectrum");
        replaced.setPosology("3 gotas à noite");
        replaced.setSpectrum(Spectrum.FULL_SPECTRUM);
        replaced.setStatus(PrescriptionStatus.SUBSTITUIDA);
        prescriptionRepository.save(replaced);

        String csv = download("prescriptions.csv", "", prescriber);

        assertThat(csv).contains("\";\"Vigente\";\"Dra. Exportação\";\"Óleo de CBD 10%\";");
        assertThat(csv).contains("\";\"Substituída\";\"Dra. Exportação\";\"Óleo full spectrum\";\"Full spectrum\";");
        assertThat(csv).doesNotContain("VIGENTE").doesNotContain("SUBSTITUIDA").doesNotContain("FULL_SPECTRUM");
    }

    // anular a vigente troca o status pra SUBSTITUIDA, pra ela n contar como vigente (NOOP-04),
    // mas ninguem substituiu ela. a situacao diz anulada, como a tela
    @Test
    void annulledCurrentPrescriptionComesOutAsAnnulled() throws Exception {
        Prescription current = new Prescription();
        current.setAppointment(appointment);
        current.setProductDescription("Óleo full spectrum");
        current.setPosology("3 gotas à noite");
        current = prescriptionRepository.save(current);

        mockMvc.perform(put("/prescriptions/" + current.getId() + "/annul")
                        .header("Authorization", bearerTokenOf(prescriber))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Receita do paciente errado\"}"))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        String csv = download("prescriptions.csv", "", prescriber);

        assertThat(csv).contains("\";\"Anulada\";\"Dra. Exportação\";\"Óleo full spectrum\";");
        assertThat(csv).contains("\"sim\";\"Receita do paciente errado\"");
        assertThat(csv).doesNotContain("SUBSTITUIDA").doesNotContain("Substituída");
    }

    // no modo sem identificacao o prescritor da receita vira numero, igual no appointments.csv
    @Test
    void deidentifiedPrescriptionsCarryOnlyThePrescriberNumber() throws Exception {
        String csv = download("prescriptions.csv", "?deidentified=true", prescriber);

        assertThat(csv).contains("\"Identificador\";\"Data da consulta\";\"Situação\";\"Prescritor\"");
        assertThat(csv).contains("\"prescritor " + prescriber.getId() + "\"");
        assertThat(csv).doesNotContain("Dra. Exportação");
    }

    // texto da receita q comeca como formula n pode rodar na planilha de quem abre o arquivo,
    // e as colunas novas passam pela mesma protecao das antigas
    @Test
    void prescriptionTextStartingLikeAFormulaGetsTheApostrophe() throws Exception {
        Prescription prescription = new Prescription();
        prescription.setAppointment(appointment);
        prescription.setProductDescription("Óleo de CBD 5%");
        prescription.setPosology("1 gota");
        prescription.setInstructions("=HYPERLINK(\"http://site-estranho\")");
        prescription.setPrecautions("-nenhuma");
        prescription.addEscalationStep(stepOf(1, "+2 gotas", null));
        prescriptionRepository.save(prescription);
        entityManager.flush();
        entityManager.clear();

        String csv = download("prescriptions.csv", "", patient);

        assertThat(csv).contains("\"'=HYPERLINK(\"\"http://site-estranho\"\")\"");
        assertThat(csv).contains("\"'-nenhuma\"");
        // o plano comeca sempre com a palavra Semana, entao o degrau com sinal na frente n vira formula
        assertThat(csv).contains("\"Semana 1: +2 gotas\"");
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

    private DoseEscalationStep stepOf(int week, String dosage, String note) {
        DoseEscalationStep step = new DoseEscalationStep();
        step.setWeek(week);
        step.setDosage(dosage);
        step.setNote(note);
        return step;
    }

    private Appointment saveAppointment(LocalDateTime dateTime, AppointmentStatus status, String observation) {
        Appointment other = new Appointment();
        other.setPatient(patient);
        other.setPrescriber(prescriber);
        other.setDateTime(dateTime);
        other.setModality(AppointmentModality.REMOTA);
        other.setStatus(status);
        other.setDurationMinutes(30);
        other.setClinicalObservation(observation);
        return appointmentRepository.save(other);
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
