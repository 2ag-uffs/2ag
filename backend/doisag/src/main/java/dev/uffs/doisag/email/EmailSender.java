package dev.uffs.doisag.email;

// quem manda e-mail no sistema
// o EmailConfig escolhe entre o envio pelo servidor smtp e o q so escreve no log
public interface EmailSender {

    // falso qnd o e-mail n saiu: sem smtp ele so vai pro log, e com smtp o servidor pode falhar
    boolean send(EmailMessage message);
}
