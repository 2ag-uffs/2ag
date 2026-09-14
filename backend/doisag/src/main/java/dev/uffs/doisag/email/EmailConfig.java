package dev.uffs.doisag.email;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

// escolhe como a api manda e-mail
// com MAIL_HOST preenchido o e-mail sai pelo servidor smtp
// sem ele a api so escreve a mensagem no log como antes
@Configuration
public class EmailConfig {

    // o spring so monta o JavaMailSender quando o host existe entao ele eh pedido so na hora de usar
    @Bean
    public EmailSender emailSender(ObjectProvider<JavaMailSender> javaMailSender,
                                   @Value("${spring.mail.host:}") String mailHost,
                                   @Value("${spring.mail.username:}") String mailUsername,
                                   @Value("${api.email.from:}") String senderAddress,
                                   @Value("${api.email.log-text:false}") boolean logText) {
        if (mailHost.isBlank()) {
            return new LogEmailSender(logText);
        }
        // sem remetente proprio o e-mail sai em nome da conta q autentica no servidor
        String from = senderAddress.isBlank() ? mailUsername : senderAddress;
        return new SmtpEmailSender(javaMailSender.getObject(), from);
    }
}
