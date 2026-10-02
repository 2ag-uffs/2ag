package dev.uffs.doisag.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
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
    public boolean send(EmailMessage message) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setFrom(senderAddress);
        mailMessage.setTo(message.to());
        mailMessage.setSubject(message.subject());
        mailMessage.setText(message.text());

        try {
            javaMailSender.send(mailMessage);
            return true;
        } catch (MailException exception) {
            // a mensagem da excecao pode trazer o endereco de quem ia receber, entao so a classe vai pro log
            log.error("falha ao enviar o e-mail com assunto {}: {}", message.subject(), failureName(exception));
            log.debug("detalhe da falha no envio", exception);
            return false;
        }
    }

    private String failureName(MailException exception) {
        String name = exception.getClass().getSimpleName();
        if (exception instanceof MailSendException sendException
                && sendException.getMessageExceptions().length > 0) {
            return name + " (" + sendException.getMessageExceptions()[0].getClass().getSimpleName() + ")";
        }
        if (exception.getCause() != null) {
            return name + " (" + exception.getCause().getClass().getSimpleName() + ")";
        }
        return name;
    }
}
