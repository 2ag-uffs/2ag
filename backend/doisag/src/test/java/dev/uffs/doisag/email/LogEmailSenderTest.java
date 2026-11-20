package dev.uffs.doisag.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

// sem smtp o e-mail vai pro log e em producao o link de senha nova n pode ficar la
@ExtendWith(OutputCaptureExtension.class)
class LogEmailSenderTest {

    private static final EmailMessage RESET_EMAIL = new EmailMessage("paciente@email.com",
            "Criar uma senha nova no 2AG",
            "Abra o link: http://localhost:5173/redefinir-senha?token=codigo-secreto");

    @Test
    void productionLogShowsTheSubjectButNotTheLink(CapturedOutput output) {
        boolean wasSent = new LogEmailSender(false).send(RESET_EMAIL);

        // ninguem recebeu, entao quem chamou precisa saber q o e-mail n saiu
        assertThat(wasSent).isFalse();
        assertThat(output).contains("Criar uma senha nova no 2AG");
        assertThat(output).doesNotContain("codigo-secreto");
    }

    // o endereco de quem recebe fica no log de proposito: eh por ele q o teste local acha o e-mail
    // por isso o docs/guarda-de-dados.md lista esse dado nos logs da api
    @Test
    void productionLogKeepsTheRecipientAddressAndSubject(CapturedOutput output) {
        new LogEmailSender(false).send(new EmailMessage("maria@exemplo.com", "Sua consulta no 2AG é amanhã", "texto"));

        assertThat(output).contains("e-mail para maria@exemplo.com com assunto Sua consulta no 2AG é amanhã");
    }

    @Test
    void developmentLogShowsTheWholeText(CapturedOutput output) {
        new LogEmailSender(true).send(RESET_EMAIL);

        assertThat(output).contains("codigo-secreto");
    }
}
