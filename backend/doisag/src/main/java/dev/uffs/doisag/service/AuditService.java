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

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    // o prescritor abrindo dado clinico de um paciente
    // o paciente olhando os proprios dados n entra na trilha
    @Transactional
    public void recordChartView(Long patientId) {
        Users loggedUser = findLoggedUser();
        if (!(loggedUser instanceof Prescriber)) {
            return;
        }

        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(CHART_VIEW_WINDOW_MINUTES);
        boolean alreadyRecorded = auditEventRepository.existsByActorIdAndPatientIdAndOperationAndOccurredAtAfter(
                loggedUser.getId(), patientId, AuditOperation.VISUALIZACAO, windowStart);
        if (alreadyRecorded) {
            return;
        }
        auditEventRepository.save(
                new AuditEvent(loggedUser, AuditOperation.VISUALIZACAO, AuditRecordType.PRONTUARIO, null, patientId));
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
