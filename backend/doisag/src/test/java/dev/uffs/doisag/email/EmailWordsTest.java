package dev.uffs.doisag.email;

import dev.uffs.doisag.dto.EmailChangeDTO;
import dev.uffs.doisag.dto.PasswordResetDTO;
import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.ScaleResponse;
import dev.uffs.doisag.model.ScaleTask;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.ScaleResponseRepository;
import dev.uffs.doisag.repository.ScaleTaskRepository;
import dev.uffs.doisag.security.LoginAttemptLimiter;
import dev.uffs.doisag.service.EmailChangeService;
import dev.uffs.doisag.service.PasswordResetService;
import dev.uffs.doisag.service.ReminderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

// nenhum e-mail leva palavra do tratamento nem o numero do paciente (20/11/2026)
//
// o e-mail passa por servidores de fora e aparece na tela bloqueada do celular, entao o assunto e o
// texto n dizem escala, sintoma nem produto, e o link n leva o numero de ninguem
//
// o teste faz os servicos montarem cada e-mail q o sistema manda e procura uma lista curta de palavras
// inteiras. eh um alarme e n uma prova: palavra fora da lista passa, o nome q a clinica poe no remetente
// n passa por aqui, e quem criar um e-mail novo precisa fazer ele sair aqui tbm
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmailWordsTest {

    private static final LocalDate TODAY = LocalDate.now();
    private static final String ADDRESS = "127.0.0.1";
    private static final String PASSWORD = "Senha@123";
    private static final String NEW_EMAIL = "palavras-paciente-novo@email.com";
    private static final Pattern TOKEN_IN_LINK = Pattern.compile("token=([A-Za-z0-9_-]+)");

    // palavra inteira: "prescritor" e "administrador" n contam como "dor"
    private static final List<String> CLINICAL_WORDS = List.of(
            "ansiedade", "sono", "dor", "cannabis", "canabidiol",
            "Hamilton", "Pittsburgh", "MEEM", "Mini-Exame", "TEA");

    // os e-mails q existiam qnd o teste foi escrito. um e-mail novo entra nesta lista e no sendEveryEmail
    private static final List<String> KNOWN_SUBJECTS = List.of(
            "Pedido de troca do e-mail de acesso no 2AG",
            "Confirmar o e-mail novo no 2AG",
            "O e-mail de acesso da sua conta no 2AG mudou",
            "Criar uma senha nova no 2AG",
            "Criar a sua senha no 2AG",
            "Sua conta no 2AG foi criada",
            "Sua consulta no 2AG é amanhã",
            "Sua consulta no 2AG foi remarcada",
            "Sua consulta no 2AG foi cancelada",
            "Você tem formulário para responder no 2AG",
            "Você tem dias em branco no 2AG");

    @Autowired private EmailChangeService emailChangeService;
    @Autowired private PasswordResetService passwordResetService;
    @Autowired private ReminderService reminderService;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private ScaleTaskRepository taskRepository;
    @Autowired private ScaleResponseRepository responseRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private LoginAttemptLimiter loginAttemptLimiter;

    @MockitoBean private EmailSender emailSender;

    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createPrescriberAndPatient() {
        // a contagem de pedidos fica em memoria e passaria de um teste pro outro
        loginAttemptLimiter.clear();

        // sem senha criada ainda, pra receber os e-mails de primeira senha
        prescriber = new Prescriber();
        prescriber.setName("Dra. Remetente");
        prescriber.setEmail("palavras-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente dos E-mails");
        patient.setEmail("palavras-paciente@email.com");
        patient.setPassword(passwordEncoder.encode(PASSWORD));
        patient.setBirthDate(LocalDate.of(1990, 1, 1));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    @Test
    void noEmailCarriesAClinicalWord() {
        List<EmailMessage> emails = sendEveryEmail();

        assertThat(emails).extracting(EmailMessage::subject).containsAll(KNOWN_SUBJECTS);
        for (EmailMessage email : emails) {
            assertThat(clinicalWordIn(email.subject())).as("assunto: " + email.subject()).isNull();
            assertThat(clinicalWordIn(email.text())).as("texto do e-mail: " + email.subject()).isNull();
        }
    }

    // o link do e-mail vai pra um endereco sem numero, e os avisos de dentro do sistema continuam com o deles
    @Test
    void noEmailLinkCarriesThePatientNumber() {
        for (EmailMessage email : sendEveryEmail()) {
            assertThat(email.text()).as("texto do e-mail: " + email.subject())
                    .doesNotContain("/pacientes/").doesNotContain("/paciente/");
        }
    }

    @Test
    void theCheckLooksAtWholeWordsOnly() {
        assertThat(clinicalWordIn("Fale com o prescritor ou com o administrador pelo computador.")).isNull();
        assertThat(clinicalWordIn("Durma bem e adormeça cedo, sem chateação.")).isNull();
        assertThat(clinicalWordIn("Como está a sua dor hoje?")).isEqualTo("dor");
        assertThat(clinicalWordIn("DOR: responda até sexta")).isEqualTo("dor");
        assertThat(clinicalWordIn("Seu diário do Sono está esperando")).isEqualTo("sono");
        assertThat(clinicalWordIn("Responda o Acompanhamento Semanal")).isEqualTo("Acompanhamento semanal");
    }

    // a primeira palavra da lista, ou nome de escala, q aparece no texto. nulo qnd n tem nenhuma
    private static String clinicalWordIn(String text) {
        List<String> words = new ArrayList<>(CLINICAL_WORDS);
        for (ScaleType scaleType : ScaleType.values()) {
            words.add(scaleType.getDisplayName());
        }
        for (String word : words) {
            Pattern wholeWord = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(word) + "(?![\\p{L}\\p{N}])",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            if (wholeWord.matcher(text).find()) {
                return word;
            }
        }
        return null;
    }

    // passa por todo lugar q monta e-mail: troca de e-mail, senha e lembretes
    private List<EmailMessage> sendEveryEmail() {
        emailChangeService.request(patient.getId(), new EmailChangeDTO(NEW_EMAIL, PASSWORD), ADDRESS);
        emailChangeService.confirm(tokenOf("Confirmar o e-mail novo no 2AG"));

        // o pedido do prescritor sem senha, o link gerado pela administracao e o da conta recem-criada
        passwordResetService.requestReset(prescriber.getEmail(), ADDRESS);
        passwordResetService.createLinkFor(prescriber);
        passwordResetService.createFirstAccessLinkFor(prescriber);
        // a senha nova gravada pelo link tbm manda o e-mail q existir pra isso
        passwordResetService.requestReset(NEW_EMAIL, ADDRESS);
        passwordResetService.resetPassword(
                new PasswordResetDTO(tokenOf("Criar uma senha nova no 2AG"), "Senha-Nova#2026"));

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setPrescriber(prescriber);
        appointment.setDateTime(TODAY.plusDays(1).atTime(9, 0));
        appointment.setModality(AppointmentModality.PRESENCIAL);
        appointment.setStatus(AppointmentStatus.AGENDADA);
        appointment.setDurationMinutes(60);
        Long appointmentId = appointmentRepository.save(appointment).getId();
        reminderService.sendAppointmentReminders(TODAY);
        // a consulta q o lembrete citou por e-mail, remarcada pra outro dia e dps cancelada
        appointment = appointmentRepository.findById(appointmentId).orElseThrow();
        appointment.setDateTime(TODAY.plusDays(3).atTime(9, 0));
        reminderService.emailRescheduleIfEmailed(appointment, ", agora remota");
        reminderService.emailCancellationIfEmailed(appointment);

        saveTask(ScaleType.ESCALA_HAMILTON);
        reminderService.sendScaleReminders(TODAY);

        // o diario com dois dias preenchidos e ontem e anteontem em branco
        ScaleTask diary = saveTask(ScaleType.ACOMPANHAMENTO_SEMANAL);
        saveDay(diary, TODAY.minusDays(4));
        saveDay(diary, TODAY.minusDays(3));
        reminderService.sendBlankDaysReminders(TODAY);

        return sentEmails();
    }

    private List<EmailMessage> sentEmails() {
        ArgumentCaptor<EmailMessage> emailCaptor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, atLeastOnce()).send(emailCaptor.capture());
        return emailCaptor.getAllValues();
    }

    // o codigo do link no ultimo e-mail com esse assunto
    private String tokenOf(String subject) {
        String token = null;
        for (EmailMessage email : sentEmails()) {
            Matcher tokenMatcher = TOKEN_IN_LINK.matcher(email.text());
            if (email.subject().equals(subject) && tokenMatcher.find()) {
                token = tokenMatcher.group(1);
            }
        }
        assertThat(token).as("link do e-mail " + subject).isNotNull();
        return token;
    }

    private ScaleTask saveTask(ScaleType scaleType) {
        ScaleTask task = new ScaleTask();
        task.setPatient(patient);
        task.setPrescriber(prescriber);
        task.setScaleType(scaleType);
        task.setPeriodStart(TODAY.minusDays(4));
        task.setPeriodEnd(TODAY.plusDays(2));
        task.setStatus(ScaleTaskStatus.PENDENTE);
        return taskRepository.save(task);
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
}
