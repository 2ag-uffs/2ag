package dev.uffs.doisag.email;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// o e-mail so sai pelo servidor smtp quando MAIL_HOST foi preenchido
class EmailConfigTest {

    private final EmailConfig emailConfig = new EmailConfig();
    private final JavaMailSender javaMailSender = mock(JavaMailSender.class);

    @Test
    void withoutMailHostTheEmailOnlyGoesToTheLog() {
        EmailSender emailSender = emailConfig.emailSender(providerOf(javaMailSender), "  ", "conta@clinica.com", "");

        assertThat(emailSender).isInstanceOf(LogEmailSender.class);
    }

    @Test
    void withMailHostTheEmailGoesThroughTheSmtpServer() {
        EmailSender emailSender = emailConfig.emailSender(providerOf(javaMailSender), "smtp.clinica.com",
                "conta@clinica.com", "nao-responda@clinica.com");

        assertThat(emailSender).isInstanceOf(SmtpEmailSender.class);
    }

    @Test
    void withoutOwnSenderTheEmailUsesTheAccountThatLogsIntoTheServer() {
        EmailSender emailSender = emailConfig.emailSender(providerOf(javaMailSender), "smtp.clinica.com",
                "conta@clinica.com", "");

        emailSender.send(new EmailMessage("paciente@email.com", "Assunto", "Texto"));

        ArgumentCaptor<SimpleMailMessage> sentMessage = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(sentMessage.capture());
        assertThat(sentMessage.getValue().getFrom()).isEqualTo("conta@clinica.com");
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<JavaMailSender> providerOf(JavaMailSender mailSender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(mailSender);
        return provider;
    }
}
