package dev.uffs.doisag.controller;

import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
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
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.PrescriptionRepository;
import dev.uffs.doisag.repository.ScaleResponseRepository;
import dev.uffs.doisag.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// exportacao em csv (RF33)
//
// o paciente leva os proprios dados e o prescritor os dos pacientes
// dele. o modo anonimo eh pra pesquisa e n pode levar dado pessoal
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

    // RNF04 no modo anonimo nenhum dado pessoal sai
    @Test
    void oModoAnonimoTiraONomeDoPaciente() throws Exception {
        String csv = download("appointments.csv", "?anonymous=true", prescriber);

        assertThat(csv).contains("\"Identificador\"");
        assertThat(csv).contains("paciente " + patient.getId());
        assertThat(csv).doesNotContain("Paciente da exportação");
        assertThat(csv).doesNotContain("export-paciente@email.com");
        // o dado clinico continua, q eh o que interessa pra pesquisa
        assertThat(csv).contains("Dor lombar crônica");
    }

    @Test
    void oModoAnonimoEhSoDoPrescritor() throws Exception {
        mockMvc.perform(get("/patients/" + patient.getId() + "/export/appointments.csv")
                        .param("anonymous", "true")
                        .header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isBadRequest());
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
    void asPrescricoesSaemComPosologia() throws Exception {
        String csv = download("prescriptions.csv", "", patient);

        assertThat(csv).contains("Óleo de CBD 10%");
        assertThat(csv).contains("2 gotas pela manhã");
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
