package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.PatientScalesPageDTO;
import dev.uffs.doisag.dto.ScaleResponseSummaryDTO;
import dev.uffs.doisag.dto.ScaleTaskDTO;
import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.ScaleResponse;
import dev.uffs.doisag.model.ScaleTask;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.ScaleResponseRepository;
import dev.uffs.doisag.repository.ScaleTaskRepository;
import dev.uffs.doisag.scale.ScaleCatalog;
import dev.uffs.doisag.scale.ScaleDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

// as tarefas de escala q o paciente recebe (RF09 e RF32)
//
// cada tarefa vale por um periodo. passou do fim sem resposta, ela vira
// nao respondida e a proxima ja pode ser enviada, em vez de a mesma
// pendencia arrastar pra sempre e travar as seguintes
@Service
public class ScaleTaskService {

    public static final String PRESCRIBER_SCALE_MESSAGE =
            "O Mini-Exame do Estado Mental é aplicado pelo prescritor durante a consulta";
    public static final String NO_PRESCRIBER_MESSAGE = "Este paciente ainda não tem prescritor vinculado";

    // quanto tempo o paciente tem pra responder uma escala avulsa
    private static final int DEFAULT_TASK_DAYS = 7;

    private final ScaleTaskRepository taskRepository;
    private final ScaleResponseRepository responseRepository;
    private final PatientRepository patientRepository;
    private final PrescriberRepository prescriberRepository;
    private final ScaleCatalog catalog;
    private final AuditService auditService;
    private NotificationService notificationService;

    public ScaleTaskService(ScaleTaskRepository taskRepository,
                            ScaleResponseRepository responseRepository,
                            PatientRepository patientRepository,
                            PrescriberRepository prescriberRepository,
                            ScaleCatalog catalog,
                            AuditService auditService) {
        this.taskRepository = taskRepository;
        this.responseRepository = responseRepository;
        this.patientRepository = patientRepository;
        this.prescriberRepository = prescriberRepository;
        this.catalog = catalog;
        this.auditService = auditService;
    }

    // o aviso depende do servico de notificacao e ele volta aqui, entao
    // a injecao eh preguicosa pra n fechar um ciclo
    @Autowired
    public void setNotificationService(@Lazy NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // envio avulso feito pelo prescritor (RF09)
    // a linha do prescritor fica travada ate o fim: dois envios da mesma escala ao mesmo tempo
    // passavam os dois pela busca da tarefa aberta e viravam duas pendencias iguais
    // so aqui e n na versao do job, senao ele seguraria todos os prescritores a rodada inteira
    @Transactional
    public ScaleTaskDTO assign(Long patientId, ScaleType scaleType) {
        Prescriber prescriber = findPatient(patientId).getPrescriber();
        if (prescriber != null) {
            prescriberRepository.lockById(prescriber.getId());
        }
        ScaleTask task = assign(patientId, scaleType, LocalDate.now(), DEFAULT_TASK_DAYS, false);
        return new ScaleTaskDTO(task, validAnswersOf(task), LocalDate.now(), isDaily(scaleType));
    }

    // a versao com data e prazo serve pro acompanhamento automatico: o
    // job manda o dia q ele esta processando e o prazo da periodicidade
    // so a tarefa do acompanhamento marca o ciclo, entao a avulsa n mexe na proxima rodada (RF32)
    @Transactional
    public ScaleTask assign(Long patientId, ScaleType scaleType, LocalDate today, int taskDays,
                            boolean fromProtocol) {
        // RN09 escala de heteroaplicacao n vira tarefa do paciente
        if (!scaleType.isFilledByPatient()) {
            throw new BusinessException(PRESCRIBER_SCALE_MESSAGE);
        }
        Patient patient = findPatient(patientId);
        Prescriber prescriber = patient.getPrescriber();
        if (prescriber == null) {
            throw new BusinessException(NO_PRESCRIBER_MESSAGE);
        }

        // a mesma escala ainda aberta n vira duas tarefas iguais
        Optional<ScaleTask> openTask = openTaskOf(patientId, scaleType);
        if (openTask.isPresent() && openTask.get().coversDay(today)) {
            return openTask.get();
        }

        ScaleTask task = new ScaleTask();
        task.setPatient(patient);
        task.setPrescriber(prescriber);
        task.setScaleType(scaleType);
        task.setPeriodStart(today);
        task.setPeriodEnd(today.plusDays(taskDays - 1L));
        task.setStatus(ScaleTaskStatus.PENDENTE);
        task.setFromProtocol(fromProtocol);

        ScaleTask savedTask = taskRepository.save(task);
        auditService.recordCreation(AuditRecordType.DESIGNACAO_DE_ESCALA, savedTask.getId(), patientId);

        notificationService.createNotification(patient, "Nova escala para responder",
                scaleType.getDisplayName() + ". Você tem até " + formatDate(savedTask.getPeriodEnd())
                        + " para responder.",
                "FORM", "/pacientes/" + patientId + "/escalas");
        return savedTask;
    }

    // liga a resposta na tarefa aberta daquela escala
    //
    // no diario a tarefa segue aberta ate o fim da semana, pq o paciente
    // preenche um dia de cada vez e a grade so fecha no fim do periodo
    @Transactional
    public void linkResponse(ScaleResponse response) {
        Optional<ScaleTask> openTask = openTaskOf(response.getPatient().getId(), response.getScaleType());
        // amarra pela data do envio e n pela janela informada: na escala de periodo
        // o formulario abre em hoje-6, q cai antes do inicio da tarefa, e o diario
        // aceita preencher dia passado. a tarefa aberta hoje eh a dona da resposta
        LocalDate today = LocalDate.now();
        if (openTask.isEmpty() || !openTask.get().coversDay(today)) {
            return;
        }
        // escala pontuada pela metade n fica com a tarefa: ela segue pendente, o lembrete cobra
        // e no prazo vira nao respondida (RN10)
        if (catalog.definitionOf(response.getScaleType()).isIncomplete(response.getScore())) {
            return;
        }
        ScaleTask task = openTask.get();
        response.setTask(task);
        if (!isDaily(response.getScaleType())) {
            closeAsAnswered(task, today);
        }
    }

    // a correcao q completa a escala fecha a tarefa aberta no dia em q a resposta pela metade
    // foi enviada, e n uma tarefa nova q ja comecou
    @Transactional
    public void linkCompletedResponse(ScaleResponse response) {
        Optional<ScaleTask> openTask = openTaskOf(response.getPatient().getId(), response.getScaleType());
        LocalDate sentDay = response.getCreatedAt().toLocalDate();
        if (response.getTask() != null || openTask.isEmpty() || !openTask.get().coversDay(sentDay)) {
            return;
        }
        linkResponse(response);
    }

    // resposta anulada deixa de contar: sem nenhuma valida a tarefa volta a pendente
    @Transactional
    public void reopenIfWithoutAnswers(ScaleTask task) {
        if (task == null || validAnswersOf(task) > 0) {
            return;
        }
        task.setStatus(ScaleTaskStatus.PENDENTE);
        task.setAnsweredAt(null);
        task.setReminderSentAt(null);
        taskRepository.save(task);
    }

    // resposta anulada n conta, entao a tarefa dela continua precisando de resposta
    private long validAnswersOf(ScaleTask task) {
        return responseRepository.countByTaskIdAndAnnulmentAnnulledAtIsNull(task.getId());
    }

    // a anamnese tem tela propria mas tbm eh uma tarefa (RF19)
    @Transactional
    public void completeTask(Long patientId, ScaleType scaleType) {
        openTaskOf(patientId, scaleType).ifPresent(task -> closeAsAnswered(task, LocalDate.now()));
    }

    // roda uma vez por dia: fecha as tarefas q passaram do prazo
    // quem tem ao menos uma resposta conta como respondida, e o resto
    // fica como nao respondida pra virar lacuna no grafico (RN10)
    @Transactional
    public int closeOverdue(LocalDate today) {
        List<ScaleTask> overdue = taskRepository.findByStatusAndPeriodEndBefore(ScaleTaskStatus.PENDENTE, today);
        for (ScaleTask task : overdue) {
            closeByAnswers(task, task.getPeriodEnd());
            // no diario o prescritor recebe um aviso por periodo e n um por dia (RF15)
            if (isDaily(task.getScaleType())) {
                notifyDailyPeriodClosed(task);
            }
        }
        return overdue.size();
    }

    // arquivar fecha na hora o q o paciente ainda tinha em aberto, pela mesma regra do prazo vencido
    // o periodo acaba no dia do arquivamento, senao o diario pela metade seguraria o acompanhamento novo
    @Transactional
    public int closePendingOf(Long patientId) {
        LocalDate today = LocalDate.now();
        List<ScaleTask> pending = taskRepository
                .findByPatientIdAndStatusOrderByPeriodEndAsc(patientId, ScaleTaskStatus.PENDENTE);
        for (ScaleTask task : pending) {
            if (task.getPeriodEnd().isAfter(today)) {
                task.setPeriodEnd(today);
            }
            closeByAnswers(task, today);
        }
        return pending.size();
    }

    private void closeByAnswers(ScaleTask task, LocalDate answeredDay) {
        long answers = validAnswersOf(task);
        task.setStatus(answers > 0 ? ScaleTaskStatus.RESPONDIDA : ScaleTaskStatus.NAO_RESPONDIDA);
        if (answers > 0 && task.getAnsweredAt() == null) {
            task.setAnsweredAt(answeredDay);
        }
        taskRepository.save(task);
    }

    // a anamnese n tem definicao no catalogo
    public boolean isDaily(ScaleType scaleType) {
        return catalog.hasDefinition(scaleType)
                && catalog.definitionOf(scaleType).fillMode() == ScaleDefinition.FillMode.DIARIO;
    }

    // conta os dias do periodo, pq dia atrasado de outra semana tbm fica ligado na tarefa
    private void notifyDailyPeriodClosed(ScaleTask task) {
        Patient patient = task.getPatient();
        long filledDays = responseRepository
                .findByPatientIdAndScaleTypeAndPeriodStartBetweenOrderByPeriodStartAsc(patient.getId(),
                        task.getScaleType(), task.getPeriodStart(), task.getPeriodEnd())
                .stream()
                .filter(response -> !response.isAnnulled())
                .count();
        if (filledDays == 0) {
            return;
        }
        long totalDays = ChronoUnit.DAYS.between(task.getPeriodStart(), task.getPeriodEnd()) + 1;
        notificationService.createNotification(task.getPrescriber(), "Escala diária concluída",
                task.getScaleType().getDisplayName() + " de " + formatDate(task.getPeriodStart()) + " a "
                        + formatDate(task.getPeriodEnd()) + ": " + patient.getName() + " preencheu "
                        + filledDays + " de " + totalDays + " dias.",
                "FORM", "/paciente/" + patient.getId() + "/historico");
    }

    // a central do paciente: o q esta em aberto e o q ja foi respondido
    @Transactional
    public PatientScalesPageDTO getPatientScalesPage(Long patientId) {
        auditService.recordChartView(patientId);
        Patient patient = findPatient(patientId);
        LocalDate today = LocalDate.now();

        List<ScaleTaskDTO> pending = taskRepository
                .findByPatientIdAndStatusOrderByPeriodEndAsc(patientId, ScaleTaskStatus.PENDENTE)
                .stream()
                .map(task -> new ScaleTaskDTO(task, validAnswersOf(task), today, isDaily(task.getScaleType())))
                .toList();

        List<ScaleResponseSummaryDTO> history = responseRepository
                .findByPatientIdOrderByPeriodStartDesc(patientId)
                .stream()
                .map(this::summaryOf)
                .toList();

        return new PatientScalesPageDTO(patient.getName(), pending, history);
    }

    @Transactional(readOnly = true)
    public List<ScaleTaskDTO> getTasksOfPatient(Long patientId) {
        LocalDate today = LocalDate.now();
        return taskRepository.findByPatientIdOrderByPeriodStartDesc(patientId)
                .stream()
                .map(task -> new ScaleTaskDTO(task, validAnswersOf(task), today, isDaily(task.getScaleType())))
                .toList();
    }

    // a avulsa q estava aberta na hora da rodada passa a ser a rodada, com o prazo da periodicidade
    @Transactional
    public void adoptAsProtocolRound(ScaleTask task, LocalDate roundEnd) {
        task.setFromProtocol(true);
        if (task.getPeriodEnd().isBefore(roundEnd)) {
            task.setPeriodEnd(roundEnd);
        }
        taskRepository.save(task);
    }

    // a ultima rodada do acompanhamento automatico, sem contar o envio avulso
    public Optional<ScaleTask> lastProtocolTaskOf(Long patientId, ScaleType scaleType) {
        return taskRepository.findFirstByPatientIdAndScaleTypeAndFromProtocolTrueOrderByPeriodStartDesc(
                patientId, scaleType);
    }

    private ScaleResponseSummaryDTO summaryOf(ScaleResponse response) {
        return new ScaleResponseSummaryDTO(response, catalog.resultTextOf(response.getScaleType(),
                response.getScore(), response.getScoreBand(), response.getAnswers()));
    }

    private void closeAsAnswered(ScaleTask task, LocalDate day) {
        task.setStatus(ScaleTaskStatus.RESPONDIDA);
        task.setAnsweredAt(day);
        taskRepository.save(task);
    }

    private Optional<ScaleTask> openTaskOf(Long patientId, ScaleType scaleType) {
        return taskRepository.findFirstByPatientIdAndScaleTypeAndStatusOrderByPeriodStartDesc(
                patientId, scaleType, ScaleTaskStatus.PENDENTE);
    }

    private Patient findPatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado com o id: " + patientId));
    }

    private String formatDate(LocalDate date) {
        return String.format("%02d/%02d/%d", date.getDayOfMonth(), date.getMonthValue(), date.getYear());
    }
}
