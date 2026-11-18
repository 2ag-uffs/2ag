package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.AuditEventDTO;
import dev.uffs.doisag.dto.AuditPageDTO;
import dev.uffs.doisag.enums.AuditOperation;
import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.enums.UserRole;
import dev.uffs.doisag.infra.BrazilianFormat;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.DateCheck;
import dev.uffs.doisag.model.AuditEvent;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AuditEventRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
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

    // comeco do detail da troca da data de nascimento, q tbm serve pra achar ela na trilha
    public static final String BIRTH_DATE_DETAIL = "Data de nascimento: ";

    private final AuditEventRepository auditEventRepository;
    private final PrescriberRepository prescriberRepository;

    // hora do ultimo acesso gravado de cada prescritor em cada paciente
    // o historico abre varias listas juntas e quando as outras chegam a gravacao da primeira
    // ainda n foi confirmada no banco entao so olhar o banco deixava passar acesso repetido
    private final Map<String, LocalDateTime> lastChartViews = new HashMap<>();

    public AuditService(AuditEventRepository auditEventRepository, PrescriberRepository prescriberRepository) {
        this.auditEventRepository = auditEventRepository;
        this.prescriberRepository = prescriberRepository;
    }

    @Transactional
    public void recordCreation(AuditRecordType recordType, Long recordId, Long patientId) {
        saveLoggedUserEvent(AuditOperation.CRIACAO, recordType, recordId, patientId);
    }

    @Transactional
    public void recordChange(AuditRecordType recordType, Long recordId, Long patientId) {
        saveLoggedUserEvent(AuditOperation.ALTERACAO, recordType, recordId, patientId);
    }

    @Transactional
    public void recordAnnulment(AuditRecordType recordType, Long recordId, Long patientId) {
        saveLoggedUserEvent(AuditOperation.ANULACAO, recordType, recordId, patientId);
    }

    // arquivar e reativar valem pro prontuario inteiro do paciente
    @Transactional
    public void recordArchiving(Long patientId) {
        saveLoggedUserEvent(AuditOperation.ARQUIVAMENTO, AuditRecordType.PRONTUARIO, null, patientId);
    }

    @Transactional
    public void recordReactivation(Long patientId) {
        saveLoggedUserEvent(AuditOperation.REATIVACAO, AuditRecordType.PRONTUARIO, null, patientId);
    }

    // o administrador gerando senha nova pra um prescritor
    // eh evento de conta e n de prontuario, entao vai pra trilha sem paciente
    // e aparece so na lista da administracao
    @Transactional
    public void recordPasswordReset(Long prescriberId) {
        saveLoggedUserEvent(AuditOperation.REDEFINICAO_DE_SENHA, AuditRecordType.CONTA_DE_PRESCRITOR,
                prescriberId, null);
    }

    // a propria pessoa confirmou um e-mail de acesso novo pelo link, sem sessao,
    // entao quem fez vai explicito em vez de sair do contexto de seguranca
    // a conta de paciente leva o paciente junto pra aparecer tbm na trilha do prontuario dele
    @Transactional
    public void recordEmailChange(Users user) {
        boolean isPatient = user.getRole() == UserRole.PATIENT;
        AuditRecordType accountType = isPatient ? AuditRecordType.CONTA_DE_PACIENTE
                : AuditRecordType.CONTA_DE_PRESCRITOR;
        auditEventRepository.save(new AuditEvent(user, AuditOperation.ALTERACAO, accountType, user.getId(),
                isPatient ? user.getId() : null));
    }

    // o paciente trocou a data de nascimento no perfil. a faixa de idade sai dela (responsavel no termo e
    // pesquisa fechada pro menor), entao a trilha guarda a antiga e a nova (revisao das regras R9)
    @Transactional
    public void recordBirthDateChange(Long patientId, LocalDate oldDate, LocalDate newDate) {
        String oldText = oldDate == null ? "em branco" : BrazilianFormat.date(oldDate);
        String detail = BIRTH_DATE_DETAIL + oldText + " → " + BrazilianFormat.date(newDate);
        saveLoggedUserEvent(AuditOperation.ALTERACAO, AuditRecordType.CONTA_DE_PACIENTE, patientId, patientId,
                detail);
    }

    // se a data de nascimento foi trocada no perfil dps desse momento, tipo dps do ultimo aceite do termo
    @Transactional(readOnly = true)
    public boolean birthDateChangedAfter(Long patientId, LocalDateTime moment) {
        return auditEventRepository.existsByPatientIdAndOperationAndRecordTypeAndDetailStartingWithAndOccurredAtAfter(
                patientId, AuditOperation.ALTERACAO, AuditRecordType.CONTA_DE_PACIENTE, BIRTH_DATE_DETAIL, moment);
    }

    // um arquivo do prontuario baixado, por qualquer pessoa, sem a janela de 30 minutos da abertura de tela:
    // a clinica precisa dizer quem tirou o prontuario do sistema, qnd, e se foi pra pesquisa (revisao das regras R19)
    @Transactional
    public void recordExport(Long patientId, String fileDetail) {
        saveLoggedUserEvent(AuditOperation.EXPORTACAO, AuditRecordType.PRONTUARIO, null, patientId, fileDetail);
    }

    // a busca por e-mail devolve o numero e o nome do paciente, entao fica registrada, na trilha da
    // administracao e na do prontuario dele, senao dava pra descobrir de quem eh cada numero sem deixar rastro
    // (revisao das regras R20)
    @Transactional
    public void recordAdminLookup(Long patientId) {
        saveLoggedUserEvent(AuditOperation.VISUALIZACAO, AuditRecordType.CONTA_DE_PACIENTE, patientId, patientId,
                "Busca da conta pelo e-mail");
    }

    // a administracao digitou num formulario de prescritor o cpf ou o e-mail de um paciente, e o 409 contou
    // pra ela q ele tem conta. fica como a busca pelo e-mail, na trilha dela e na do prontuario dele
    // (decisao de 17/11/2026). o 409 desfaz a transacao de quem chama, entao a linha vai numa transacao so dela
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPatientDataTypedByAdmin(Long patientId, String detail) {
        saveLoggedUserEvent(AuditOperation.VISUALIZACAO, AuditRecordType.CONTA_DE_PACIENTE, patientId, patientId,
                detail);
    }

    // a lista das contas desativadas mostra nome e e-mail de todas, entao cada abertura tbm fica, sem paciente
    @Transactional
    public void recordDeactivatedListView() {
        saveLoggedUserEvent(AuditOperation.VISUALIZACAO, AuditRecordType.CONTA_DE_PACIENTE, null, null,
                "Lista das contas desativadas");
    }

    // conta ligada ou desligada pela administracao
    // a de paciente leva o paciente junto pra aparecer tbm na trilha do prontuario dele
    @Transactional
    public void recordAccountActiveChange(AuditRecordType accountType, Long accountId, Long patientId,
                                          boolean active) {
        AuditOperation operation = active ? AuditOperation.REATIVACAO : AuditOperation.DESATIVACAO;
        saveLoggedUserEvent(operation, accountType, accountId, patientId);
    }

    // o prescritor abrindo dado clinico de um paciente
    // o paciente olhando os proprios dados n entra na trilha
    //
    // quem chama isso n pode estar numa transacao readOnly: no postgres ela vira
    // BEGIN READ ONLY e o insert da trilha eh recusado, o h2 n reclama e esconde o erro
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

        // dps de reiniciar a api a memoria comeca vazia mas o banco ainda lembra do acesso
        // a memoria esquece essa abertura q n virou linha, senao a janela contava dela e n da linha do banco
        boolean alreadyRecorded = auditEventRepository.existsByActorIdAndPatientIdAndOperationAndOccurredAtAfter(
                loggedUser.getId(), patientId, AuditOperation.VISUALIZACAO, windowStart);
        if (alreadyRecorded) {
            forgetChartView(viewKey);
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
    // e sem dizer qual registro clinico foi, ver AuditEventDTO.forAdmin
    @Transactional(readOnly = true)
    public AuditPageDTO getStaffEvents(LocalDate from, LocalDate to, int page) {
        checkPeriod(from, to);
        Page<AuditEvent> staffEvents = auditEventRepository.findStaffEvents(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay(), UserRole.PATIENT, pageRequest(page));

        // de quem era a conta de prescritor mexida, lendo os prescritores da pagina de uma vez
        List<Long> prescriberIds = staffEvents.getContent()
                .stream()
                .filter(this::isPrescriberAccountEvent)
                .map(AuditEvent::getRecordId)
                .distinct()
                .toList();
        Map<Long, String> prescriberNames = new HashMap<>();
        for (Prescriber prescriber : prescriberRepository.findAllById(prescriberIds)) {
            prescriberNames.put(prescriber.getId(), prescriber.getName());
        }

        List<AuditEventDTO> events = staffEvents.getContent()
                .stream()
                // o recordId de outro evento eh de consulta, receita... e pode bater com o id de um prescritor
                .map(event -> AuditEventDTO.forAdmin(event,
                        isPrescriberAccountEvent(event) ? prescriberNames.get(event.getRecordId()) : null))
                .toList();
        return new AuditPageDTO(events, staffEvents.getNumber(), staffEvents.getTotalPages(),
                staffEvents.getTotalElements());
    }

    private boolean isPrescriberAccountEvent(AuditEvent auditEvent) {
        return auditEvent.getRecordType() == AuditRecordType.CONTA_DE_PRESCRITOR && auditEvent.getRecordId() != null;
    }

    private void checkPeriod(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessException(DateCheck.INVALID_RANGE_MESSAGE);
        }
    }

    private PageRequest pageRequest(int page) {
        return PageRequest.of(Math.max(page, 0), PAGE_SIZE);
    }

    private void saveLoggedUserEvent(AuditOperation operation, AuditRecordType recordType, Long recordId,
                                     Long patientId) {
        saveLoggedUserEvent(operation, recordType, recordId, patientId, null);
    }

    private void saveLoggedUserEvent(AuditOperation operation, AuditRecordType recordType, Long recordId,
                                     Long patientId, String detail) {
        auditEventRepository.save(
                new AuditEvent(findLoggedUser(), operation, recordType, recordId, patientId, detail));
    }

    private Users findLoggedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Users loggedUser)) {
            return null;
        }
        return loggedUser;
    }
}
