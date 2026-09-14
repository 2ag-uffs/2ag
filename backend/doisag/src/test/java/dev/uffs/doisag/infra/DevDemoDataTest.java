package dev.uffs.doisag.infra;

import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.NotificationRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.ScaleResponseRepository;
import dev.uffs.doisag.repository.ScaleTaskRepository;
import dev.uffs.doisag.repository.UsersRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

// o seed de demonstracao engole erro pra n derrubar a api
// entao esse teste confere q ele chega ate o fim com as regras atuais do sistema
@SpringBootTest(properties = {
        "api.seed.enabled=true",
        // banco proprio pq o seed grava de verdade e sujaria os outros testes
        "spring.datasource.url=jdbc:h2:mem:doisag-demo;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
})
@ActiveProfiles("test")
class DevDemoDataTest {

    @Autowired private UsersRepository usersRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private ScaleResponseRepository responseRepository;
    @Autowired private ScaleTaskRepository taskRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private NotificationRepository notificationRepository;

    @Test
    void oSeedCriaADemonstracaoInteira() {
        Users prescriber = usersRepository.findByEmail("prescritor@email.com").orElseThrow();
        Users maria = usersRepository.findByEmail("paciente@email.com").orElseThrow();
        Users paulo = usersRepository.findByEmail("paulo.nunes@email.com").orElseThrow();
        Users carla = usersRepository.findByEmail("carla.menezes@email.com").orElseThrow();

        // oito semanas de diario com alguns dias em branco mais as duas hamilton
        assertThat(responseRepository.findByPatientIdOrderByPeriodStartDesc(maria.getId())).hasSizeGreaterThan(40);
        assertThat(appointmentRepository.findByPrescriberIdOrderByDateTimeAsc(prescriber.getId()))
                .hasSizeGreaterThanOrEqualTo(7);
        assertThat(patientRepository.findById(carla.getId()).orElseThrow().isArchived()).isTrue();
        // a ficha do paulo eh a ultima etapa entao se ela existe nada parou no meio
        assertThat(taskRepository.countByPatientIdAndStatus(paulo.getId(), ScaleTaskStatus.PENDENTE)).isEqualTo(1);
        // os avisos do historico saem e so os dos ultimos dias ficam pra ler
        assertThat(notificationRepository.countUnread(prescriber.getId())).isBetween(1L, 15L);
    }
}
