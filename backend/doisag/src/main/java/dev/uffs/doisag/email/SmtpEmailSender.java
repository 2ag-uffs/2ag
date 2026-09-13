package dev.uffs.doisag.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

// envio de verdade pelo servidor smtp configurado nas variaveis MAIL
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender javaMailSender;
    private final String senderAddress;

    public SmtpEmailSender(JavaMailSender javaMailSender, String senderAddress) {
        this.javaMailSender = javaMailSender;
        this.senderAddress = senderAddress;
    }

    @Override
    public void send(EmailMessage message) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setFrom(senderAddress);
        mailMessage.setTo(message.to());
        mailMessage.setSubject(message.subject());
        mailMessage.setText(message.text());

        try {
            javaMailSender.send(mailMessage);
        } catch (MailException exception) {
            // quem pede o link de senha nova recebe sempre a mesma resposta
            // entao a falha do servidor fica so no log e sem o endereco nem o link
            log.error("falha ao enviar o e-mail com assunto {}", message.subject(), exception);
        }
    }
}
