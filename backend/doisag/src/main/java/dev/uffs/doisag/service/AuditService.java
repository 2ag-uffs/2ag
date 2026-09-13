package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.AuditPageDTO;
import dev.uffs.doisag.enums.AuditOperation;
import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.enums.UserRole;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.model.AuditEvent;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AuditEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

// grava e le a trilha de auditoria do prontuario (RF31)
// quem fez sai da sessao e fica nulo quando quem faz eh o proprio sistema
// os servicos chamam isso dentro da propria transacao entao a mudanca e o evento entram juntos no banco
@Service
public class AuditService {

    // quantos eventos cada pagina da trilha mostra
    public static final int PAGE_SIZE = 30;

    // abrir o mesmo prontuario de novo dentro desse tempo n gera outra linha
    public static final int CHART_VIEW_WINDOW_MINUTES = 30;

    private final AuditEventRepository auditEventRepository;

    // hora do ultimo acesso gravado de cada prescritor em cada paciente
    // o historico abre varias listas juntas e quando as outras chegam a gravacao da primeira
    // ainda n foi confirmada no banco entao so olhar o banco deixava passar acesso repetido
    private final Map<String, LocalDateTime> lastChartViews = new HashMap<>();

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional
    public void recordCreation(AuditRecordType recordType, Long recordId, Long patientId) {
        auditEventRepository.save(
                new AuditEvent(findLoggedUser(), AuditOperation.CRIACAO, recordType, recordId, patientId));
    }

    @Transactional
    public void recordChange(AuditRecordType recordType, Long recordId, Long patientId) {
        auditEventRepository.save(
                new AuditEvent(findLoggedUser(), AuditOperation.ALTERACAO, recordType, recordId, patientId));
    }

    @Transactional
    public void recordAnnulment(AuditRecordType recordType, Long recordId, Long patientId) {
        auditEventRepository.save(
                new AuditEvent(findLoggedUser(), AuditOperation.ANULACAO, recordType, recordId, patientId));
    }

    // o prescritor abrindo dado clinico de um paciente
    // o paciente olhando os proprios dados n entra na trilha
    @Transactional
    public void recordChartView(Long patientId) {
        Users loggedUser = findLoggedUser();
        if (!(loggedUser instanceof Prescriber)) {
            return;
        }

        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(CHART_VIEW_WINDOW_MINUTES);
        String viewKey = loggedUser.getId() + "-" + patientId;
        if (!claimChartView(viewKey, windowStart)) {
            return;
        }
        forgetChartViewIfRolledBack(viewKey);

        // depois de reiniciar a api a memoria comeca vazia mas o banco ainda lembra do acesso
        boolean alreadyRecorded = auditEventRepository.existsByActorIdAndPatientIdAndOperationAndOccurredAtAfter(
                loggedUser.getId(), patientId, AuditOperation.VISUALIZACAO, windowStart);
        if (alreadyRecorded) {
            return;
        }
        auditEventRepository.save(
                new AuditEvent(loggedUser, AuditOperation.VISUALIZACAO, AuditRecordType.PRONTUARIO, null, patientId));
    }

    // so a primeira chamada de cada janela ganha o direito de gravar o acesso
    private synchronized boolean claimChartView(String viewKey, LocalDateTime windowStart) {
        LocalDateTime lastView = lastChartViews.get(viewKey);
        if (lastView != null && lastView.isAfter(windowStart)) {
            return false;
        }
        lastChartViews.put(viewKey, LocalDateTime.now());
        return true;
    }

    private synchronized void forgetChartView(String viewKey) {
        lastChartViews.remove(viewKey);
    }

    // se a leitura der erro o banco desfaz a gravacao do acesso
    // entao a memoria esquece tbm e a proxima abertura grava de novo
    private void forgetChartViewIfRolledBack(String viewKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    forgetChartView(viewKey);
                }
            }
        });
    }

    // trilha de um paciente com as duas datas inclusive
    @Transactional(readOnly = true)
    public AuditPageDTO getPatientEvents(Long patientId, LocalDate from, LocalDate to, int page) {
        checkPeriod(from, to);
        return AuditPageDTO.from(auditEventRepository.findPatientEvents(
                patientId, from.atStartOfDay(), to.plusDays(1).atStartOfDay(), pageRequest(page)));
    }

    // o q prescritores e o sistema fizeram no periodo sem as acoes dos pacientes
    @Transactional(readOnly = true)
    public AuditPageDTO getStaffEvents(LocalDate from, LocalDate to, int page) {
        checkPeriod(from, to);
        return AuditPageDTO.from(auditEventRepository.findStaffEvents(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay(), UserRole.PATIENT, pageRequest(page)));
    }

    private void checkPeriod(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessException("A data inicial precisa ser igual ou anterior à data final");
        }
    }

    private PageRequest pageRequest(int page) {
        return PageRequest.of(Math.max(page, 0), PAGE_SIZE);
    }

    private Users findLoggedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Users loggedUser)) {
            return null;
        }
        return loggedUser;
    }
}
