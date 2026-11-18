package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.AgendaAppointmentDTO;
import dev.uffs.doisag.dto.AppointmentRescheduleDTO;
import dev.uffs.doisag.dto.AppointmentScheduleDTO;
import dev.uffs.doisag.email.EmailMessage;
import dev.uffs.doisag.email.EmailSender;
import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.infra.BrazilianFormat;
import dev.uffs.doisag.model.Annulment;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Notification;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.ScaleResponse;
import dev.uffs.doisag.model.ScaleTask;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.NotificationRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.ScaleResponseRepository;
import dev.uffs.doisag.repository.ScaleTaskRepository;
import dev.uffs.doisag.service.AppointmentService;
import dev.uffs.doisag.service.PatientArchiveService;
import dev.uffs.doisag.service.ReminderService;
import dev.uffs.doisag.dto.ProtocolItemDTO;
import dev.uffs.doisag.dto.ScaleResponseCreateDTO;
import dev.uffs.doisag.dto.TreatmentProtocolCreateDTO;
import dev.uffs.doisag.enums.Periodicity;
import dev.uffs.doisag.service.ScaleResponseService;
import dev.uffs.doisag.service.ScaleTaskService;
import dev.uffs.doisag.service.TreatmentProtocolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// os lembretes automaticos do dia (RF34)
//
// o job de verdade roda as 8 da manha, entao aqui a gente chama o
// servico passando a data na mao
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReminderTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired private ReminderService reminderService;
    @Autowired private ScaleResponseService scaleResponseService;
    @Autowired private ScaleTaskService scaleTaskService;
    @Autowired private TreatmentProtocolService treatmentProtocolService;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private ScaleTaskRepository taskRepository;
    @Autowired private ScaleResponseRepository responseRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private AppointmentService appointmentService;
    @Autowired private PatientArchiveService patientArchiveService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private EmailSender emailSender;

    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createPrescriberAndPatient() {
        prescriber = new Prescriber();
        prescriber.setName("Dra. Lembrete");
        prescriber.setEmail("lembrete-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente do lembrete");
        patient.setEmail("lembrete-paciente@email.com");
        patient.setPassword("hash");
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    @Test
    void aConsultaDeAmanhaViraAvisoEEmail() {
        saveAppointment(TODAY.plusDays(1).atTime(9, 0), AppointmentStatus.AGENDADA);

        int enviados = reminderService.sendAppointmentReminders(TODAY);

        assertThat(enviados).isEqualTo(1);
        assertThat(notificationsOf(patient)).hasSize(1);
        assertThat(notificationsOf(patient).get(0).getTitle()).isEqualTo("Consulta amanhã");

        ArgumentCaptor<EmailMessage> email = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(email.capture());
        assertThat(email.getValue().to()).isEqualTo("lembrete-paciente@email.com");
        assertThat(email.getValue().text()).contains("Dra. Lembrete");
    }

    // o e-mail chama a pessoa pelo nome social qnd ela tem (RN15)
    @Test
    void oEmailCumprimentaPeloNomeSocial() {
        patient.setSocialName("Nome Social do Lembrete");
        patientRepository.save(patient);
        saveAppointment(TODAY.plusDays(1).atTime(9, 0), AppointmentStatus.AGENDADA);

        reminderService.sendAppointmentReminders(TODAY);

        ArgumentCaptor<EmailMessage> email = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(email.capture());
        assertThat(email.getValue().text()).startsWith("Olá, Nome Social do Lembrete.")
                .doesNotContain("Paciente do lembrete");
    }

    // o job roda todo dia, entao o mesmo lembrete n pode sair de novo
    @Test
    void oMesmoLembreteNaoSaiDuasVezes() {
        saveAppointment(TODAY.plusDays(1).atTime(9, 0), AppointmentStatus.AGENDADA);
        reminderService.sendAppointmentReminders(TODAY);

        int enviados = reminderService.sendAppointmentReminders(TODAY);

        assertThat(enviados).isZero();
        assertThat(notificationsOf(patient)).hasSize(1);
    }

    @Test
    void consultaDeHojeOuDepoisDeAmanhaNaoEntraNoLembrete() {
        saveAppointment(TODAY.atTime(9, 0), AppointmentStatus.AGENDADA);
        saveAppointment(TODAY.plusDays(2).atTime(9, 0), AppointmentStatus.AGENDADA);

        assertThat(reminderService.sendAppointmentReminders(TODAY)).isZero();
        assertThat(notificationsOf(patient)).isEmpty();
    }

    // pedido sem resposta e consulta cancelada n geram lembrete
    @Test
    void pedidoSemRespostaECanceladaNaoGeramLembrete() {
        saveAppointment(TODAY.plusDays(1).atTime(9, 0), AppointmentStatus.SOLICITADA);
        saveAppointment(TODAY.plusDays(1).atTime(11, 0), AppointmentStatus.CANCELADA);

        assertThat(reminderService.sendAppointmentReminders(TODAY)).isZero();
        verify(emailSender, never()).send(any());
    }

    @Test
    void oFormularioQueVenceEmDoisDiasViraLembrete() {
        saveTask(ScaleType.ESCALA_HAMILTON, TODAY.plusDays(2));

        int enviados = reminderService.sendScaleReminders(TODAY);

        assertThat(enviados).isEqualTo(1);
        assertThat(notificationsOf(patient).get(0).getTitle()).isEqualTo("Formulário para responder");
        assertThat(notificationsOf(patient).get(0).getMessage()).contains("Hamilton");
        // o nome da escala eh dado clinico e n vai no e-mail
        ArgumentCaptor<EmailMessage> email = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(email.capture());
        assertThat(email.getValue().text()).doesNotContain("Hamilton").contains("Entre no sistema para ver qual");
    }

    @Test
    void oFormularioComPrazoLongeAindaNaoCobra() {
        saveTask(ScaleType.ESCALA_HAMILTON, TODAY.plusDays(5));

        assertThat(reminderService.sendScaleReminders(TODAY)).isZero();
    }

    // quem ja comecou a responder n precisa de cobranca
    @Test
    void quemJaRespondeuAlgoNaoRecebeCobranca() {
        ScaleTask task = saveTask(ScaleType.ACOMPANHAMENTO_SEMANAL, TODAY.plusDays(1));
        ScaleResponse response = new ScaleResponse();
        response.setPatient(patient);
        response.setScaleType(ScaleType.ACOMPANHAMENTO_SEMANAL);
        response.setPeriodStart(TODAY);
        response.setPeriodEnd(TODAY);
        response.setTask(task);
        response.setAnswers(new LinkedHashMap<>(Map.of("dor", 3)));
        responseRepository.save(response);

        assertThat(reminderService.sendScaleReminders(TODAY)).isZero();
    }

    // dia preenchido de outra semana n eh da tarefa aberta, entao n cala o lembrete dela
    @Test
    void diaDeOutraSemanaNaoCalaOLembrete() {
        saveTask(ScaleType.ACOMPANHAMENTO_SEMANAL, TODAY.plusDays(2));
        scaleResponseService.answer(patient.getId(), ScaleType.ACOMPANHAMENTO_SEMANAL,
                new ScaleResponseCreateDTO(TODAY.minusDays(10), TODAY.minusDays(10), Map.of("dor", 3)));

        assertThat(reminderService.sendScaleReminders(TODAY)).isEqualTo(1);
    }

    // RF18 quem desligou o e-mail continua recebendo o aviso no sistema
    @Test
    void quemDesligouOEmailRecebeSoOAvisoNoSistema() {
        patient.setEmailNotificationsEnabled(false);
        patientRepository.save(patient);
        saveAppointment(TODAY.plusDays(1).atTime(9, 0), AppointmentStatus.AGENDADA);

        reminderService.sendAppointmentReminders(TODAY);

        assertThat(notificationsOf(patient)).hasSize(1);
        verify(emailSender, never()).send(any());
    }

    // o e-mail q falha n pode fazer o mesmo aviso nascer de novo todo dia
    @Test
    void emailQueFalhaNaoRepeteOAviso() {
        saveTask(ScaleType.ESCALA_HAMILTON, TODAY.plusDays(2));

        reminderService.sendScaleReminders(TODAY);
        reminderService.sendScaleReminders(TODAY.plusDays(1));

        assertThat(notificationsOf(patient)).hasSize(1);
        verify(emailSender, times(1)).send(any());
    }

    // RN10 a escala pontuada pela metade continua sendo cobrada
    @Test
    void escalaPontuadaPelaMetadeContinuaSendoCobrada() {
        saveTask(ScaleType.ESCALA_HAMILTON, TODAY.plusDays(2));
        scaleResponseService.answer(patient.getId(), ScaleType.ESCALA_HAMILTON,
                new ScaleResponseCreateDTO(null, null, Map.of("humorAnsioso", 2)));

        assertThat(reminderService.sendScaleReminders(TODAY)).isEqualTo(1);
    }

    // no diario um dia preenchido calava a cobranca da semana toda (decisao de 14/11/2026, revisao R6)
    // agora dois dias seguidos em branco viram um lembrete, um so por sequencia de dias em branco
    @Test
    void doisDiasSeguidosEmBrancoNoDiarioViramUmLembretePorSequencia() {
        ScaleTask task = saveTask(ScaleType.ACOMPANHAMENTO_SEMANAL, TODAY.plusDays(2));
        saveDay(task, TODAY.minusDays(4));
        saveDay(task, TODAY.minusDays(3));

        assertThat(reminderService.sendBlankDaysReminders(TODAY)).isEqualTo(1);

        Notification notice = notificationsOf(patient).get(0);
        assertThat(notice.getTitle()).isEqualTo("Dias em branco");
        assertThat(notice.getMessage()).contains("Acompanhamento semanal")
                .contains(formatDate(TODAY.minusDays(2))).contains(formatDate(TODAY.minusDays(1)));
        assertThat(notice.getLink()).isEqualTo("/escalas/acompanhamento-semanal?data=" + TODAY.minusDays(2));
        // o nome da escala eh dado clinico e n vai no e-mail
        ArgumentCaptor<EmailMessage> email = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(email.capture());
        assertThat(email.getValue().text()).doesNotContain("Acompanhamento").contains("Entre no sistema");

        // a mesma sequencia de dias em branco n cobra de novo
        assertThat(reminderService.sendBlankDaysReminders(TODAY.plusDays(1))).isZero();
        // um dia preenchido fecha a sequencia, e dois dias em branco depois dele cobram de novo
        saveDay(task, TODAY);
        assertThat(reminderService.sendBlankDaysReminders(TODAY.plusDays(2))).isZero();
        // na ordem do job: o closeOverdue fecha a semana antes da etapa dos dias em branco
        scaleTaskService.closeOverdue(TODAY.plusDays(3));
        assertThat(reminderService.sendBlankDaysReminders(TODAY.plusDays(3))).isEqualTo(1);
        assertThat(notificationsOf(patient)).hasSize(2);
    }

    // na ordem do job: o closeOverdue fecha a semana antes da etapa dos dias em branco, e os dois
    // ultimos dias do periodo em branco tbm viram lembrete (revisao das regras R6)
    @Test
    void osDoisUltimosDiasEmBrancoDoPeriodoTambemViramLembrete() {
        ScaleTask task = saveTask(ScaleType.ACOMPANHAMENTO_SEMANAL, TODAY.plusDays(2));
        for (LocalDate day = TODAY.minusDays(4); !day.isAfter(TODAY); day = day.plusDays(1)) {
            saveDay(task, day);
        }

        assertThat(scaleTaskService.closeOverdue(TODAY.plusDays(3))).isEqualTo(1);
        assertThat(reminderService.sendBlankDaysReminders(TODAY.plusDays(3))).isEqualTo(1);

        Notification notice = notificationsOf(patient).get(0);
        assertThat(notice.getTitle()).isEqualTo("Dias em branco");
        assertThat(notice.getMessage()).contains(formatDate(TODAY.plusDays(1))).contains(formatDate(TODAY.plusDays(2)));
        assertThat(notice.getLink()).isEqualTo("/escalas/acompanhamento-semanal?data=" + TODAY.plusDays(1));
    }

    // o ultimo dia de uma semana e o primeiro da seguinte em branco tbm sao dois dias seguidos
    @Test
    void oUltimoDiaDaSemanaEOPrimeiroDaSeguinteEmBrancoViramLembrete() {
        ScaleTask firstWeek = saveTask(ScaleType.ACOMPANHAMENTO_SEMANAL, TODAY.plusDays(2));
        for (LocalDate day = TODAY.minusDays(4); !day.isAfter(TODAY.plusDays(1)); day = day.plusDays(1)) {
            saveDay(firstWeek, day);
        }
        scaleTaskService.closeOverdue(TODAY.plusDays(3));
        ScaleTask nextWeek = saveTask(ScaleType.ACOMPANHAMENTO_SEMANAL, TODAY.plusDays(9));
        nextWeek.setPeriodStart(TODAY.plusDays(3));
        taskRepository.save(nextWeek);

        assertThat(reminderService.sendBlankDaysReminders(TODAY.plusDays(3))).isZero();
        assertThat(reminderService.sendBlankDaysReminders(TODAY.plusDays(4))).isEqualTo(1);
        Notification notice = notificationsOf(patient).get(0);
        assertThat(notice.getMessage())
                .contains(formatDate(TODAY.plusDays(2))).contains(formatDate(TODAY.plusDays(3)));
        // a semana anterior ja saiu da grade, entao o link abre ontem, na semana de agora
        assertThat(notice.getLink()).isEqualTo("/escalas/acompanhamento-semanal?data=" + TODAY.plusDays(3));
        // a mesma sequencia n cobra de novo
        assertThat(reminderService.sendBlankDaysReminders(TODAY.plusDays(5))).isZero();
    }

    // a semana q o arquivamento fechou n cobra os dias em branco dela, pq o arquivado n eh mais cobrado
    @Test
    void aSemanaFechadaPeloArquivamentoNaoCobraOsDiasEmBranco() {
        saveTask(ScaleType.ACOMPANHAMENTO_SEMANAL, TODAY.plusDays(2));
        patientArchiveService.archive(patient.getId(), prescriber);
        // o arquivamento vem um segundo dps da tarefa. no mesmo instante do relogio o banco arredondava a hora
        // da tarefa pra cima e ela parecia mais nova q o arquivamento, e o teste falhava de vez em quando
        patient.setArchivedAt(patient.getArchivedAt().plusSeconds(1));
        patientRepository.save(patient);

        assertThat(reminderService.sendBlankDaysReminders(TODAY.plusDays(1))).isZero();
    }

    // o diario enviado a mao dps do arquivamento n foi interrompido por ele: os dois ultimos dias em branco
    // cobram como em qualquer semana
    @Test
    void oDiarioEnviadoDepoisDoArquivamentoCobraOsDoisUltimosDiasEmBranco() {
        patientArchiveService.archive(patient.getId(), prescriber);
        // o envio vem um segundo dps do arquivamento, senao os dois podiam cair no mesmo instante do relogio
        patient.setArchivedAt(patient.getArchivedAt().minusSeconds(1));
        patientRepository.save(patient);
        ScaleTask task = saveTask(ScaleType.ACOMPANHAMENTO_SEMANAL, TODAY.plusDays(2));
        for (LocalDate day = TODAY.minusDays(4); !day.isAfter(TODAY); day = day.plusDays(1)) {
            saveDay(task, day);
        }

        assertThat(scaleTaskService.closeOverdue(TODAY.plusDays(3))).isEqualTo(1);
        assertThat(reminderService.sendBlankDaysReminders(TODAY.plusDays(3))).isEqualTo(1);
        assertThat(notificationsOf(patient).get(0).getTitle()).isEqualTo("Dias em branco");
    }

    // a semana q fechou na troca, com o diario fora do acompanhamento novo, tbm n cobra
    @Test
    void aSemanaDoDiarioTiradoDoAcompanhamentoNaoCobraOsDiasEmBranco() {
        ScaleTask week = saveTask(ScaleType.ACOMPANHAMENTO_SEMANAL, TODAY.minusDays(1));
        week.setFromProtocol(true);
        week.setStatus(ScaleTaskStatus.NAO_RESPONDIDA);
        week.setAnsweredAt(TODAY.minusDays(1));
        taskRepository.save(week);
        treatmentProtocolService.create(patient.getId(), new TreatmentProtocolCreateDTO(TODAY, 90, null, null,
                List.of(new ProtocolItemDTO(ScaleType.ESCALA_HAMILTON, null, Periodicity.SEMANAL))), prescriber);

        assertThat(reminderService.sendBlankDaysReminders(TODAY)).isZero();
    }

    @Test
    void umDiaPreenchidoEntreOsDoisNaoCobra() {
        ScaleTask task = saveTask(ScaleType.REGISTRO_SONO, TODAY.plusDays(2));
        saveDay(task, TODAY.minusDays(1));

        assertThat(reminderService.sendBlankDaysReminders(TODAY)).isZero();
    }

    // o dia antes de a tarefa comecar n eh dia em branco, e escala q n eh diario n entra
    @Test
    void foraDoDiarioOuAntesDoPeriodoNaoCobra() {
        ScaleTask newWeek = saveTask(ScaleType.ACOMPANHAMENTO_SEMANAL, TODAY.plusDays(5));
        newWeek.setPeriodStart(TODAY.minusDays(1));
        taskRepository.save(newWeek);
        saveTask(ScaleType.ESCALA_HAMILTON, TODAY.plusDays(5));

        assertThat(reminderService.sendBlankDaysReminders(TODAY)).isZero();
    }

    // quem recebeu hoje o lembrete do prazo n ganha outro pela mesma sequencia, e a preferencia de e-mail vale
    @Test
    void oLembreteDoPrazoDoDiaJaContaPelaSequencia() {
        patient.setEmailNotificationsEnabled(false);
        patientRepository.save(patient);
        saveTask(ScaleType.ACOMPANHAMENTO_SEMANAL, TODAY.plusDays(2));

        assertThat(reminderService.sendScaleReminders(TODAY)).isEqualTo(1);
        assertThat(reminderService.sendBlankDaysReminders(TODAY)).isZero();
        assertThat(reminderService.sendBlankDaysReminders(TODAY.plusDays(1))).isZero();
        assertThat(notificationsOf(patient)).hasSize(1);
        verify(emailSender, never()).send(any());
    }

    // sem o prescritor ninguem le, como no lembrete do prazo (#148)
    @Test
    void comOPrescritorDesativadoODiarioNaoCobra() {
        saveTask(ScaleType.REGISTRO_SONO, TODAY.plusDays(4));
        prescriber.setActive(false);
        prescriberRepository.save(prescriber);

        assertThat(reminderService.sendBlankDaysReminders(TODAY)).isZero();
    }

    // a sequencia em branco q passa pra semana seguinte eh a mesma e n cobra de novo. ela so volta a cobrar
    // dps de um dia preenchido (revisao das regras R6)
    @Test
    void aSequenciaEmBrancoQuePassaParaASemanaSeguinteNaoCobraDeNovo() {
        ScaleTask firstWeek = saveTask(ScaleType.ACOMPANHAMENTO_SEMANAL, TODAY.plusDays(2));
        assertThat(reminderService.sendBlankDaysReminders(TODAY.minusDays(2))).isEqualTo(1);
        // o job fecha a semana, lida de novo pq o lembrete trava e solta a tarefa
        firstWeek = taskRepository.findById(firstWeek.getId()).orElseThrow();
        firstWeek.setStatus(ScaleTaskStatus.NAO_RESPONDIDA);
        taskRepository.save(firstWeek);
        ScaleTask nextWeek = saveTask(ScaleType.ACOMPANHAMENTO_SEMANAL, TODAY.plusDays(9));
        nextWeek.setPeriodStart(TODAY.plusDays(3));
        taskRepository.save(nextWeek);

        assertThat(reminderService.sendBlankDaysReminders(TODAY.plusDays(5))).isZero();
        assertThat(reminderService.sendBlankDaysReminders(TODAY.plusDays(6))).isZero();

        saveDay(nextWeek, TODAY.plusDays(6));
        assertThat(reminderService.sendBlankDaysReminders(TODAY.plusDays(9))).isEqualTo(1);
        assertThat(notificationsOf(patient)).hasSize(2);
    }

    // a semana q comeca dps de dias sem tarefa nenhuma comeca uma sequencia nova
    @Test
    void depoisDeDiasSemTarefaASequenciaComecaDeNovo() {
        ScaleTask oldWeek = saveTask(ScaleType.REGISTRO_SONO, TODAY.minusDays(4));
        oldWeek.setPeriodStart(TODAY.minusDays(20));
        oldWeek.setPeriodEnd(TODAY.minusDays(14));
        oldWeek.setStatus(ScaleTaskStatus.NAO_RESPONDIDA);
        oldWeek.setBlankDaysRemindedUntil(TODAY.minusDays(19));
        taskRepository.save(oldWeek);
        ScaleTask newWeek = saveTask(ScaleType.REGISTRO_SONO, TODAY.plusDays(4));
        newWeek.setPeriodStart(TODAY.minusDays(2));
        taskRepository.save(newWeek);

        assertThat(reminderService.sendBlankDaysReminders(TODAY)).isEqualTo(1);
    }

    // a avulsa q vira rodada ganha o prazo da periodicidade, e o lembrete do prazo antigo
    // n pode calar o do novo (RF34)
    @Test
    void aAvulsaQueViraRodadaGanhaLembreteDoPrazoNovo() {
        ScaleTask avulsa = scaleTaskService.assign(patient.getId(), ScaleType.ESCALA_HAMILTON, TODAY, 7, false);
        assertThat(reminderService.sendScaleReminders(TODAY.plusDays(4))).isEqualTo(1);
        treatmentProtocolService.create(patient.getId(), new TreatmentProtocolCreateDTO(TODAY.plusDays(5), 90,
                null, null, List.of(new ProtocolItemDTO(ScaleType.ESCALA_HAMILTON, null, Periodicity.SEMANAL))),
                prescriber);

        treatmentProtocolService.designarEscalasVencidas(TODAY.plusDays(5));

        // lida de novo: o lembrete trava a tarefa e le ela de novo, entao a copia de cima ficou pra tras
        ScaleTask round = taskRepository.findById(avulsa.getId()).orElseThrow();
        assertThat(round.isFromProtocol()).isTrue();
        assertThat(round.getPeriodEnd()).isEqualTo(TODAY.plusDays(11));
        assertThat(reminderService.sendScaleReminders(TODAY.plusDays(8))).isZero();
        assertThat(reminderService.sendScaleReminders(TODAY.plusDays(9))).isEqualTo(1);
    }

    // remarcada pra outro horario de amanha dps do lembrete das 8h: o job de amanha ja olha o dia seguinte,
    // entao o lembrete do horario novo sai na hora, senao o paciente ficava so com o e-mail do horario antigo
    @Test
    void aConsultaRemarcadaParaAmanhaDepoisDoLembreteGanhaOLembreteDoHorarioNovo() {
        Appointment appointment = saveAppointment(TODAY.plusDays(1).atTime(9, 0), AppointmentStatus.AGENDADA);
        reminderService.sendAppointmentReminders(TODAY);

        appointmentService.reschedule(appointment.getId(), new AppointmentRescheduleDTO(
                TODAY.plusDays(1).atTime(14, 0), AppointmentModality.PRESENCIAL, null, null));

        ArgumentCaptor<EmailMessage> emails = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, times(2)).send(emails.capture());
        assertThat(emails.getAllValues().get(0).text()).contains("às 09:00");
        assertThat(emails.getAllValues().get(1).text()).contains("às 14:00");
        assertThat(appointmentRepository.findById(appointment.getId()).orElseThrow().getReminderSentAt()).isNotNull();
        // o job de amanha olha o dia seguinte, e o de hoje rodando de novo n repete
        assertThat(reminderService.sendAppointmentReminders(TODAY)).isZero();
    }

    // a consulta marcada pra amanha dps do job de hoje n entra em job nenhum, entao o lembrete sai na hora,
    // sem depender do ciclo do dia. o job q rodar depois trava a consulta, le ela de novo e n repete
    @Test
    void aConsultaMarcadaParaAmanhaGanhaOLembreteNaHoraEOJobNaoRepete() {
        AgendaAppointmentDTO scheduled = appointmentService.schedule(new AppointmentScheduleDTO(patient.getId(),
                TODAY.plusDays(1).atTime(15, 0), AppointmentModality.PRESENCIAL, null, null), prescriber.getId());
        // a de depois de amanha fica com o job de amanha
        appointmentService.schedule(new AppointmentScheduleDTO(patient.getId(),
                TODAY.plusDays(2).atTime(15, 0), AppointmentModality.PRESENCIAL, null, null), prescriber.getId());

        assertThat(remindersOf(patient)).hasSize(1);
        ArgumentCaptor<EmailMessage> email = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(email.capture());
        assertThat(email.getValue().text()).contains("às 15:00");
        assertThat(appointmentRepository.findById(scheduled.id()).orElseThrow().getReminderSentAt()).isNotNull();

        assertThat(reminderService.sendAppointmentReminders(TODAY)).isZero();
        assertThat(remindersOf(patient)).hasSize(1);
        verify(emailSender, times(1)).send(any());
    }

    // o pedido confirmado pra amanha tbm
    @Test
    void oPedidoConfirmadoParaAmanhaGanhaOLembreteNaHora() {
        Appointment request = saveAppointment(TODAY.plusDays(1).atTime(10, 0), AppointmentStatus.SOLICITADA);

        appointmentService.confirm(request.getId());

        ArgumentCaptor<EmailMessage> email = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(email.capture());
        assertThat(email.getValue().text()).contains("às 10:00");
    }

    // a tela de remarcar manda a mesma data qnd so troca a modalidade, a duracao ou o tipo: o lembrete q ja
    // saiu continua valendo e n sai outro igual, nem no job
    @Test
    void aEdicaoNoMesmoHorarioDepoisDoLembreteNaoMandaOutro() {
        Appointment appointment = saveAppointment(TODAY.plusDays(1).atTime(9, 0), AppointmentStatus.AGENDADA);
        reminderService.sendAppointmentReminders(TODAY);

        appointmentService.reschedule(appointment.getId(), new AppointmentRescheduleDTO(
                TODAY.plusDays(1).atTime(9, 0), AppointmentModality.REMOTA, 30, null));

        Appointment edited = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertThat(edited.getModality()).isEqualTo(AppointmentModality.REMOTA);
        assertThat(edited.getReminderSentAt()).isNotNull();
        assertThat(remindersOf(patient)).hasSize(1);
        verify(emailSender, times(1)).send(any());
        assertThat(reminderService.sendAppointmentReminders(TODAY)).isZero();
    }

    // o lembrete na hora segue a mesma regra do job: so o aviso no sistema pra quem desligou o e-mail
    @Test
    void oLembreteNaHoraRespeitaAPreferenciaDeEmail() {
        patient.setEmailNotificationsEnabled(false);
        patientRepository.save(patient);
        Appointment appointment = saveAppointment(TODAY.plusDays(3).atTime(9, 0), AppointmentStatus.AGENDADA);

        appointmentService.reschedule(appointment.getId(), new AppointmentRescheduleDTO(
                TODAY.plusDays(1).atTime(16, 0), AppointmentModality.REMOTA, null, null));

        assertThat(notificationsOf(patient)).extracting(Notification::getTitle).contains("Consulta amanhã");
        verify(emailSender, never()).send(any());
    }

    // o e-mail disse "sua consulta eh amanha" e a prescritora cancelou: o cancelamento tbm vai por e-mail,
    // com o mesmo tipo de conteudo do lembrete (decisao de 14/11/2026, revisao das regras R11)
    @Test
    void aConsultaQueJaRecebeuOLembreteECanceladaGanhaEmailDoCancelamento() {
        Appointment appointment = saveAppointment(TODAY.plusDays(1).atTime(9, 0), AppointmentStatus.AGENDADA);
        reminderService.sendAppointmentReminders(TODAY);

        appointmentService.cancel(appointment.getId(), prescriber);

        ArgumentCaptor<EmailMessage> emails = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, times(2)).send(emails.capture());
        EmailMessage cancellation = emails.getAllValues().get(1);
        assertThat(cancellation.to()).isEqualTo("lembrete-paciente@email.com");
        assertThat(cancellation.subject()).isEqualTo("Sua consulta no 2AG foi cancelada");
        assertThat(cancellation.text()).contains("Sua consulta com Dra. Lembrete de "
                        + BrazilianFormat.dateTime(TODAY.plusDays(1).atTime(9, 0)) + " foi cancelada.")
                .contains(Notification.PATIENT_AGENDA_LINK);
    }

    // remarcada pra outro dia dps do lembrete: o horario novo n ganha lembrete hoje, entao o e-mail diz o novo
    @Test
    void aConsultaQueJaRecebeuOLembreteERemarcadaParaOutroDiaGanhaEmailDaRemarcacao() {
        Appointment appointment = saveAppointment(TODAY.plusDays(1).atTime(9, 0), AppointmentStatus.AGENDADA);
        reminderService.sendAppointmentReminders(TODAY);

        appointmentService.reschedule(appointment.getId(), new AppointmentRescheduleDTO(
                TODAY.plusDays(4).atTime(10, 0), AppointmentModality.REMOTA, null, null));

        ArgumentCaptor<EmailMessage> emails = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, times(2)).send(emails.capture());
        EmailMessage reschedule = emails.getAllValues().get(1);
        assertThat(reschedule.subject()).isEqualTo("Sua consulta no 2AG foi remarcada");
        assertThat(reschedule.text()).contains("Sua consulta com Dra. Lembrete de "
                + BrazilianFormat.dateTime(TODAY.plusDays(1).atTime(9, 0)) + " foi remarcada para "
                + BrazilianFormat.dateTime(TODAY.plusDays(4).atTime(10, 0)) + ".");
        // o horario novo continua com o lembrete dele na vespera
        assertThat(reminderService.sendAppointmentReminders(TODAY.plusDays(3))).isEqualTo(1);
    }

    // sem o lembrete o e-mail nunca disse nada dessa consulta, e o aviso fica so no sistema como antes
    @Test
    void consultaSemLembreteCanceladaOuRemarcadaNaoGanhaEmail() {
        Appointment canceled = saveAppointment(TODAY.plusDays(3).atTime(9, 0), AppointmentStatus.AGENDADA);
        Appointment moved = saveAppointment(TODAY.plusDays(3).atTime(14, 0), AppointmentStatus.AGENDADA);

        appointmentService.cancel(canceled.getId(), prescriber);
        appointmentService.reschedule(moved.getId(), new AppointmentRescheduleDTO(
                TODAY.plusDays(5).atTime(14, 0), AppointmentModality.PRESENCIAL, null, null));

        assertThat(notificationsOf(patient)).extracting(Notification::getTitle)
                .contains("Consulta cancelada", "Consulta remarcada");
        verify(emailSender, never()).send(any());
    }

    // o e-mail do cancelamento segue a preferencia de e-mail igual o lembrete
    @Test
    void oEmailDoCancelamentoRespeitaAPreferenciaDeEmail() {
        Appointment appointment = saveAppointment(TODAY.plusDays(1).atTime(9, 0), AppointmentStatus.AGENDADA);
        patient.setEmailNotificationsEnabled(false);
        patientRepository.save(patient);
        reminderService.sendAppointmentReminders(TODAY);

        appointmentService.cancel(appointment.getId(), prescriber);

        assertThat(notificationsOf(patient)).extracting(Notification::getTitle)
                .contains("Consulta amanhã", "Consulta cancelada");
        verify(emailSender, never()).send(any());
    }

    // quem cancelou foi o proprio paciente, entao n tem o q corrigir por e-mail
    @Test
    void oPacienteQueCancelaAConsultaLembradaNaoGanhaEmail() {
        Appointment appointment = saveAppointment(TODAY.plusDays(3).atTime(9, 0), AppointmentStatus.AGENDADA);
        appointment.setReminderSentAt(LocalDateTime.now());
        appointment.setEmailedDateTime(appointment.getDateTime());
        appointmentRepository.save(appointment);

        appointmentService.cancel(appointment.getId(), patient);

        verify(emailSender, never()).send(any());
    }

    // o e-mail da remarcacao tbm disse um horario, entao cancelar dps dele vai por e-mail de novo
    @Test
    void depoisDoEmailDaRemarcacaoOCancelamentoTambemVaiPorEmail() {
        Appointment appointment = saveAppointment(TODAY.plusDays(1).atTime(9, 0), AppointmentStatus.AGENDADA);
        reminderService.sendAppointmentReminders(TODAY);
        appointmentService.reschedule(appointment.getId(), new AppointmentRescheduleDTO(
                TODAY.plusDays(4).atTime(10, 0), AppointmentModality.REMOTA, null, null));

        appointmentService.cancel(appointment.getId(), prescriber);

        ArgumentCaptor<EmailMessage> emails = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, times(3)).send(emails.capture());
        assertThat(emails.getAllValues()).extracting(EmailMessage::subject).containsExactly(
                "Sua consulta no 2AG é amanhã", "Sua consulta no 2AG foi remarcada", "Sua consulta no 2AG foi cancelada");
        assertThat(emails.getAllValues().get(2).text()).contains(
                BrazilianFormat.dateTime(TODAY.plusDays(4).atTime(10, 0)) + " foi cancelada.");
    }

    // a decisao manda e-mail so no cancelamento e na remarcacao pra outro dia. mudar so a hora no mesmo dia q o
    // e-mail disse, inclusive no proprio dia da consulta, fica so com o aviso no sistema (revisao das regras R11)
    @Test
    void aConsultaQueMudaSoDeHoraNoMesmoDiaDoEmailNaoGanhaEmailDaRemarcacao() {
        LocalDateTime emailedTime = TODAY.plusDays(3).atTime(10, 0);
        Appointment appointment = saveAppointment(emailedTime, AppointmentStatus.AGENDADA);
        appointment.setEmailedDateTime(emailedTime);
        appointmentRepository.save(appointment);

        appointmentService.reschedule(appointment.getId(), new AppointmentRescheduleDTO(
                TODAY.plusDays(3).atTime(15, 0), AppointmentModality.REMOTA, null, null));

        verify(emailSender, never()).send(any());
        assertThat(notificationsOf(patient)).extracting(Notification::getTitle)
                .containsExactly("Consulta remarcada");
        // o horario do ultimo e-mail continua guardado, entao cancelar ou mudar de dia dps ainda vai por e-mail
        assertThat(appointmentRepository.findById(appointment.getId()).orElseThrow().getEmailedDateTime())
                .isEqualTo(emailedTime);
    }

    // o lembrete saiu so no sistema pq o e-mail estava desligado: nenhum e-mail disse nada dessa consulta, entao
    // o cancelamento fica no sistema mesmo com o e-mail ligado dps
    @Test
    void consultaLembradaSoNoSistemaNaoGanhaEmailDoCancelamento() {
        Appointment appointment = saveAppointment(TODAY.plusDays(1).atTime(9, 0), AppointmentStatus.AGENDADA);
        patient.setEmailNotificationsEnabled(false);
        patientRepository.save(patient);
        reminderService.sendAppointmentReminders(TODAY);
        patient.setEmailNotificationsEnabled(true);
        patientRepository.save(patient);

        appointmentService.cancel(appointment.getId(), prescriber);

        verify(emailSender, never()).send(any());
    }

    // conta desativada n consegue entrar pra desligar o e-mail no perfil
    @Test
    void contaDesativadaNaoRecebeEmailDeLembrete() {
        patient.setActive(false);
        patientRepository.save(patient);
        saveAppointment(TODAY.plusDays(1).atTime(9, 0), AppointmentStatus.AGENDADA);

        reminderService.sendAppointmentReminders(TODAY);

        verify(emailSender, never()).send(any());
    }

    // o job pega o dia de amanha inteiro, ate a consulta do fim da noite
    @Test
    void aConsultaDaTardeEDaNoiteDeAmanhaTambemGanhaLembrete() {
        saveAppointment(TODAY.plusDays(1).atTime(14, 0), AppointmentStatus.AGENDADA);
        saveAppointment(TODAY.plusDays(1).atTime(23, 30), AppointmentStatus.AGENDADA);

        assertThat(reminderService.sendAppointmentReminders(TODAY)).isEqualTo(2);
        assertThat(remindersOf(patient)).hasSize(2);
    }

    // o dia vem de quem chama, entao o ciclo de outro dia olha o dia seguinte a ele e n o de amanha
    @Test
    void oCicloDeOutroDiaLembraAConsultaDoDiaSeguinteAEle() {
        Appointment tomorrowAppointment = saveAppointment(TODAY.plusDays(1).atTime(9, 0), AppointmentStatus.AGENDADA);
        Appointment laterAppointment = saveAppointment(TODAY.plusDays(3).atTime(9, 0), AppointmentStatus.AGENDADA);

        assertThat(reminderService.sendAppointmentReminders(TODAY.plusDays(2))).isEqualTo(1);
        assertThat(appointmentRepository.findById(laterAppointment.getId()).orElseThrow().getReminderSentAt())
                .isNotNull();
        assertThat(appointmentRepository.findById(tomorrowAppointment.getId()).orElseThrow().getReminderSentAt())
                .isNull();
    }

    // a consulta anulada foi marcada por engano e n vira lembrete
    @Test
    void aConsultaAnuladaNaoGeraLembrete() {
        Appointment annulled = saveAppointment(TODAY.plusDays(1).atTime(9, 0), AppointmentStatus.AGENDADA);
        annulled.setAnnulment(new Annulment(prescriber, "marcada no paciente errado"));
        appointmentRepository.save(annulled);

        assertThat(reminderService.sendAppointmentReminders(TODAY)).isZero();
        assertThat(notificationsOf(patient)).isEmpty();
        verify(emailSender, never()).send(any());
    }

    // cancelada por outra rota dps da lista do job e antes da trava: eh pulada e n conta como enviada
    @Test
    void aConsultaCanceladaAntesDaTravaNaoContaComoEnviada() {
        Appointment appointment = saveAppointment(TODAY.plusDays(1).atTime(9, 0), AppointmentStatus.AGENDADA);
        // direto no banco, entao a copia da sessao fica velha como a da lista do job
        jdbcTemplate.update("update appointment set status = 'CANCELADA' where id = ?", appointment.getId());

        assertThat(reminderService.sendAppointmentReminders(TODAY)).isZero();
        assertThat(notificationsOf(patient)).isEmpty();
        verify(emailSender, never()).send(any());
    }

    private List<Notification> notificationsOf(Patient owner) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(owner.getId());
    }

    // so os lembretes de consulta, sem o aviso de marcada ou remarcada
    private List<Notification> remindersOf(Patient owner) {
        return notificationsOf(owner).stream()
                .filter(notification -> notification.getTitle().equals("Consulta amanhã"))
                .toList();
    }

    private Appointment saveAppointment(LocalDateTime dateTime, AppointmentStatus status) {
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setPrescriber(prescriber);
        appointment.setDateTime(dateTime);
        appointment.setModality(AppointmentModality.PRESENCIAL);
        appointment.setStatus(status);
        appointment.setDurationMinutes(60);
        return appointmentRepository.save(appointment);
    }

    private void saveDay(ScaleTask task, LocalDate day) {
        ScaleResponse response = new ScaleResponse();
        response.setPatient(patient);
        response.setScaleType(task.getScaleType());
        response.setPeriodStart(day);
        response.setPeriodEnd(day);
        response.setTask(task);
        response.setAnswers(new LinkedHashMap<>(Map.of("dor", 3)));
        responseRepository.save(response);
    }

    private String formatDate(LocalDate date) {
        return String.format("%02d/%02d/%d", date.getDayOfMonth(), date.getMonthValue(), date.getYear());
    }

    private ScaleTask saveTask(ScaleType scaleType, LocalDate deadline) {
        ScaleTask task = new ScaleTask();
        task.setPatient(patient);
        task.setPrescriber(prescriber);
        task.setScaleType(scaleType);
        task.setPeriodStart(TODAY.minusDays(4));
        task.setPeriodEnd(deadline);
        task.setStatus(ScaleTaskStatus.PENDENTE);
        return taskRepository.save(task);
    }
}
