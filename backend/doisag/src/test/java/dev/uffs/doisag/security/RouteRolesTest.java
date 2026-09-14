package dev.uffs.doisag.security;

import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.model.Admin;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.PrescriberAvailability;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberAvailabilityRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// controle de acesso por perfil (RF29)
// as varreduras passam por todas as rotas da api
// entao uma rota nova sem regra de perfil quebra o teste na hora
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RouteRolesTest {

    // rotas abertas de proposito e o motivo de cada uma esta no SecurityConfigurations
    private static final Set<String> PUBLIC_ROUTES = Set.of(
            "POST /auth/login",
            "POST /auth/logout",
            "POST /auth/register",
            "POST /auth/password-reset/request",
            "POST /auth/password-reset/confirm",
            "GET /invites/{token}",
            "GET /consent-term",
            "GET /health"
    );

    // consulta n pode ser marcada no passado entao a data anda junto com o calendario
    private static final LocalDate NEXT_MONTH = LocalDate.now().plusMonths(1);

    @Autowired private MockMvc mockMvc;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlerMapping;
    @Autowired private UsersRepository usersRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private PrescriberAvailabilityRepository availabilityRepository;
    @Autowired private TokenService tokenService;

    private Prescriber prescriber;
    private Patient patient;
    private Appointment appointment;

    @BeforeEach
    void createAccounts() {
        prescriber = savePrescriber("perfil-prescritor@email.com");
        patient = savePatient("perfil-paciente@email.com", prescriber);

        appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setPrescriber(prescriber);
        appointment.setDateTime(NEXT_MONTH.atTime(9, 0));
        appointment.setModality(AppointmentModality.PRESENCIAL);
        appointment.setStatus(AppointmentStatus.AGENDADA);
        appointment.setDurationMinutes(60);
        appointment = appointmentRepository.save(appointment);
    }

    @Test
    void everyRouteDeclaresTheRolesThatCanUseIt() {
        Map<String, HandlerMethod> routes = appRoutes();
        List<String> routesWithoutRoles = new ArrayList<>();

        for (Map.Entry<String, HandlerMethod> route : routes.entrySet()) {
            if (PUBLIC_ROUTES.contains(route.getKey())) {
                continue;
            }
            String rule = roleRuleOf(route.getValue());
            if (rule == null || !(rule.contains("hasRole(") || rule.contains("hasAnyRole("))) {
                routesWithoutRoles.add(route.getKey());
            }
        }

        assertThat(routesWithoutRoles).isEmpty();
        // se uma rota publica mudar de caminho a lista la de cima precisa mudar junto
        assertThat(routes.keySet()).containsAll(PUBLIC_ROUTES);
    }

    @Test
    void everyPrivateRouteAnswers401WithoutSession() throws Exception {
        for (String route : appRoutes().keySet()) {
            if (PUBLIC_ROUTES.contains(route)) {
                continue;
            }
            int status = mockMvc.perform(requestFor(route)).andReturn().getResponse().getStatus();
            assertThat(status).as(route).isEqualTo(401);
        }
    }

    // o administrador cuida das contas e n chega em dado clinico nem por engano
    @Test
    void adminOnlyReachesAdministrationAndTheirOwnAccount() throws Exception {
        Admin admin = new Admin();
        admin.setName("Administrador do teste de perfil");
        admin.setEmail("perfil-admin@email.com");
        admin.setPassword("hash");
        String adminToken = bearerTokenOf(usersRepository.save(admin));

        for (String route : appRoutes().keySet()) {
            String path = route.substring(route.indexOf(' ') + 1);
            if (PUBLIC_ROUTES.contains(route) || isAdministrationOrOwnAccount(path)) {
                continue;
            }
            int status = mockMvc.perform(requestFor(route).header("Authorization", adminToken))
                    .andReturn().getResponse().getStatus();
            assertThat(status).as(route).isEqualTo(403);
        }
    }

    @Test
    void patientCannotUseClinicalManagementRoutes() throws Exception {
        String patientToken = bearerTokenOf(patient);

        mockMvc.perform(get("/paciente").header("Authorization", patientToken))
                .andExpect(status().isForbidden());

        // registrar e alterar consulta mesmo sendo a propria
        String appointmentBody = "{\"patientId\":" + patient.getId() + ",\"dateTime\":\"" + NEXT_MONTH
                + "T15:00:00\",\"modality\":\"PRESENCIAL\"}";
        mockMvc.perform(post("/consulta").header("Authorization", patientToken)
                        .contentType(MediaType.APPLICATION_JSON).content(appointmentBody))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/consulta/" + appointment.getId()).header("Authorization", patientToken)
                        .contentType(MediaType.APPLICATION_JSON).content(appointmentBody))
                .andExpect(status().isForbidden());

        // registrar o atendimento clinico
        mockMvc.perform(post("/pacientes/" + patient.getId() + "/consultas").header("Authorization", patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modality\":\"PRESENCIAL\",\"diagnosis\":\"escrito pelo paciente\"}"))
                .andExpect(status().isForbidden());

        // emitir prescricao e aplicar o meem
        mockMvc.perform(post("/consulta/" + appointment.getId() + "/prescricao").header("Authorization", patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productDescription\":\"Oleo de CBD\",\"spectrum\":\"FULL_SPECTRUM\",\"components\":[{\"cannabinoid\":\"CBD\",\"concentration\":3,\"unit\":\"PERCENTUAL\"}],\"posology\":\"2 gotas a noite\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/escalas/mini-exame/consulta/" + appointment.getId())
                        .header("Authorization", patientToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":{\"registro\":3}}"))
                .andExpect(status().isForbidden());

        // designar escala e montar o acompanhamento de 90 dias
        mockMvc.perform(post("/pacientes/" + patient.getId() + "/escalas").header("Authorization", patientToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"scaleType\":\"ESCALA_HAMILTON\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/pacientes/" + patient.getId() + "/acompanhamento").header("Authorization", patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"scaleType\":\"ESCALA_HAMILTON\",\"periodicity\":\"SEMANAL\"}]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void prescriberCannotUseAdministrationRoutes() throws Exception {
        String prescriberToken = bearerTokenOf(prescriber);

        mockMvc.perform(get("/admin/prescribers").header("Authorization", prescriberToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/prescribers").header("Authorization", prescriberToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/admin/prescribers/" + prescriber.getId() + "/active")
                        .header("Authorization", prescriberToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isForbidden());
    }

    // a api antiga devolvia anamneses escalas e prescricoes do sistema inteiro
    @Test
    void noRouteListsRecordsOfTheWholeSystem() throws Exception {
        String prescriberToken = bearerTokenOf(prescriber);
        List<String> oldListRoutes = List.of("/anamnese", "/acompanhamento", "/escala-hamilton",
                "/escala-pittsburgh", "/registro-dor", "/registro-sono", "/registro-tea", "/mini-exame",
                "/prescricao", "/paciente/prescritor/" + prescriber.getId());

        for (String url : oldListRoutes) {
            int status = mockMvc.perform(get(url).header("Authorization", prescriberToken))
                    .andReturn().getResponse().getStatus();
            assertThat(status).as(url).isIn(404, 405);
        }
    }

    @Test
    void prescriberListsOnlyTheirOwnPatients() throws Exception {
        Prescriber otherPrescriber = savePrescriber("perfil-outro-prescritor@email.com");
        savePatient("perfil-paciente-de-outro@email.com", otherPrescriber);

        mockMvc.perform(get("/paciente").header("Authorization", bearerTokenOf(prescriber)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("perfil-paciente@email.com"));
    }

    // o paciente escolhe o horario mas diagnostico e conduta sao do prescritor
    @Test
    void patientSchedulingNeverWritesClinicalFields() throws Exception {
        String body = "{\"dateTime\":\"" + NEXT_MONTH + "T14:00:00\",\"modality\":\"REMOTA\","
                + "\"diagnosis\":\"escrito pelo paciente\",\"therapeuticPlan\":\"escrito pelo paciente\"}";

        // o paciente so pede horario livre entao a agenda do prescritor precisa ter o periodo aberto
        PrescriberAvailability period = new PrescriberAvailability();
        period.setPrescriber(prescriber);
        period.setDayOfWeek(NEXT_MONTH.getDayOfWeek().getValue());
        period.setStartTime(LocalTime.of(8, 0));
        period.setEndTime(LocalTime.of(18, 0));
        availabilityRepository.save(period);

        mockMvc.perform(post("/consulta/agendamento").header("Authorization", bearerTokenOf(patient))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SOLICITADA"))
                .andExpect(jsonPath("$.diagnosis").doesNotExist())
                .andExpect(jsonPath("$.therapeuticPlan").doesNotExist());
    }

    // cada rota da api escrita como METODO e caminho
    private Map<String, HandlerMethod> appRoutes() {
        Map<String, HandlerMethod> routes = new TreeMap<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> mapping : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod handlerMethod = mapping.getValue();
            if (!handlerMethod.getBeanType().getPackageName().startsWith("dev.uffs.doisag")) {
                continue;
            }
            for (RequestMethod method : mapping.getKey().getMethodsCondition().getMethods()) {
                for (String path : mapping.getKey().getPatternValues()) {
                    routes.put(method.name() + " " + path, handlerMethod);
                }
            }
        }
        return routes;
    }

    // a regra do metodo vale mais q a da classe
    private String roleRuleOf(HandlerMethod handlerMethod) {
        PreAuthorize methodRule = handlerMethod.getMethodAnnotation(PreAuthorize.class);
        if (methodRule != null) {
            return methodRule.value();
        }
        PreAuthorize classRule = handlerMethod.getBeanType().getAnnotation(PreAuthorize.class);
        return classRule == null ? null : classRule.value();
    }

    // troca cada variavel do caminho por 1
    private MockHttpServletRequestBuilder requestFor(String route) {
        String method = route.substring(0, route.indexOf(' '));
        String path = route.substring(route.indexOf(' ') + 1).replaceAll("\\{[^}]+}", "1");
        return request(HttpMethod.valueOf(method), path);
    }

    private boolean isAdministrationOrOwnAccount(String path) {
        return path.startsWith("/admin/") || path.equals("/profile") || path.startsWith("/profile/")
                || path.equals("/auth/me");
    }

    private String bearerTokenOf(Users user) {
        return "Bearer " + tokenService.generateToken(user);
    }

    private Prescriber savePrescriber(String email) {
        Prescriber newPrescriber = new Prescriber();
        newPrescriber.setName("Prescritor " + email);
        newPrescriber.setEmail(email);
        newPrescriber.setPassword("hash");
        return prescriberRepository.save(newPrescriber);
    }

    private Patient savePatient(String email, Prescriber linkedPrescriber) {
        Patient newPatient = new Patient();
        newPatient.setName("Paciente " + email);
        newPatient.setEmail(email);
        newPatient.setPassword("hash");
        newPatient.setBirthDate(LocalDate.of(1990, 1, 1));
        newPatient.setPrescriber(linkedPrescriber);
        return patientRepository.save(newPatient);
    }
}
