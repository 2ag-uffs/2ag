package dev.uffs.doisag.infra;

import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.ScaleResponseRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.security.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// os testes de controller rodam dentro de uma transacao q deixa o banco aberto ate o fim da resposta
// isso escondeu leitura preguicosa fora do servico q quebrava a agenda de verdade
// aqui n tem transacao entao cada tela principal precisa abrir como abre no navegador
@SpringBootTest(properties = {
        "api.seed.enabled=true",
        // banco proprio pq o seed grava de verdade e sujaria os outros testes
        "spring.datasource.url=jdbc:h2:mem:doisag-telas;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MainScreensWithDemoDataTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private UsersRepository usersRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private ScaleResponseRepository responseRepository;

    @Test
    void asTelasDoPrescritorAbremSemErro() throws Exception {
        Users prescriber = findUser("prescritor@email.com");
        Users maria = findUser("paciente@email.com");
        String mariaPath = "/patients/" + maria.getId();
        LocalDate today = LocalDate.now();

        List<String> urls = List.of(
                "/auth/me",
                "/profile",
                "/notifications",
                "/dashboard/prescriber/" + prescriber.getId(),
                "/appointments?from=" + today.minusDays(7) + "&to=" + today.plusDays(7),
                "/appointments/requests",
                "/appointments/" + lastAppointmentIdOf(maria),
                "/availability",
                "/patients",
                "/patients?archived=true",
                mariaPath,
                mariaPath + "/appointments",
                mariaPath + "/prescriptions",
                mariaPath + "/anamneses",
                mariaPath + "/scales",
                mariaPath + "/scales/overview",
                mariaPath + "/scales/responses",
                mariaPath + "/treatment-protocol",
                mariaPath + "/progress?attribute=DOR&period=DIAS_60",
                mariaPath + "/progress/comments?period=DIAS_60",
                mariaPath + "/progress/appointments?period=DIAS_60",
                mariaPath + "/audit-events?from=" + today.minusDays(30) + "&to=" + today,
                "/scales/responses/" + lastResponseIdOf(maria),
                "/scales/assignable",
                "/scales/definitions",
                "/progress/attributes");

        assertThat(failedRequests(urls, prescriber)).isEmpty();
    }

    @Test
    void asTelasDoPacienteAbremSemErro() throws Exception {
        Users maria = findUser("paciente@email.com");
        String mariaPath = "/patients/" + maria.getId();
        LocalDate today = LocalDate.now();

        List<String> urls = List.of(
                "/auth/me",
                "/profile",
                "/notifications",
                "/dashboard/patient/" + maria.getId(),
                "/appointments/mine",
                "/appointments/free-slots?from=" + today + "&to=" + today.plusDays(14),
                mariaPath,
                mariaPath + "/appointments",
                mariaPath + "/prescriptions",
                mariaPath + "/anamneses",
                mariaPath + "/scales",
                mariaPath + "/scales/overview",
                mariaPath + "/scales/responses",
                mariaPath + "/treatment-protocol",
                mariaPath + "/progress?attribute=DOR&period=DIAS_30",
                mariaPath + "/progress/comments?period=DIAS_30",
                mariaPath + "/progress/appointments?period=DIAS_30",
                "/scales/acompanhamento-semanal/responses",
                "/scales/responses/" + lastResponseIdOf(maria),
                "/progress/attributes");

        assertThat(failedRequests(urls, maria)).isEmpty();
    }

    // junta todas as falhas pra mostrar o tamanho do problema de uma vez so
    private List<String> failedRequests(List<String> urls, Users user) throws Exception {
        String token = "Bearer " + tokenService.generateToken(user);
        List<String> failures = new ArrayList<>();
        for (String url : urls) {
            int status = mockMvc.perform(get(url).header("Authorization", token))
                    .andReturn().getResponse().getStatus();
            if (status != 200) {
                failures.add(status + " " + url);
            }
        }
        return failures;
    }

    private Users findUser(String email) {
        return usersRepository.findByEmail(email).orElseThrow();
    }

    private Long lastAppointmentIdOf(Users patient) {
        return appointmentRepository.findByPatientIdOrderByDateTimeDesc(patient.getId()).get(0).getId();
    }

    private Long lastResponseIdOf(Users patient) {
        return responseRepository.findByPatientIdOrderByPeriodStartDesc(patient.getId()).get(0).getId();
    }
}
