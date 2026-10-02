package dev.uffs.doisag.email;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class BackgroundEmailSenderTest {

    private static final EmailMessage MESSAGE = new EmailMessage("paciente@email.com", "Assunto", "Texto");

    private final EmailSender emailSender = mock(EmailSender.class);
    private final BackgroundEmailSender backgroundEmailSender = new BackgroundEmailSender(emailSender, true);

    @AfterEach
    void stopSender() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        backgroundEmailSender.stop();
    }

    @Test
    void withoutATransactionTheEmailGoesOutInBackground() {
        backgroundEmailSender.sendLater(MESSAGE);

        verify(emailSender, timeout(2000)).send(MESSAGE);
    }

    @Test
    void insideATransactionTheEmailWaitsForTheCommit() {
        TransactionSynchronizationManager.initSynchronization();

        backgroundEmailSender.sendLater(MESSAGE);
        verify(emailSender, never()).send(any());

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        verify(emailSender, timeout(2000)).send(MESSAGE);
    }

    // link de um registro q o banco desfez n pode chegar na caixa de ninguem
    @Test
    void rolledBackTransactionSendsNothing() {
        TransactionSynchronizationManager.initSynchronization();

        backgroundEmailSender.sendLater(MESSAGE);
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        verify(emailSender, never()).send(any());
    }

    @Test
    void withBackgroundOffTheEmailGoesOutRightAway() {
        BackgroundEmailSender directSender = new BackgroundEmailSender(emailSender, false);

        directSender.sendLater(MESSAGE);

        verify(emailSender).send(MESSAGE);
        directSender.stop();
    }
}
