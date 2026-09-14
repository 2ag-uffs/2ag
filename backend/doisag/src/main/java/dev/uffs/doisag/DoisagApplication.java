package dev.uffs.doisag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

// o login eh feito pelo AuthService entao o usuario em memoria q o spring cria sozinho fica desligado
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
// preenche sozinho a data de criacao e de alteracao dos registros
@EnableJpaAuditing
// liga o job diario do acompanhamento automatico
@EnableScheduling
public class DoisagApplication {

	public static void main(String[] args) {
		// o sistema inteiro trabalha no horario de brasilia
		// sem isso hoje e agora dependem do fuso da maquina onde a api roda
		TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
		SpringApplication.run(DoisagApplication.class, args);
	}
}
