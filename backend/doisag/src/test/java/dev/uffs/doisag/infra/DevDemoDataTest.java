package dev.uffs.doisag.infra;

import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.model.ScaleTask;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.NotificationRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.ScaleResponseRepository;
import dev.uffs.doisag.repository.ScaleTaskRepository;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.service.TreatmentProtocolService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// o seed de demonstracao engole erro pra n derrubar a api
// entao esse teste confere q ele chega ate o fim com as regras atuais do sistema
@SpringBootTest(properties = {
        "api.seed.enabled=true",
        // banco proprio pq o seed grava de verdade e sujaria os outros testes
        "spring.datasource.url=jdbc:h2:mem:doisag-demo;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
})
@ActiveProfiles("test")
@Import(NarrowingProxyWatch.class)
class DevDemoDataTest {

    @Autowired private UsersRepository usersRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private ScaleResponseRepository responseRepository;
    @Autowired private ScaleTaskRepository taskRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private TreatmentProtocolService treatmentProtocolService;

    @Test
    void oSeedCriaADemonstracaoInteira() {
        Users prescriber = usersRepository.findByEmail("prescritor@email.com").orElseThrow();
        Users maria = usersRepository.findByEmail("paciente@email.com").orElseThrow();
        Users paulo = usersRepository.findByEmail("paulo.nunes@email.com").orElseThrow();
        Users carla = usersRepository.findByEmail("carla.menezes@email.com").orElseThrow();

        // oito semanas de diario com alguns dias em branco mais as duas hamilton
        assertThat(responseRepository.findByPatientIdOrderByPeriodStartDescIdDesc(maria.getId())).hasSizeGreaterThan(40);
        assertThat(appointmentRepository.findByPrescriberIdOrderByDateTimeAsc(prescriber.getId()))
                .hasSizeGreaterThanOrEqualTo(7);
        assertThat(patientRepository.findById(carla.getId()).orElseThrow().isArchived()).isTrue();
        // a ficha do paulo eh a ultima etapa entao se ela existe nada parou no meio
        assertThat(taskRepository.countByPatientIdAndStatus(paulo.getId(), ScaleTaskStatus.PENDENTE)).isEqualTo(1);
        // os avisos do historico saem e so os dos ultimos dias ficam pra ler
        assertThat(notificationRepository.countUnread(prescriber.getId())).isBetween(1L, 15L);
        // a subida com o seed era onde o hibernate mais avisava "narrowing proxy" (issue 39)
        assertThat(NarrowingProxyWatch.warnings()).isZero();
        // a senha das contas de teste veio pronta, entao o administrador n troca o e-mail delas (issue 131)
        assertThat(prescriber.getPasswordChangedAt()).isNotNull();
        assertThat(usersRepository.findByEmail("carlos.pereira@email.com").orElseThrow().getPasswordChangedAt())
                .isNotNull();
    }

    // o diario e o diario do sono da semana da maria ja sao a rodada do acompanhamento dela
    // o job q roda dps do seed (a cada minuto no ambiente de teste) adotava os dois e mudava o prazo
    @Test
    void oJobDoAcompanhamentoDeixaAsEscalasDaDemonstracaoComoEstao() {
        Users maria = usersRepository.findByEmail("paciente@email.com").orElseThrow();
        LocalDate today = LocalDate.now();
        List<String> before = pendingTasksOf(maria);
        assertThat(before).contains(
                ScaleType.ACOMPANHAMENTO_SEMANAL + " " + today.minusDays(2) + " " + today.plusDays(4),
                ScaleType.REGISTRO_SONO + " " + today + " " + today.plusDays(6));

        int sent = treatmentProtocolService.designarEscalasVencidas(today);

        assertThat(sent).isZero();
        assertThat(pendingTasksOf(maria)).isEqualTo(before);
    }

    // escala, inicio e prazo de cada tarefa pendente
    private List<String> pendingTasksOf(Users patient) {
        List<String> tasks = new ArrayList<>();
        for (ScaleTask task : taskRepository.findByPatientIdAndStatusOrderByPeriodEndAsc(patient.getId(),
                ScaleTaskStatus.PENDENTE)) {
            tasks.add(task.getScaleType() + " " + task.getPeriodStart() + " " + task.getPeriodEnd());
        }
        return tasks;
    }
}
