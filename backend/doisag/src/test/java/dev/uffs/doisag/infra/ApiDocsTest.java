package dev.uffs.doisag.infra;

import com.jayway.jsonpath.JsonPath;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.security.TokenService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ClassUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// a documentacao da api gerada do codigo (issue 40)
// so roda com o perfil docs do maven (./mvnw test -Pdocs), q eh quem traz o springdoc
@SpringBootTest(properties = "API_DOCS=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiDocsTest {

    // os textos de quem pode usar sao montados so com essas palavras
    private static final String WHO_CAN_USE =
            "sem login|(paciente|prescritor|administração)( ou (paciente|prescritor|administração))*"
                    + "(, só (do próprio paciente|a própria conta))?";

    @Autowired private MockMvc mockMvc;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private TokenService tokenService;

    private String prescriberToken;

    // antes do contexto subir: sem o perfil a classe inteira eh pulada sem custo
    @BeforeAll
    static void onlyWithTheDocsProfile() {
        assumeTrue(ClassUtils.isPresent("org.springdoc.core.configuration.SpringDocConfiguration", null),
                "sem o perfil docs n tem springdoc");
    }

    @BeforeEach
    void createPrescriber() {
        Prescriber prescriber = new Prescriber();
        prescriber.setName("Dra. Documentacao");
        prescriber.setEmail("docs-prescritora@email.com");
        prescriber.setPassword("hash");
        prescriberToken = "Bearer " + tokenService.generateToken(prescriberRepository.save(prescriber));
    }

    @Test
    void theDocumentationOnlyOpensForWhoIsLoggedIn() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/swagger-ui/index.html").header("Authorization", prescriberToken))
                .andExpect(status().isOk());
    }

    // cada rota diz quem pode usar, lido das regras de acesso do codigo
    @Test
    void everyRouteSaysWhoCanUseIt() throws Exception {
        String docs = mockMvc.perform(get("/v3/api-docs").header("Authorization", prescriberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("2AG"))
                .andExpect(jsonPath("$.servers[0].url").value("/api"))
                .andExpect(jsonPath("$.paths./auth/login.post.summary").value("sem login"))
                .andExpect(jsonPath("$.paths./admin/prescribers.get.summary").value("administração"))
                .andExpect(jsonPath("$.paths./appointments.post.summary")
                        .value("prescritor, só do próprio paciente"))
                .andExpect(jsonPath("$.paths./patients/{patientId}/prescriptions.get.summary")
                        .value("paciente ou prescritor, só do próprio paciente"))
                // o 'ANAMNESE' da regra de acesso n eh papel
                .andExpect(jsonPath("$.paths./anamneses/{id}.get.summary")
                        .value("paciente ou prescritor, só do próprio paciente"))
                // isSelf confere a propria conta, n um paciente
                .andExpect(jsonPath("$.paths./dashboard/prescriber/{id}.get.summary")
                        .value("prescritor, só a própria conta"))
                .andExpect(jsonPath("$.paths./scales/{slug}/responses.post.summary").value("paciente"))
                // o corpo das rotas vem descrito a partir dos dtos
                .andExpect(jsonPath("$.components.schemas.AppointmentScheduleDTO.properties.dateTime").exists())
                .andReturn().getResponse().getContentAsString();

        Map<String, Map<String, Map<String, Object>>> paths = JsonPath.read(docs, "$.paths");
        assertThat(paths).hasSizeGreaterThan(60);
        for (Map.Entry<String, Map<String, Map<String, Object>>> path : paths.entrySet()) {
            for (Map.Entry<String, Map<String, Object>> operation : path.getValue().entrySet()) {
                String route = operation.getKey() + " " + path.getKey();
                assertThat((String) operation.getValue().get("summary")).as(route).matches(WHO_CAN_USE);
                assertThat((String) operation.getValue().get("description")).as(route).startsWith("Quem pode usar: ");
            }
        }
        // as abertas de proposito sao so essas
        List<String> publicRoutes = JsonPath.read(docs, "$.paths.*.*[?(@.summary == 'sem login')]");
        assertThat(publicRoutes).hasSize(8);
    }
}
