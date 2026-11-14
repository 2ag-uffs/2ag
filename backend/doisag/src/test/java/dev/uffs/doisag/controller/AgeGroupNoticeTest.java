package dev.uffs.doisag.controller;

import dev.uffs.doisag.enums.ConsentType;
import dev.uffs.doisag.infra.BrazilianFormat;
import dev.uffs.doisag.infra.PendingTermException;
import dev.uffs.doisag.model.Address;
import dev.uffs.doisag.model.Admin;
import dev.uffs.doisag.model.ConsentAcceptance;
import dev.uffs.doisag.model.Notification;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.ConsentAcceptanceRepository;
import dev.uffs.doisag.repository.NotificationRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.security.TokenService;
import dev.uffs.doisag.service.AgeGroupNoticeService;
import dev.uffs.doisag.service.ConsentTermService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// a faixa de idade sai da data de nascimento q o proprio paciente informa (revisao das regras R9)
// a troca da data vai pra trilha com a antiga e a nova, e a prescritora fica sabendo qnd o paciente
// passa de menor pra adulto ou volta, pela data trocada ou pelo aniversario de 18 anos
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AgeGroupNoticeTest {

    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDate ADULT_BIRTH_DATE = LocalDate.of(1990, 4, 12);

    @Autowired private MockMvc mockMvc;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private UsersRepository usersRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ConsentAcceptanceRepository consentAcceptanceRepository;
    @Autowired private AgeGroupNoticeService ageGroupNoticeService;
    @Autowired private TokenService tokenService;
    @Autowired private EntityManager entityManager;

    private Prescriber prescriber;
    private Patient patient;

    @BeforeEach
    void createAccounts() {
        prescriber = new Prescriber();
        prescriber.setName("Prescritora da Idade");
        prescriber.setEmail("idade-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriber = prescriberRepository.save(prescriber);

        patient = new Patient();
        patient.setName("Paciente da Idade");
        patient.setEmail("idade-paciente@email.com");
        patient.setPassword("hash");
        patient.setCpf("52998224725");
        patient.setBirthDate(ADULT_BIRTH_DATE);
        patient.setPhone("49999887766");
        patient.setAddress(new Address("Rua das Flores", "120", "Chapeco", "SC", "Brasil"));
        patient.setPrescriber(prescriber);
        patient = patientRepository.save(patient);
    }

    @Test
    void birthDateChangeGoesToTheTrailWithTheOldAndTheNewDate() throws Exception {
        LocalDate newBirthDate = LocalDate.of(1991, 5, 20);
        saveProfile(newBirthDate, null);

        mockMvc.perform(get("/patients/" + patient.getId() + "/audit-events")
                        .param("from", TODAY.toString()).param("to", TODAY.toString())
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[?(@.recordType == 'CONTA_DE_PACIENTE')].operation",
                        contains("ALTERACAO")))
                .andExpect(jsonPath("$.events[?(@.recordType == 'CONTA_DE_PACIENTE')].actorRole",
                        contains("PATIENT")))
                .andExpect(jsonPath("$.events[?(@.recordType == 'CONTA_DE_PACIENTE')].detail",
                        contains("Data de nascimento: 12/04/1990 → 20/05/1991")));
        // na msm faixa de idade a prescritora n recebe aviso
        assertThat(noticesOfThePrescriber()).isEmpty();
    }

    // salvar o perfil sem mexer na data n enche a trilha
    @Test
    void savingTheProfileWithTheSameBirthDateRecordsNothing() throws Exception {
        saveProfile(ADULT_BIRTH_DATE, null);

        mockMvc.perform(get("/patients/" + patient.getId() + "/audit-events")
                        .param("from", TODAY.toString()).param("to", TODAY.toString())
                        .header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events", hasSize(0)));
    }

    // a troca eh do proprio paciente, entao a trilha da administracao continua sem ela
    @Test
    void adminTrailDoesNotShowTheBirthDate() throws Exception {
        saveProfile(LocalDate.of(1991, 5, 20), null);
        Admin admin = new Admin();
        admin.setName("Administração da Idade");
        admin.setEmail("idade-admin@email.com");
        admin.setPassword("hash");
        Users savedAdmin = usersRepository.save(admin);

        String body = mockMvc.perform(get("/admin/audit-events")
                        .param("from", TODAY.toString()).param("to", TODAY.toString())
                        .header("Authorization", bearerTokenOf(savedAdmin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("Data de nascimento").doesNotContain("1991");
    }

    @Test
    void dateOfAMinorWarnsThePrescriber() throws Exception {
        LocalDate minorBirthDate = TODAY.minusYears(10);
        saveProfile(minorBirthDate, "Joana Souza");

        assertThat(noticesOfThePrescriber()).singleElement().satisfies(notice -> {
            assertThat(notice.getTitle()).isEqualTo(AgeGroupNoticeService.AGE_GROUP_TITLE);
            assertThat(notice.getMessage()).isEqualTo("Paciente da Idade trocou a data de nascimento no perfil de "
                    + "12/04/1990 para " + BrazilianFormat.date(minorBirthDate)
                    + " e passou de adulto para menor de 18 anos. Confira a idade na próxima consulta.");
            assertThat(notice.getLink()).isEqualTo(Notification.patientHistoryLink(patient.getId()));
        });
    }

    // a data corrigida pra de um adulto avisa e conta como o aviso dos 18 anos, senao o job mandava outro
    @Test
    void dateOfAnAdultWarnsThePrescriberOnce() throws Exception {
        makeMinor(TODAY.minusYears(10));
        LocalDate turnedEighteenThreeDaysAgo = TODAY.minusYears(18).minusDays(3);

        saveProfile(turnedEighteenThreeDaysAgo, "Joana Souza");
        ageGroupNoticeService.sendEighteenthBirthdayNotices(TODAY);

        assertThat(noticesOfThePrescriber()).singleElement().satisfies(notice -> {
            assertThat(notice.getTitle()).isEqualTo(AgeGroupNoticeService.AGE_GROUP_TITLE);
            assertThat(notice.getMessage()).contains("passou de menor de 18 anos para adulto");
        });
    }

    @Test
    void eighteenthBirthdayWarnsThePrescriberOnce() {
        makeMinor(TODAY.minusYears(18));

        assertThat(ageGroupNoticeService.sendEighteenthBirthdayNotices(TODAY)).isEqualTo(1);
        assertThat(ageGroupNoticeService.sendEighteenthBirthdayNotices(TODAY)).isZero();
        assertThat(ageGroupNoticeService.sendEighteenthBirthdayNotices(TODAY.plusDays(1))).isZero();

        assertThat(noticesOfThePrescriber()).singleElement().satisfies(notice -> {
            assertThat(notice.getTitle()).isEqualTo(AgeGroupNoticeService.BIRTHDAY_TITLE);
            assertThat(notice.getMessage()).startsWith("Paciente da Idade fez 18 anos");
            assertThat(notice.getLink()).isEqualTo(Notification.patientHistoryLink(patient.getId()));
        });
    }

    // a api q ficou fora do ar no dia do aniversario avisa qnd o job volta a rodar, dentro da semana
    @Test
    void birthdayMissedByTheJobIsStillWarnedWithinTheWeek() {
        makeMinor(TODAY.minusYears(18).minusDays(3));

        assertThat(ageGroupNoticeService.sendEighteenthBirthdayNotices(TODAY)).isEqualTo(1);
    }

    // quem ja era adulto ha tempo e quem ainda vai fazer 18 n recebe nada
    @Test
    void onlyWhoJustTurnedEighteenIsWarned() {
        Patient almostEighteen = new Patient();
        almostEighteen.setName("Quase Dezoito");
        almostEighteen.setEmail("idade-quase@email.com");
        almostEighteen.setPassword("hash");
        almostEighteen.setBirthDate(TODAY.minusYears(18).plusDays(1));
        almostEighteen.setPrescriber(prescriber);
        patientRepository.save(almostEighteen);

        assertThat(ageGroupNoticeService.sendEighteenthBirthdayNotices(TODAY)).isZero();
        assertThat(noticesOfThePrescriber()).isEmpty();
    }

    // no aniversario de 18 anos a data n mudou, entao o termo n oferece "Corrigir a data" e o perfil
    // fica travado ate o aceite de adulto, como na versao nova do termo
    @Test
    void termPendingFromTheBirthdayDoesNotLetTheDateBeChanged() throws Exception {
        makeMinor(TODAY.minusYears(18));
        acceptAsMinorAYearAgo();

        mockMvc.perform(get("/auth/me").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termPending").value(true))
                .andExpect(jsonPath("$.termPendingByAge").value(false));
        mockMvc.perform(put("/profile")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileJson(TODAY.minusYears(17), "Joana Souza")))
                .andExpect(status().isForbidden())
                .andExpect(header().string(PendingTermException.HEADER, "true"));
        assertThat(patientRepository.findById(patient.getId()).orElseThrow().getBirthDate())
                .isEqualTo(TODAY.minusYears(18));
    }

    // o ano digitado errado no perfil q joga o aniversario de 18 anos pra dps do aceite n eh aniversario:
    // a data mudou, entao o termo ainda oferece "Corrigir a data" e o perfil salva a data certa
    @Test
    void dateChangedAfterTheAcceptanceCanStillBeFixed() throws Exception {
        makeMinor(TODAY.minusYears(8));
        acceptAsMinorAYearAgo();
        saveProfile(TODAY.minusYears(18).minusMonths(1), "Joana Souza");

        mockMvc.perform(get("/auth/me").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termPending").value(true))
                .andExpect(jsonPath("$.termPendingByAge").value(true));
        saveProfile(TODAY.minusYears(8), "Joana Souza");
        mockMvc.perform(get("/auth/me").header("Authorization", bearerTokenOf(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termPending").value(false));
    }

    private void acceptAsMinorAYearAgo() {
        ConsentAcceptance acceptance = new ConsentAcceptance();
        acceptance.setUser(patient);
        acceptance.setTermType(ConsentType.REGISTRATION);
        acceptance.setTermVersion(ConsentTermService.CURRENT_VERSION);
        acceptance.setAcceptedAt(TODAY.minusYears(1).atTime(10, 0));
        acceptance.setGuardianName("Joana Souza");
        acceptance.setGuardianRelationship("mãe");
        consentAcceptanceRepository.save(acceptance);
    }

    private void makeMinor(LocalDate birthDate) {
        patient.setBirthDate(birthDate);
        patient.setGuardianName("Joana Souza");
        patient.setGuardianRelationship("mãe");
        patientRepository.save(patient);
    }

    private void saveProfile(LocalDate birthDate, String guardianName) throws Exception {
        mockMvc.perform(put("/profile")
                        .header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileJson(birthDate, guardianName)))
                .andExpect(status().isOk());
        entityManager.flush();
    }

    private String profileJson(LocalDate birthDate, String guardianName) {
        String guardian = guardianName == null ? ""
                : ",\"guardian\":{\"name\":\"" + guardianName + "\",\"relationship\":\"mãe\"}";
        return "{\"name\":\"Paciente da Idade\",\"birthDate\":\"" + birthDate + "\",\"phone\":\"49999887766\","
                + "\"address\":{\"street\":\"Rua das Flores\",\"number\":\"120\",\"city\":\"Chapeco\",\"state\":\"SC\"}"
                + guardian + "}";
    }

    private List<Notification> noticesOfThePrescriber() {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(prescriber.getId());
    }

    private String bearerTokenOf(Users user) {
        return "Bearer " + tokenService.generateToken(user);
    }
}
