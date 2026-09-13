package dev.uffs.doisag.email;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

// o e-mail vai com remetente destinatario assunto e texto
// e uma falha do servidor n derruba quem pediu o envio
class SmtpEmailSenderTest {

    private final JavaMailSender javaMailSender = mock(JavaMailSender.class);
    private final SmtpEmailSender smtpEmailSender = new SmtpEmailSender(javaMailSender, "nao-responda@clinica.com");

    @Test
    void messageGoesWithSenderRecipientSubjectAndText() {
        smtpEmailSender.send(new EmailMessage("paciente@email.com", "Criar uma senha nova no 2AG", "Use o link"));

        ArgumentCaptor<SimpleMailMessage> sentMessage = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(sentMessage.capture());
        assertThat(sentMessage.getValue().getFrom()).isEqualTo("nao-responda@clinica.com");
        assertThat(sentMessage.getValue().getTo()).containsExactly("paciente@email.com");
        assertThat(sentMessage.getValue().getSubject()).isEqualTo("Criar uma senha nova no 2AG");
        assertThat(sentMessage.getValue().getText()).isEqualTo("Use o link");
    }

    // quem pede o link de senha nova recebe sempre a mesma resposta
    // entao a falha do servidor fica so no log
    @Test
    void serverFailureDoesNotReachTheCaller() {
        doThrow(new MailSendException("servidor fora do ar")).when(javaMailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> smtpEmailSender.send(new EmailMessage("paciente@email.com", "Assunto", "Texto")))
                .doesNotThrowAnyException();
    }
}
