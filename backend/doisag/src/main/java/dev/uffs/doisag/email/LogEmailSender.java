package dev.uffs.doisag.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// envio de mentira q so escreve o e-mail no log
// o EmailConfig usa esse quando MAIL_HOST n esta preenchido
// o texto tem o link de senha nova entao so aparece no log qdo showText esta ligado
// em producao quem le o log n pode conseguir trocar a senha de ninguem
public class LogEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);

    private final boolean showText;

    public LogEmailSender(boolean showText) {
        this.showText = showText;
        log.warn("envio de e-mail nao configurado: as mensagens aparecem so no log da api");
    }

    @Override
    public void send(EmailMessage message) {
        if (showText) {
            log.info("e-mail para {} com assunto {}\n{}", message.to(), message.subject(), message.text());
        } else {
            log.info("e-mail para {} com assunto {} nao enviado: configure MAIL_HOST", message.to(), message.subject());
        }
    }
}
