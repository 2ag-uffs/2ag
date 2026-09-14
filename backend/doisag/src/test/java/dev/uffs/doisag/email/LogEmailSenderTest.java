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
        new LogEmailSender(false).send(RESET_EMAIL);

        assertThat(output).contains("Criar uma senha nova no 2AG");
        assertThat(output).doesNotContain("codigo-secreto");
    }

    @Test
    void developmentLogShowsTheWholeText(CapturedOutput output) {
        new LogEmailSender(true).send(RESET_EMAIL);

        assertThat(output).contains("codigo-secreto");
    }
}
