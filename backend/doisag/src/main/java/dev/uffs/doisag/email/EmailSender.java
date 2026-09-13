package dev.uffs.doisag.email;

// quem manda e-mail no sistema
// hoje so existe a versao q escreve no log ate o servidor de e-mail ser configurado
public interface EmailSender {

    void send(EmailMessage message);
}
