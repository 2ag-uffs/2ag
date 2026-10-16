package dev.uffs.doisag.controller;

import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.model.Annulment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.PatientInvite;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.ScaleResponse;
import dev.uffs.doisag.model.ScaleTask;
import dev.uffs.doisag.model.TreatmentProtocol;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.PatientInviteRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.ScaleResponseRepository;
import dev.uffs.doisag.repository.ScaleTaskRepository;
import dev.uffs.doisag.repository.TreatmentProtocolRepository;
import dev.uffs.doisag.security.SecureTokens;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.IndicatorsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// indicadores do piloto (RF39): so numero somado dos pacientes do prescritor logado
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IndicatorsTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired private MockMvc mockMvc;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private ScaleTaskRepository taskRepository;
    @Autowired private ScaleResponseRepository responseRepository;
    @Autowired private TreatmentProtocolRepository protocolRepository;
    @Autowired private PatientInviteRepository inviteRepository;
    @Autowired private TokenService tokenService;

    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createPrescriberAndPatient() {
        prescriber = savePrescriber("indicadores-prescritora@email.com");
        patient = savePatient("Paciente dos indicadores", "indicadores-paciente@email.com", prescriber);
    }

    @Test
    void semDatasUsaOsUltimos90DiasAteOntem() throws Exception {
        indicators("")
                .andExpect(jsonPath("$.from").value(TODAY.minusDays(90).toString()))
                .andExpect(jsonPath("$.to").value(TODAY.minusDays(1).toString()))
                .andExpect(jsonPath("$.goalPercent").value(70))
                .andExpect(jsonPath("$.formsDue").value(0))
                .andExpect(jsonPath("$.onTimePercent").isEmpty())
                .andExpect(jsonPath("$.scales.length()").value(0));
    }

    // no prazo eh respondido ate o ultimo dia, e o prazo de hoje ou de antes do periodo fica de fora
    @Test
    void contaNoPrazoSoQuemRespondeuAteOFimDoPeriodo() throws Exception {
        saveTask(patient, ScaleType.ESCALA_HAMILTON, TODAY.minusDays(16), TODAY.minusDays(10),
                ScaleTaskStatus.RESPONDIDA, TODAY.minusDays(12));
        saveTask(patient, ScaleType.ESCALA_PITTSBURGH, TODAY.minusDays(11), TODAY.minusDays(5),
                ScaleTaskStatus.NAO_RESPONDIDA, null);
        // a anamnese enviada depois do prazo conta como atrasada
        saveTask(patient, ScaleType.ANAMNESE, TODAY.minusDays(26), TODAY.minusDays(20),
                ScaleTaskStatus.RESPONDIDA, TODAY.minusDays(19));
        saveTask(patient, ScaleType.REGISTRO_DOR, TODAY.minusDays(6), TODAY,
                ScaleTaskStatus.RESPONDIDA, TODAY.minusDays(1));
        saveTask(patient, ScaleType.REGISTRO_DOR, TODAY.minusDays(97), TODAY.minusDays(91),
                ScaleTaskStatus.RESPONDIDA, TODAY.minusDays(92));

        indicators("")
                .andExpect(jsonPath("$.formsDue").value(3))
                .andExpect(jsonPath("$.formsOnTime").value(1))
                .andExpect(jsonPath("$.onTimePercent").value(33))
                .andExpect(jsonPath("$.patientsWithForms").value(1))
                .andExpect(jsonPath("$.scales.length()").value(3))
                .andExpect(jsonPath("$.scales[0].scaleType").value("ESCALA_HAMILTON"))
                .andExpect(jsonPath("$.scales[0].scaleName").value("Escala de ansiedade de Hamilton"))
                .andExpect(jsonPath("$.scales[0].onTimePercent").value(100))
                .andExpect(jsonPath("$.scales[1].scaleType").value("ESCALA_PITTSBURGH"))
                .andExpect(jsonPath("$.scales[1].formsOnTime").value(0))
                .andExpect(jsonPath("$.scales[2].scaleType").value("ANAMNESE"))
                .andExpect(jsonPath("$.scales[2].formsOnTime").value(0));
    }

    // no diario basta um dia valido, e os dias preenchidos aparecem a parte
    @Test
    void oDiarioContaComUmDiaEMostraOsDiasPreenchidos() throws Exception {
        ScaleTask task = saveTask(patient, ScaleType.ACOMPANHAMENTO_SEMANAL, TODAY.minusDays(9), TODAY.minusDays(3),
                ScaleTaskStatus.RESPONDIDA, TODAY.minusDays(3));
        saveDay(task, TODAY.minusDays(9), false);
        saveDay(task, TODAY.minusDays(8), false);
        saveDay(task, TODAY.minusDays(6), false);
        saveDay(task, TODAY.minusDays(5), true);

        indicators("")
                .andExpect(jsonPath("$.formsDue").value(1))
                .andExpect(jsonPath("$.formsOnTime").value(1))
                .andExpect(jsonPath("$.diaryDaysExpected").value(7))
                .andExpect(jsonPath("$.diaryDaysFilled").value(3))
                .andExpect(jsonPath("$.diaryDaysPercent").value(42))
                .andExpect(jsonPath("$.scales[0].daily").value(true))
                .andExpect(jsonPath("$.scales[0].daysExpected").value(7))
                .andExpect(jsonPath("$.scales[0].daysFilled").value(3));
    }

    // a regra olha a data e os dias ligados, n a situacao: o job das 8h pode ainda n ter fechado a tarefa
    @Test
    void oPrazoNaoDependeDaSituacaoDaTarefa() throws Exception {
        // diario ainda pendente com dois dias validos conta no prazo
        ScaleTask pendingDiary = saveTask(patient, ScaleType.REGISTRO_SONO, TODAY.minusDays(20), TODAY.minusDays(14),
                ScaleTaskStatus.PENDENTE, null);
        saveDay(pendingDiary, TODAY.minusDays(20), false);
        saveDay(pendingDiary, TODAY.minusDays(18), false);
        // diario respondido mas so com dia anulado fica fora do prazo
        ScaleTask annulledDiary = saveTask(patient, ScaleType.ACOMPANHAMENTO_SEMANAL, TODAY.minusDays(13),
                TODAY.minusDays(7), ScaleTaskStatus.RESPONDIDA, TODAY.minusDays(7));
        saveDay(annulledDiary, TODAY.minusDays(12), true);
        // escala pendente com o prazo vencido e sem resposta entra no total e fica fora do prazo
        saveTask(patient, ScaleType.ESCALA_PITTSBURGH, TODAY.minusDays(9), TODAY.minusDays(3),
                ScaleTaskStatus.PENDENTE, null);

        indicators("")
                .andExpect(jsonPath("$.formsDue").value(3))
                .andExpect(jsonPath("$.formsOnTime").value(1))
                .andExpect(jsonPath("$.onTimePercent").value(33))
                .andExpect(jsonPath("$.diaryDaysExpected").value(14))
                .andExpect(jsonPath("$.diaryDaysFilled").value(2))
                .andExpect(jsonPath("$.scales[0].scaleType").value("ACOMPANHAMENTO_SEMANAL"))
                .andExpect(jsonPath("$.scales[0].formsOnTime").value(0))
                .andExpect(jsonPath("$.scales[0].daysFilled").value(0))
                .andExpect(jsonPath("$.scales[1].scaleType").value("REGISTRO_SONO"))
                .andExpect(jsonPath("$.scales[1].formsOnTime").value(1))
                .andExpect(jsonPath("$.scales[1].daysFilled").value(2))
                .andExpect(jsonPath("$.scales[2].scaleType").value("ESCALA_PITTSBURGH"))
                .andExpect(jsonPath("$.scales[2].formsDue").value(1))
                .andExpect(jsonPath("$.scales[2].formsOnTime").value(0));
    }

    @Test
    void aPorcentagemArredondaPraBaixo() throws Exception {
        saveTask(patient, ScaleType.REGISTRO_DOR, TODAY.minusDays(27), TODAY.minusDays(21),
                ScaleTaskStatus.RESPONDIDA, TODAY.minusDays(25));
        saveTask(patient, ScaleType.REGISTRO_DOR, TODAY.minusDays(20), TODAY.minusDays(14),
                ScaleTaskStatus.RESPONDIDA, TODAY.minusDays(14));
        saveTask(patient, ScaleType.REGISTRO_DOR, TODAY.minusDays(13), TODAY.minusDays(7),
                ScaleTaskStatus.NAO_RESPONDIDA, null);

        indicators("")
                .andExpect(jsonPath("$.formsOnTime").value(2))
                .andExpect(jsonPath("$.onTimePercent").value(66));
    }

    // no ciclo hoje: acompanhamento valendo hoje, conta ativa e fora do arquivo
    @Test
    void oCicloDeHojeContaSoQuemTemAcompanhamentoValendo() throws Exception {
        saveProtocol(patient, TODAY.minusDays(10), true);

        Patient archived = savePatient("Arquivada", "indicadores-arquivada@email.com", prescriber);
        saveProtocol(archived, TODAY.minusDays(10), true);
        archived.setArchivedAt(LocalDateTime.now());
        patientRepository.save(archived);

        Patient ended = savePatient("Encerrado", "indicadores-encerrado@email.com", prescriber);
        saveProtocol(ended, TODAY.minusDays(10), false);

        Patient startsTomorrow = savePatient("Amanha", "indicadores-amanha@email.com", prescriber);
        saveProtocol(startsTomorrow, TODAY.plusDays(1), true);

        // ainda ativo pq o job das 8h n rodou, mas o fim foi ontem
        Patient endedYesterday = savePatient("Terminou ontem", "indicadores-ontem@email.com", prescriber);
        saveProtocol(endedYesterday, TODAY.minusDays(90), true);

        Patient inactiveAccount = savePatient("Desativada", "indicadores-desativada@email.com", prescriber);
        saveProtocol(inactiveAccount, TODAY.minusDays(10), true);
        inactiveAccount.setActive(false);
        patientRepository.save(inactiveAccount);

        // o total eh do msm grupo do ciclo: fora do arquivo e com a conta ativa
        indicators("")
                .andExpect(jsonPath("$.patientsInCycle").value(1))
                .andExpect(jsonPath("$.activePatients").value(4));
    }

    @Test
    void osConvitesDoPeriodoViramOFunil() throws Exception {
        Patient withProtocol = savePatient("Com acompanhamento", "indicadores-com@email.com", prescriber);
        saveProtocol(withProtocol, TODAY.minusDays(20), false);
        Patient withoutProtocol = savePatient("Sem acompanhamento", "indicadores-sem@email.com", prescriber);

        saveInvite(prescriber, TODAY.minusDays(30).atTime(9, 0), withProtocol, null);
        saveInvite(prescriber, TODAY.minusDays(20).atTime(9, 0), withoutProtocol, null);
        saveInvite(prescriber, TODAY.minusDays(10).atTime(9, 0), null, LocalDateTime.now().minusDays(9));
        // criado ontem e ainda no prazo, entao n eh falha ainda
        saveInvite(prescriber, TODAY.minusDays(1).atTime(23, 0), null, null);
        // n foi usado nem cancelado, mas o link ja venceu: foi enviado e n esta mais em aberto
        saveInvite(prescriber, TODAY.minusDays(40).atTime(9, 0), null, null);
        saveInvite(prescriber, TODAY.minusDays(100).atTime(9, 0), null, null);

        indicators("")
                .andExpect(jsonPath("$.invitesSent").value(4))
                .andExpect(jsonPath("$.invitesRegistered").value(2))
                .andExpect(jsonPath("$.invitesInCycle").value(1))
                .andExpect(jsonPath("$.invitesStillOpen").value(1));
    }

    // arquivar encurta o prazo, e sem resposta a tarefa segue contando como fora do prazo
    @Test
    void pacienteArquivadoContinuaNaConta() throws Exception {
        saveTask(patient, ScaleType.REGISTRO_DOR, TODAY.minusDays(5), TODAY.minusDays(2),
                ScaleTaskStatus.NAO_RESPONDIDA, null);
        patient.setArchivedAt(TODAY.minusDays(2).atTime(10, 0));
        patientRepository.save(patient);

        indicators("")
                .andExpect(jsonPath("$.formsDue").value(1))
                .andExpect(jsonPath("$.formsOnTime").value(0))
                .andExpect(jsonPath("$.onTimePercent").value(0))
                .andExpect(jsonPath("$.activePatients").value(0));
    }

    // RF30 o prescritor so ve os numeros dos proprios pacientes, e sem nome nenhum
    @Test
    void naoMisturaOutroPrescritorENaoMostraNome() throws Exception {
        Prescriber otherPrescriber = savePrescriber("indicadores-outra@email.com");
        Patient otherPatient = savePatient("Paciente de outro prescritor", "indicadores-outro@email.com",
                otherPrescriber);
        saveProtocol(otherPatient, TODAY.minusDays(10), true);
        saveTask(otherPatient, ScaleType.ESCALA_HAMILTON, TODAY.minusDays(16), TODAY.minusDays(10),
                ScaleTaskStatus.RESPONDIDA, TODAY.minusDays(12));
        saveInvite(otherPrescriber, TODAY.minusDays(30).atTime(9, 0), otherPatient, null);

        indicators("")
                .andExpect(jsonPath("$.activePatients").value(1))
                .andExpect(jsonPath("$.patientsInCycle").value(0))
                .andExpect(jsonPath("$.invitesSent").value(0))
                .andExpect(jsonPath("$.formsDue").value(0));

        String body = mockMvc.perform(get("/indicators").header("Authorization", bearerTokenOf(otherPrescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formsDue").value(1))
                .andExpect(jsonPath("$.patientsInCycle").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Paciente de outro prescritor").doesNotContain("indicadores-outro@email.com");
    }

    @Test
    void periodoIncompletoInvertidoOuAteHojeDa400() throws Exception {
        String token = bearerTokenOf(prescriber);
        mockMvc.perform(get("/indicators?from=" + TODAY.minusDays(10)).header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(IndicatorsService.INCOMPLETE_RANGE_MESSAGE));
        mockMvc.perform(get("/indicators?from=" + TODAY.minusDays(2) + "&to=" + TODAY.minusDays(5))
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(IndicatorsService.INVALID_RANGE_MESSAGE));
        mockMvc.perform(get("/indicators?from=" + TODAY.minusDays(5) + "&to=" + TODAY)
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(IndicatorsService.OPEN_DEADLINE_MESSAGE));
    }

    @Test
    void oPeriodoEscolhidoVoltaNaResposta() throws Exception {
        saveTask(patient, ScaleType.ESCALA_HAMILTON, TODAY.minusDays(16), TODAY.minusDays(10),
                ScaleTaskStatus.RESPONDIDA, TODAY.minusDays(12));

        indicators("?from=" + TODAY.minusDays(9) + "&to=" + TODAY.minusDays(1))
                .andExpect(jsonPath("$.from").value(TODAY.minusDays(9).toString()))
                .andExpect(jsonPath("$.formsDue").value(0));
        indicators("?from=" + TODAY.minusDays(10) + "&to=" + TODAY.minusDays(10))
                .andExpect(jsonPath("$.formsDue").value(1));
    }

    @Test
    void pacienteNaoAbreOsIndicadores() throws Exception {
        mockMvc.perform(get("/indicators").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isForbidden());
    }

    private ResultActions indicators(String query) throws Exception {
        return mockMvc.perform(get("/indicators" + query).header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk());
    }

    private ScaleTask saveTask(Patient owner, ScaleType scaleType, LocalDate periodStart, LocalDate periodEnd,
                               ScaleTaskStatus taskStatus, LocalDate answeredAt) {
        ScaleTask task = new ScaleTask();
        task.setPatient(owner);
        task.setPrescriber(owner.getPrescriber());
        task.setScaleType(scaleType);
        task.setPeriodStart(periodStart);
        task.setPeriodEnd(periodEnd);
        task.setStatus(taskStatus);
        task.setAnsweredAt(answeredAt);
        return taskRepository.save(task);
    }

    private void saveDay(ScaleTask task, LocalDate day, boolean annulled) {
        ScaleResponse response = new ScaleResponse();
        response.setPatient(task.getPatient());
        response.setTask(task);
        response.setScaleType(task.getScaleType());
        response.setPeriodStart(day);
        response.setPeriodEnd(day);
        if (annulled) {
            response.setAnnulment(new Annulment(prescriber, "dia preenchido errado"));
        }
        responseRepository.save(response);
    }

    private void saveProtocol(Patient owner, LocalDate startDate, boolean active) {
        TreatmentProtocol protocol = new TreatmentProtocol();
        protocol.setPatient(owner);
        protocol.setPrescriber(owner.getPrescriber());
        protocol.setStartDate(startDate);
        protocol.setEndDate(startDate.plusDays(89));
        protocol.setActive(active);
        protocolRepository.save(protocol);
    }

    private void saveInvite(Prescriber owner, LocalDateTime createdAt, Patient usedBy, LocalDateTime cancelledAt) {
        PatientInvite invite = new PatientInvite();
        invite.setPrescriber(owner);
        invite.setTokenHash(SecureTokens.hashToken(SecureTokens.createRandomToken()));
        invite.setCreatedAt(createdAt);
        invite.setExpiresAt(createdAt.plusDays(7));
        if (usedBy != null) {
            invite.setUsedAt(createdAt.plusDays(1));
            invite.setPatient(usedBy);
        }
        invite.setCancelledAt(cancelledAt);
        inviteRepository.save(invite);
    }

    private Patient savePatient(String name, String email, Prescriber owner) {
        Patient newPatient = new Patient();
        newPatient.setName(name);
        newPatient.setEmail(email);
        newPatient.setPassword("hash");
        newPatient.setBirthDate(LocalDate.of(1990, 1, 1));
        newPatient.setPrescriber(owner);
        return patientRepository.save(newPatient);
    }

    private Prescriber savePrescriber(String email) {
        Prescriber newPrescriber = new Prescriber();
        newPrescriber.setName(email.contains("outra") ? "Outra prescritora" : "Prescritora dos indicadores");
        newPrescriber.setEmail(email);
        newPrescriber.setPassword("hash");
        return prescriberRepository.save(newPrescriber);
    }

    private String bearerTokenOf(Users user) {
        return "Bearer " + tokenService.generateToken(user);
    }
}
