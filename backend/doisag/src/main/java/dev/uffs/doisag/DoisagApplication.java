package dev.uffs.doisag;

import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.UsersRepository;
import dev.uffs.doisag.service.NotificationService;
import dev.uffs.doisag.service.PrescriberService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

@SpringBootApplication
public class DoisagApplication {

	public static void main(String[] args) {
		SpringApplication.run(DoisagApplication.class, args);
	}

	@Bean
	public CommandLineRunner initDatabase(
			UsersRepository usersRepository,
			PasswordEncoder passwordEncoder,
			PrescriberService prescriberService,
			NotificationService notificationService
	) {
		return args -> {
			// cria o prescritor de teste
			String prescriberEmail = "prescritor@email.com";
			UserDetails prescriberDetails = usersRepository.findByEmail(prescriberEmail);
			Prescriber testPrescriber;

			if (prescriberDetails == null) {
				System.out.println("CRIANDO USUARIO DE TESTE (PRESCRITOR): " + prescriberEmail);
				var newPrescriber = new Prescriber();
				newPrescriber.setName("Bruna Varela");
				newPrescriber.setEmail(prescriberEmail);
				newPrescriber.setPassword("123456");
				newPrescriber.setProfession("biomedica");
				newPrescriber.setRegistryType("CRBM");
				newPrescriber.setRegistryNumber("12345");
				testPrescriber = prescriberService.create(newPrescriber);
			} else {
				testPrescriber = (Prescriber) prescriberDetails;
			}

			// cria o paciente de teste
			String patientEmail = "paciente@email.com";
			UserDetails patientDetails = usersRepository.findByEmail(patientEmail);
			Patient testPatient;

			if (patientDetails == null) {
				System.out.println("CRIANDO USUARIO DE TESTE (PACIENTE): " + patientEmail);
				var newPatient = new Patient();
				newPatient.setName("Paciente Teste da Silva");
				newPatient.setEmail(patientEmail);
				newPatient.setPassword(passwordEncoder.encode("123456"));
				newPatient.setCpf("00000000000");
				newPatient.setBirthDate(LocalDate.of(1990, 5, 15));
				newPatient.setPrescriber(testPrescriber);
				testPatient = usersRepository.save(newPatient);
			} else {
				testPatient = (Patient) patientDetails;
			}

			if (notificationService.getNotificationsForUser(testPatient.getId()).isEmpty()) {
				System.out.println("CRIANDO NOTIFICACOES DE TESTE PARA O PACIENTE...");

				notificationService.createNotification(
						testPatient,
						"Lembrete de Consulta",
						"Sua consulta de retorno com a Dra. Bruna Varela está marcada para depois de amanhã.",
						"APPOINTMENT",
						"/agendamento-consulta"
				);
				notificationService.createNotification(
						testPatient,
						"Lembrete de Consulta",
						"Sua consulta de retorno com a Dra. Bruna Varela está marcada para depois de amanhã.",
						"APPOINTMENT",
						"/agendamento-consulta"
				);

				notificationService.createNotification(
						testPatient,
						"Ajuste na sua prescrição",
						"Houve uma atualização na sua prescrição. Verifique as novas orientações de dosagem.",
						"ALERT",
						"/prescricao"
				);
			}

			// teste notificações para o presc ---
			if (notificationService.getNotificationsForUser(testPrescriber.getId()).isEmpty()) {
				System.out.println("CRIANDO NOTIFICACOES DE TESTE PARA O PRESCRITOR...");

				// notificação de novo paciente
				notificationService.createNotification(
						testPrescriber,
						"Novo Paciente Vinculado",
						"O paciente Paciente Teste da Silva acabou de se cadastrar e está vinculado a você.",
						"PATIENT", // tipo para novo paciente
						"/lista-pacientes"
				);

				// botificação de formulário respondido
				notificationService.createNotification(
						testPrescriber,
						"Paciente respondeu formulário",
						"O paciente Paciente Teste da Silva acabou de preencher o Diário do Sono.",
						"FORM",
						"/paciente/" + testPatient.getId() + "/historico"
				);

				// notificação de agendamento
				notificationService.createNotification(
						testPrescriber,
						"Novo Agendamento",
						"Você tem uma nova consulta com Paciente Teste da Silva em 20/07/2025 às 10:00.",
						"APPOINTMENT",
						"/agendamento-prescritor"
				);

				// notificação de alerta
				notificationService.createNotification(
						testPrescriber,
						"Alerta Clínico de Paciente",
						"Paciente Teste da Silva relatou efeitos adversos. Requer atenção.",
						"ALERT",
						"/paciente/" + testPatient.getId() + "/prontuario"
				);
			}
		};
	}
}
