package dev.uffs.doisag.service;

import dev.uffs.doisag.model.AuditEvent;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.AuditEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// acesso ao prontuario gravado uma vez so por janela (RF31)
// o repositorio falso sempre responde q n tem acesso gravado
// igual o banco responde quando varias listas abrem juntas e a primeira gravacao ainda n foi confirmada
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private AuditEventRepository auditEventRepository;
    @InjectMocks private AuditService auditService;

    @BeforeEach
    void logInAsPrescriber() {
        Prescriber prescriber = new Prescriber();
        prescriber.setName("Prescritora da trilha");
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(prescriber, null, List.of()));
    }

    @AfterEach
    void clearLoginAndTransaction() {
        SecurityContextHolder.clearContext();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void chartOpenedByManyListsAtOnceIsRecordedOnce() {
        auditService.recordChartView(1L);
        auditService.recordChartView(1L);
        auditService.recordChartView(1L);

        verify(auditEventRepository, times(1)).save(any(AuditEvent.class));
    }

    @Test
    void eachPatientChartIsRecordedSeparately() {
        auditService.recordChartView(1L);
        auditService.recordChartView(2L);

        verify(auditEventRepository, times(2)).save(any(AuditEvent.class));
    }

    // a leitura q deu erro n salvou nada entao a proxima abertura precisa gravar
    @Test
    void chartViewIsRecordedAgainWhenTheFirstReadFailed() {
        openTransaction();
        auditService.recordChartView(1L);
        finishTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        auditService.recordChartView(1L);

        verify(auditEventRepository, times(2)).save(any(AuditEvent.class));
    }

    @Test
    void confirmedChartViewStillBlocksTheRepeatedRecord() {
        openTransaction();
        auditService.recordChartView(1L);
        finishTransaction(TransactionSynchronization.STATUS_COMMITTED);

        auditService.recordChartView(1L);

        verify(auditEventRepository, times(1)).save(any(AuditEvent.class));
    }

    // imita o q o spring faz no comeco e no fim de uma transacao
    private void openTransaction() {
        TransactionSynchronizationManager.initSynchronization();
    }

    private void finishTransaction(int status) {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(status);
        }
        TransactionSynchronizationManager.clearSynchronization();
    }
}
