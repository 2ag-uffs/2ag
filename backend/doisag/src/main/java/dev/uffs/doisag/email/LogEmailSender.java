package dev.uffs.doisag.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// envio de mentira q so escreve o e-mail no log
// o EmailConfig usa esse quando MAIL_HOST n esta preenchido
// cuidado pq o link de recuperacao de senha aparece inteiro no log
public class LogEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);

    public LogEmailSender() {
        log.warn("envio de e-mail nao configurado: as mensagens aparecem so no log da api");
    }

    @Override
    public void send(EmailMessage message) {
        log.info("e-mail para {} com assunto {}\n{}", message.to(), message.subject(), message.text());
    }
}
