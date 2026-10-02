package dev.uffs.doisag.email;

import jakarta.mail.SendFailedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

// o e-mail vai com remetente destinatario assunto e texto
// e uma falha do servidor n derruba quem pediu o envio
@ExtendWith(OutputCaptureExtension.class)
class SmtpEmailSenderTest {

    private final JavaMailSender javaMailSender = mock(JavaMailSender.class);
    private final SmtpEmailSender smtpEmailSender = new SmtpEmailSender(javaMailSender, "nao-responda@clinica.com");

    @Test
    void messageGoesWithSenderRecipientSubjectAndText() {
        boolean wasSent = smtpEmailSender.send(
                new EmailMessage("paciente@email.com", "Criar uma senha nova no 2AG", "Use o link"));

        assertThat(wasSent).isTrue();
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
        assertThat(smtpEmailSender.send(new EmailMessage("paciente@email.com", "Assunto", "Texto"))).isFalse();
    }

    // qnd o servidor recusa o destinatario a mensagem da excecao traz o endereco dele
    @Test
    void failureLogHasTheErrorTypeButNotTheRecipient(CapturedOutput output) {
        Exception rejectedRecipient = new SendFailedException("550 5.1.1 <paciente@email.com> rejected");
        doThrow(new MailSendException(Map.of("mensagem", rejectedRecipient)))
                .when(javaMailSender).send(any(SimpleMailMessage.class));

        smtpEmailSender.send(new EmailMessage("paciente@email.com", "Assunto", "Texto"));

        assertThat(output).contains("MailSendException (SendFailedException)");
        assertThat(output).doesNotContain("paciente@email.com");
    }
}
