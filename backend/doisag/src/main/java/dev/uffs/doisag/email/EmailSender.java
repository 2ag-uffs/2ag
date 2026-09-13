package dev.uffs.doisag.email;

// quem manda e-mail no sistema
// o EmailConfig escolhe entre o envio pelo servidor smtp e o q so escreve no log
public interface EmailSender {

    void send(EmailMessage message);
}
