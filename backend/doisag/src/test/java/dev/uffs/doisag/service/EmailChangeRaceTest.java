package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.AddressDTO;
import dev.uffs.doisag.dto.AgendaAppointmentDTO;
import dev.uffs.doisag.dto.AnamnesisDTO;
import dev.uffs.doisag.dto.AnamnesisResponseDTO;
import dev.uffs.doisag.dto.AnnulmentDTO;
import dev.uffs.doisag.dto.ChangePasswordDTO;
import dev.uffs.doisag.dto.EmailChangeDTO;
import dev.uffs.doisag.dto.GuardianDTO;
import dev.uffs.doisag.dto.PasswordResetDTO;
import dev.uffs.doisag.dto.PatientResponseDTO;
import dev.uffs.doisag.dto.ProfileDTO;
import dev.uffs.doisag.dto.ProfileUpdateDTO;
import dev.uffs.doisag.dto.ScaleResponseCreateDTO;
import dev.uffs.doisag.email.EmailMessage;
import dev.uffs.doisag.email.EmailSender;
import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.infra.InvalidFieldException;
import dev.uffs.doisag.model.Address;
import dev.uffs.doisag.model.Anamnesis;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.AnamnesisRepository;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.ConsentAcceptanceRepository;
import dev.uffs.doisag.repository.EmailChangeRepository;
import dev.uffs.doisag.repository.PasswordResetRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.ScaleResponseRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.security.LoginAttemptLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// a troca do e-mail de acesso contra o q pode acontecer na mesma conta no mesmo instante (issue 130)
//
// as travas por conta seguem uma ordem so (paciente, dps password_reset, dps users, dps email_change):
// quem chega primeiro ganha e o outro espera e le o resultado, em vez de os dois esperarem um ao outro
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailChangeRaceTest {

    private static final String PASSWORD = "Senha@123";
    private static final String NEW_PASSWORD = "Senha-Nova#2026";
    private static final String ADDRESS = "127.0.0.1";
    private static final String OLD_EMAIL = "corrida-email-paciente@email.com";
    private static final String FIRST_EMAIL = "primeiro-corrida@email.com";
    private static final String SECOND_EMAIL = "segundo-corrida@email.com";
    private static final Pattern TOKEN_IN_LINK = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired private MockMvc mockMvc;
    @Autowired private EmailChangeService emailChangeService;
    @Autowired private PasswordResetService passwordResetService;
    @Autowired private PasswordService passwordService;
    @Autowired private ScaleResponseService scaleResponseService;
    @Autowired private ProfileService profileService;
    @Autowired private ConsentTermService consentTermService;
    @Autowired private PatientAccountService patientAccountService;
    @Autowired private PatientArchiveService patientArchiveService;
    @Autowired private AppointmentService appointmentService;
    @Autowired private AnamnesisService anamnesisService;
    @Autowired private EmailChangeRepository emailChangeRepository;
    @Autowired private PasswordResetRepository passwordResetRepository;
    @Autowired private UsersRepository usersRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private ScaleResponseRepository scaleResponseRepository;
    @Autowired private ConsentAcceptanceRepository consentAcceptanceRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private AnamnesisRepository anamnesisRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private LoginAttemptLimiter loginAttemptLimiter;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmailSender emailSender;

    private HeldLock heldLock;
    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createPatient() {
        heldLock = new HeldLock(transactionTemplate);
        loginAttemptLimiter.clear();

        prescriber = new Prescriber();
        prescriber.setName("Prescritora da corrida de e-mail");
        prescriber.setEmail("corrida-email-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente da corrida de e-mail");
        patient.setEmail(OLD_EMAIL);
        patient.setPassword(passwordEncoder.encode(PASSWORD));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    // sem a transacao do teste o q foi gravado fica no banco, entao sai na mao
    @AfterEach
    void removeWhatWasCreated() throws InterruptedException {
        heldLock.close();
        jdbcTemplate.update("delete from email_change where user_id = ?", patient.getId());
        jdbcTemplate.update("delete from audit_event where actor_id = ? or patient_id = ?", patient.getId(), patient.getId());
        jdbcTemplate.update("delete from password_reset where user_id = ?", patient.getId());
        jdbcTemplate.update("delete from consent_acceptance where user_id = ?", patient.getId());
        jdbcTemplate.update("delete from notification where user_id in (?, ?)", prescriber.getId(), patient.getId());
        jdbcTemplate.update("delete from scale_response where patient_id = ?", patient.getId());
        jdbcTemplate.update("delete from scale_task where patient_id = ?", patient.getId());
        jdbcTemplate.update("delete from appointment where patient_id = ?", patient.getId());
        jdbcTemplate.update("delete from anamnesis where patient_id = ?", patient.getId());
        patientRepository.deleteById(patient.getId());
        prescriberRepository.deleteById(prescriber.getId());
    }

    private String tokenFromLastLinkTo(String address) {
        ArgumentCaptor<EmailMessage> emailCaptor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, atLeastOnce()).send(emailCaptor.capture());
        List<EmailMessage> emails = emailCaptor.getAllValues();
        for (int index = emails.size() - 1; index >= 0; index--) {
            Matcher tokenMatcher = TOKEN_IN_LINK.matcher(emails.get(index).text());
            if (emails.get(index).to().equals(address) && tokenMatcher.find()) {
                return tokenMatcher.group(1);
            }
        }
        throw new AssertionError("nenhum link foi pra " + address);
    }

    private MvcResult confirm(String token) throws Exception {
        return mockMvc.perform(post("/auth/email-change/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andReturn();
    }

    private void assertRefusedLink(HeldLock.Outcome<MvcResult> outcome) throws Exception {
        assertThat(outcome.error()).isNull();
        assertThat(outcome.value().getResponse().getStatus()).isEqualTo(400);
        assertThat(outcome.value().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains(EmailChangeService.INVALID_LINK_MESSAGE);
    }

    private Patient savedPatient() {
        return patientRepository.findById(patient.getId()).orElseThrow();
    }

    // a escala respondida pelo paciente logado: grava a trilha com ele como autor
    private void answerScaleAsThePatient() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(patient, null, patient.getAuthorities()));
        try {
            scaleResponseService.answer(patient.getId(), ScaleType.ACOMPANHAMENTO_SEMANAL,
                    new ScaleResponseCreateDTO(null, null, Map.of("dor", 5, "sono", 6)));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // o fluxo roda com o paciente logado, entao a trilha grava ele como autor
    private <T> T asThePatient(Callable<T> flow) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(patient, null, patient.getAuthorities()));
        try {
            return flow.call();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // pedido de consulta ainda sem resposta, q o paciente cancela a qualquer hora
    private Long saveRequest() {
        Appointment request = new Appointment();
        request.setPatient(patient);
        request.setPrescriber(prescriber);
        request.setDateTime(LocalDateTime.of(LocalDate.now().plusDays(7), LocalTime.of(10, 0)));
        request.setModality(AppointmentModality.PRESENCIAL);
        request.setDurationMinutes(60);
        request.setStatus(AppointmentStatus.SOLICITADA);
        return appointmentRepository.save(request).getId();
    }

    private Long saveAnamnesis() {
        Anamnesis anamnesis = new Anamnesis();
        anamnesis.setPatient(patient);
        anamnesis.setAssessmentDate(LocalDate.now());
        anamnesis.setReasonForVisit("Dor lombar");
        return anamnesisRepository.save(anamnesis).getId();
    }

    // a pessoa pede outro endereco enquanto o link do primeiro esta sendo aberto
    // o pedido comeca travando a conta, entao a transacao de fora segura essa mesma trava
    @Test
    void theConfirmationWaitsForTheNewRequestAndFindsTheOldLinkCancelled() throws Exception {
        emailChangeService.request(patient.getId(), new EmailChangeDTO(FIRST_EMAIL, PASSWORD), ADDRESS);
        String firstToken = tokenFromLastLinkTo(FIRST_EMAIL);

        HeldLock.Outcome<MvcResult> outcome = heldLock.run(
                () -> usersRepository.lockById(patient.getId()),
                () -> emailChangeService.request(patient.getId(), new EmailChangeDTO(SECOND_EMAIL, PASSWORD), ADDRESS),
                () -> confirm(firstToken));

        assertRefusedLink(outcome);
        // o e-mail n mudou e o pedido q vale eh o novo
        assertThat(savedPatient().getEmail()).isEqualTo(OLD_EMAIL);
        assertThat(emailChangeRepository.findPendingEmails(patient.getId(), LocalDateTime.now()))
                .containsExactly(SECOND_EMAIL);
    }

    // a dona da conta recebeu o aviso e usa o link de senha nova enquanto o outro endereco abre o link
    // de confirmacao: os dois travam na mesma ordem, entao a confirmacao espera e encontra o pedido
    // cancelado pela troca de senha, em vez de os dois esperarem um ao outro
    @Test
    void theConfirmationWaitsForTheResetLinkAndIsRefusedWhenThePasswordGotChanged() throws Exception {
        emailChangeService.request(patient.getId(), new EmailChangeDTO(FIRST_EMAIL, PASSWORD), ADDRESS);
        String changeToken = tokenFromLastLinkTo(FIRST_EMAIL);
        passwordResetService.requestReset(OLD_EMAIL, ADDRESS);
        String resetToken = tokenFromLastLinkTo(OLD_EMAIL);

        HeldLock.Outcome<MvcResult> outcome = heldLock.run(
                () -> passwordResetRepository.findPendingForUpdate(patient.getId()),
                () -> passwordResetService.resetPassword(new PasswordResetDTO(resetToken, NEW_PASSWORD)),
                () -> confirm(changeToken));

        assertRefusedLink(outcome);
        Patient saved = savedPatient();
        assertThat(saved.getEmail()).isEqualTo(OLD_EMAIL);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, saved.getPassword())).isTrue();
        assertThat(emailChangeRepository.findPendingEmails(patient.getId(), LocalDateTime.now())).isEmpty();
    }

    // quem tem a senha antiga pede o e-mail novo no mesmo instante em q a dona troca a senha:
    // o pedido espera a conta e confere a senha nova, entao eh recusado e nada fica pendente
    @Test
    void theRequestWaitsForThePasswordChangeAndIsRefusedWithTheOldPassword() throws Exception {
        HeldLock.Outcome<ProfileDTO> outcome = heldLock.run(
                () -> usersRepository.lockById(patient.getId()),
                () -> passwordService.changePassword(patient.getId(), new ChangePasswordDTO(PASSWORD, NEW_PASSWORD), ADDRESS),
                () -> emailChangeService.request(patient.getId(), new EmailChangeDTO(FIRST_EMAIL, PASSWORD), ADDRESS));

        assertThat(outcome.error()).isInstanceOf(InvalidFieldException.class);
        assertThat(emailChangeRepository.findPendingEmails(patient.getId(), LocalDateTime.now())).isEmpty();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, savedPatient().getPassword())).isTrue();
    }

    // a escala trava o paciente e dps grava a trilha com ele como autor, q no postgres segura a conta
    // em modo compartilhado. a confirmacao grava a trilha apontando pro paciente: se ela travasse a conta
    // antes, ficava esperando o paciente enquanto a escala esperava a conta. ela trava o paciente primeiro,
    // espera a escala terminar e dps troca o e-mail. no h2 a chave estrangeira n trava nada, entao la
    // o teste so pega a confirmacao q n espera o paciente
    @Test
    void thePatientConfirmationWaitsForTheScaleAnswerInsteadOfLockingTheAccountFirst() throws Exception {
        emailChangeService.request(patient.getId(), new EmailChangeDTO(FIRST_EMAIL, PASSWORD), ADDRESS);
        String token = tokenFromLastLinkTo(FIRST_EMAIL);

        HeldLock.Outcome<MvcResult> outcome = heldLock.run(
                () -> patientRepository.lockById(patient.getId()),
                this::answerScaleAsThePatient,
                () -> confirm(token));

        assertThat(outcome.error()).isNull();
        assertThat(outcome.value().getResponse().getStatus()).isEqualTo(204);
        assertThat(savedPatient().getEmail()).isEqualTo(FIRST_EMAIL);
        assertThat(scaleResponseRepository.findByPatientIdAndScaleTypeOrderByPeriodStartDesc(
                patient.getId(), ScaleType.ACOMPANHAMENTO_SEMANAL)).hasSize(1);
    }

    // o perfil do paciente grava na conta e no paciente. gravando a conta e dps esperando o paciente
    // travado pela confirmacao, q dps trava a conta, um esperava o outro. ele trava o paciente antes,
    // igual a escala e a confirmacao, espera ela terminar e grava so o q mudou nele
    @Test
    void thePatientProfileWaitsForTheConfirmationThatLockedThePatient() throws Exception {
        emailChangeService.request(patient.getId(), new EmailChangeDTO(FIRST_EMAIL, PASSWORD), ADDRESS);
        String token = tokenFromLastLinkTo(FIRST_EMAIL);
        ProfileUpdateDTO profileData = new ProfileUpdateDTO("Nome Novo da Corrida", "Nome Social da Corrida",
                LocalDate.of(1990, 1, 1), "49988887777", new AddressDTO("Rua B", "20", "Chapecó", "SC", null), null);

        HeldLock.Outcome<ProfileDTO> outcome = heldLock.run(
                () -> patientRepository.lockById(patient.getId()),
                () -> emailChangeService.confirm(token),
                () -> profileService.updateProfile(patient.getId(), profileData));

        assertThat(outcome.error()).isNull();
        Patient saved = savedPatient();
        assertThat(saved.getEmail()).isEqualTo(FIRST_EMAIL);
        assertThat(saved.getName()).isEqualTo("Nome Novo da Corrida");
        assertThat(saved.getSocialName()).isEqualTo("Nome Social da Corrida");
    }

    // o menor q aceita o termo com o responsavel grava o aceite e o responsavel, e os dois gravam
    // na conta antes do paciente. o aceite trava o paciente antes, entao espera a confirmacao em vez
    // de segurar a conta q ela vai travar
    @Test
    void theMinorTermAcceptanceWaitsForTheConfirmationThatLockedThePatient() throws Exception {
        patient.setBirthDate(LocalDate.now().minusYears(10));
        patient.setPhone("49999999999");
        patient.setAddress(new Address("Rua A", "10", "Chapecó", "SC", null));
        patient = patientRepository.save(patient);
        emailChangeService.request(patient.getId(), new EmailChangeDTO(FIRST_EMAIL, PASSWORD), ADDRESS);
        String token = tokenFromLastLinkTo(FIRST_EMAIL);

        HeldLock.Outcome<Object> outcome = heldLock.run(
                () -> patientRepository.lockById(patient.getId()),
                () -> emailChangeService.confirm(token),
                () -> {
                    consentTermService.acceptNewVersion(patient.getId(), ConsentTermService.CURRENT_VERSION,
                            new GuardianDTO("Joana Souza", "mãe", "49988776655"));
                    return null;
                });

        assertThat(outcome.error()).isNull();
        Patient saved = savedPatient();
        assertThat(saved.getEmail()).isEqualTo(FIRST_EMAIL);
        assertThat(saved.getGuardianRelationship()).isEqualTo("mãe");
        assertThat(consentAcceptanceRepository.findAllByUserIdOrderByAcceptedAtDesc(patient.getId()))
                .extracting("termVersion").containsExactly(ConsentTermService.CURRENT_VERSION);
    }

    // a administracao desativa a conta enquanto o link eh aberto: a desativacao grava a trilha, q aponta
    // pro paciente, e a conta no fim, entao trava o paciente antes, como a confirmacao, e espera ela
    @Test
    void theDeactivationWaitsForTheConfirmationThatLockedThePatient() throws Exception {
        emailChangeService.request(patient.getId(), new EmailChangeDTO(FIRST_EMAIL, PASSWORD), ADDRESS);
        String token = tokenFromLastLinkTo(FIRST_EMAIL);

        HeldLock.Outcome<Patient> outcome = heldLock.run(
                () -> patientRepository.lockById(patient.getId()),
                () -> emailChangeService.confirm(token),
                () -> patientAccountService.changeActive(patient.getId(), false));

        assertThat(outcome.error()).isNull();
        Patient saved = savedPatient();
        assertThat(saved.getEmail()).isEqualTo(FIRST_EMAIL);
        assertThat(saved.isActive()).isFalse();
    }

    // o prescritor arquiva o prontuario enquanto o link eh aberto: gravar o paciente grava a conta antes,
    // entao o arquivamento trava o paciente primeiro, como a confirmacao, e espera ela terminar
    @Test
    void theArchivingWaitsForTheConfirmationThatLockedThePatient() throws Exception {
        emailChangeService.request(patient.getId(), new EmailChangeDTO(FIRST_EMAIL, PASSWORD), ADDRESS);
        String token = tokenFromLastLinkTo(FIRST_EMAIL);

        HeldLock.Outcome<PatientResponseDTO> outcome = heldLock.run(
                () -> patientRepository.lockById(patient.getId()),
                () -> emailChangeService.confirm(token),
                () -> patientArchiveService.archive(patient.getId(), prescriber));

        assertThat(outcome.error()).isNull();
        Patient saved = savedPatient();
        assertThat(saved.getEmail()).isEqualTo(FIRST_EMAIL);
        assertThat(saved.isArchived()).isTrue();
    }

    // o paciente cancela um pedido enquanto o link eh aberto: o cancelamento grava a trilha com ele como
    // autor, q no postgres segura a conta em modo compartilhado, e a trilha aponta pro paciente. ele trava
    // a consulta e logo dps o paciente, entao espera a confirmacao antes de encostar na conta
    @Test
    void thePatientCancellationWaitsForTheConfirmationThatLockedThePatient() throws Exception {
        emailChangeService.request(patient.getId(), new EmailChangeDTO(FIRST_EMAIL, PASSWORD), ADDRESS);
        String token = tokenFromLastLinkTo(FIRST_EMAIL);
        Long requestId = saveRequest();

        HeldLock.Outcome<AgendaAppointmentDTO> outcome = heldLock.run(
                () -> patientRepository.lockById(patient.getId()),
                () -> emailChangeService.confirm(token),
                () -> asThePatient(() -> appointmentService.cancel(requestId, patient)));

        assertThat(outcome.error()).isNull();
        assertThat(savedPatient().getEmail()).isEqualTo(FIRST_EMAIL);
        assertThat(appointmentRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.CANCELADA);
    }

    // a correcao da anamnese tbm grava a trilha com o paciente como autor: ela trava o paciente
    // antes da anamnese e espera a confirmacao
    @Test
    void theAnamnesisCorrectionWaitsForTheConfirmationThatLockedThePatient() throws Exception {
        emailChangeService.request(patient.getId(), new EmailChangeDTO(FIRST_EMAIL, PASSWORD), ADDRESS);
        String token = tokenFromLastLinkTo(FIRST_EMAIL);
        Long anamnesisId = saveAnamnesis();
        AnamnesisDTO correction = new AnamnesisDTO(null, null, "Dor lombar e insônia", null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, "Sim", null);

        HeldLock.Outcome<AnamnesisResponseDTO> outcome = heldLock.run(
                () -> patientRepository.lockById(patient.getId()),
                () -> emailChangeService.confirm(token),
                () -> asThePatient(() -> anamnesisService.update(anamnesisId, correction)));

        assertThat(outcome.error()).isNull();
        assertThat(savedPatient().getEmail()).isEqualTo(FIRST_EMAIL);
        assertThat(anamnesisRepository.findById(anamnesisId).orElseThrow().getReasonForVisit())
                .isEqualTo("Dor lombar e insônia");
    }

    // a anulacao do prescritor trava na mesma ordem da correcao (paciente, dps anamnese), senao uma
    // segurava a anamnese esperando o paciente e a outra o paciente esperando a anamnese
    @Test
    void theAnamnesisAnnulmentWaitsForTheConfirmationThatLockedThePatient() throws Exception {
        emailChangeService.request(patient.getId(), new EmailChangeDTO(FIRST_EMAIL, PASSWORD), ADDRESS);
        String token = tokenFromLastLinkTo(FIRST_EMAIL);
        Long anamnesisId = saveAnamnesis();

        HeldLock.Outcome<AnamnesisResponseDTO> outcome = heldLock.run(
                () -> patientRepository.lockById(patient.getId()),
                () -> emailChangeService.confirm(token),
                () -> anamnesisService.annul(anamnesisId, new AnnulmentDTO("preenchida pela pessoa errada"),
                        prescriber));

        assertThat(outcome.error()).isNull();
        assertThat(savedPatient().getEmail()).isEqualTo(FIRST_EMAIL);
        assertThat(anamnesisRepository.findById(anamnesisId).orElseThrow().isAnnulled()).isTrue();
    }
}
