package dev.uffs.doisag.email;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// manda o e-mail dps q o banco confirmou e fora da requisicao
// assim a espera pelo servidor de e-mail n denuncia q a conta existe
@Component
public class BackgroundEmailSender {

    private static final Logger log = LoggerFactory.getLogger(BackgroundEmailSender.class);
    private static final int STOP_WAIT_SECONDS = 20;

    private final EmailSender emailSender;
    private final boolean inBackground;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public BackgroundEmailSender(EmailSender emailSender,
                                 @Value("${api.email.background:true}") boolean inBackground) {
        this.emailSender = emailSender;
        this.inBackground = inBackground;
    }

    // o userId diz no log de quem era o e-mail q n saiu
    public void sendLater(EmailMessage message, Long userId) {
        // no teste a transacao nunca eh confirmada, entao la o envio eh direto
        if (!inBackground) {
            deliver(message, userId);
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            executor.execute(() -> deliver(message, userId));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                executor.execute(() -> deliver(message, userId));
            }
        });
    }

    // o endereco n vai pro log, so o numero da conta
    private void deliver(EmailMessage message, Long userId) {
        boolean wasSent = emailSender.send(message);
        if (!wasSent) {
            log.warn("o e-mail com assunto {} n saiu pra conta {}", message.subject(), userId);
        }
    }

    // espera o q ja estava na fila, senao um deploy no meio do envio some com o link de alguem
    @PreDestroy
    public void stop() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(STOP_WAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("a api parou com e-mail ainda na fila de envio");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
