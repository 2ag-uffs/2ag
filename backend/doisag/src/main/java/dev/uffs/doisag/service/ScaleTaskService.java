package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.PatientScalesPageDTO;
import dev.uffs.doisag.dto.ScaleResponseSummaryDTO;
import dev.uffs.doisag.dto.ScaleTaskDTO;
import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.infra.RowLock;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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

    private static final Logger log = LoggerFactory.getLogger(ScaleTaskService.class);

    public static final String PRESCRIBER_SCALE_MESSAGE =
            "O Mini-Exame do Estado Mental é aplicado pelo prescritor durante a consulta";
    public static final String NO_PRESCRIBER_MESSAGE = "Este paciente ainda não tem prescritor vinculado";
    public static final String ARCHIVED_ANAMNESIS_MESSAGE =
            "Paciente arquivado não preenche a avaliação inicial. Reative o paciente antes de enviar";
    public static final String ANSWERED_TODAY_MESSAGE =
            "Esta escala já foi respondida hoje. Veja a resposta no histórico do paciente";

    // quanto tempo o paciente tem pra responder uma escala avulsa
    private static final int DEFAULT_TASK_DAYS = 7;
    // a tarefa reaberta pela anulacao vale pelo menos ate depois de amanha: o job de amanha n fecha ela
    // e o lembrete, q sai faltando dois dias pro prazo, ainda cobra
    private static final int REOPENED_TASK_DAYS = 2;

    private final ScaleTaskRepository taskRepository;
    private final ScaleResponseRepository responseRepository;
    private final PatientRepository patientRepository;
    private final PrescriberRepository prescriberRepository;
    private final ScaleCatalog catalog;
    private final AuditService auditService;
    private final RowLock rowLock;
    private final TransactionTemplate transactionTemplate;
    private NotificationService notificationService;

    public ScaleTaskService(ScaleTaskRepository taskRepository,
                            ScaleResponseRepository responseRepository,
                            PatientRepository patientRepository,
                            PrescriberRepository prescriberRepository,
                            ScaleCatalog catalog,
                            AuditService auditService,
                            RowLock rowLock,
                            PlatformTransactionManager transactionManager) {
        this.taskRepository = taskRepository;
        this.responseRepository = responseRepository;
        this.patientRepository = patientRepository;
        this.prescriberRepository = prescriberRepository;
        this.catalog = catalog;
        this.auditService = auditService;
        this.rowLock = rowLock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
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
    // a versao com data n trava: o acompanhamento ja travou o prescritor e dps o paciente antes de chamar ela,
    // e o job faz isso um acompanhamento por transacao, entao segura um prescritor por vez e n todos
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
        // o arquivado n consegue mandar a ficha, entao a tarefa ia ficar cobrando algo impossivel
        if (scaleType == ScaleType.ANAMNESE && patient.isArchived()) {
            throw new BusinessException(ARCHIVED_ANAMNESIS_MESSAGE);
        }

        // a mesma escala ainda aberta n vira duas tarefas iguais
        Optional<ScaleTask> openTask = openTaskOf(patientId, scaleType);
        if (openTask.isPresent() && openTask.get().coversDay(today)) {
            return openTask.get();
        }
        // a rodada q chega no dia em q a escala ja foi respondida fica com essa resposta: a resposta
        // do dia eh uma so, entao responder a tarefa nova so corrigia a primeira e ela seguia pendente
        if (fromProtocol && !isDaily(scaleType)) {
            Optional<ScaleTask> answeredToday = taskAnsweredOn(patientId, scaleType, today);
            if (answeredToday.isPresent()) {
                return answeredToday.get();
            }
        }
        // a avulsa no dia da resposta ficava pendente sem jeito de fechar: o paciente responder de novo
        // no mesmo dia so corrigia a resposta q ja existia
        if (!fromProtocol && answeredOn(patientId, scaleType, today)) {
            throw new BusinessException(ANSWERED_TODAY_MESSAGE);
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
        if (isDaily(scaleType)) {
            linkDaysAlreadyFilled(savedTask);
        }

        notificationService.createNotification(patient, "Nova escala para responder",
                scaleType.getDisplayName() + ". Você tem até " + formatDate(savedTask.getPeriodEnd())
                        + " para responder.",
                "FORM", "/pacientes/" + patientId + "/escalas");
        return savedTask;
    }

    // liga a resposta na tarefa aberta daquela escala
    //
    // no diario a tarefa segue aberta ate o fim do periodo, pq o paciente
    // preenche um dia de cada vez (ou os dias q esqueceu de uma vez so)
    @Transactional
    public void linkResponse(ScaleResponse response) {
        LocalDate today = LocalDate.now();
        boolean daily = isDaily(response.getScaleType());
        Optional<ScaleTask> openTask = taskForAnswer(response);
        if (openTask.isEmpty()) {
            return;
        }
        // escala pontuada pela metade n fica com a tarefa: ela segue pendente, o lembrete cobra
        // e no prazo vira nao respondida (RN10)
        if (catalog.definitionOf(response.getScaleType()).isIncomplete(response.getScore())) {
            return;
        }
        ScaleTask task = openTask.get();
        response.setTask(task);
        if (!daily) {
            closeAsAnswered(task, today);
        }
    }

    // a correcao q completa a escala fecha a tarefa q ja existia no dia em q a resposta pela metade
    // foi enviada, e n uma tarefa nova q comecou dps
    @Transactional
    public void linkCompletedResponse(ScaleResponse response) {
        Optional<ScaleTask> openTask = taskForAnswer(response);
        LocalDate sentDay = response.getCreatedAt().toLocalDate();
        if (response.getTask() != null || openTask.isEmpty() || openTask.get().getPeriodStart().isAfter(sentDay)) {
            return;
        }
        linkResponse(response);
    }

    // a tarefa pendente q recebe a resposta
    // no diario eh a q cobre o dia preenchido: antes do job das 8h o dia de ontem ainda eh da tarefa
    // anterior, e dia de outro periodo n conta na atual. nas outras eh a q cobre o dia de q a resposta
    // fala (a data da avaliacao, ou o fim da semana respondida, q na tela eh hoje: o inicio cai em hoje-6,
    // antes do inicio da tarefa). sem nenhuma, vale a pendente mais nova q ja comecou, q eh a reaberta
    // pela anulacao ou a q venceu e o job das 8h ainda n fechou. senao a resposta nova nunca contava
    private Optional<ScaleTask> taskForAnswer(ScaleResponse response) {
        Long patientId = response.getPatient().getId();
        ScaleType scaleType = response.getScaleType();
        if (isDaily(scaleType)) {
            return pendingTaskOfDay(patientId, scaleType, response.getPeriodStart());
        }
        Optional<ScaleTask> taskOfDay = pendingTaskOfDay(patientId, scaleType, response.getPeriodEnd());
        if (taskOfDay.isPresent()) {
            return taskOfDay;
        }
        LocalDate today = LocalDate.now();
        List<ScaleTask> pending = taskRepository.findByPatientIdAndScaleTypeAndStatusOrderByPeriodStartDesc(
                patientId, scaleType, ScaleTaskStatus.PENDENTE);
        for (ScaleTask task : pending) {
            if (!task.getPeriodStart().isAfter(today)) {
                return Optional.of(task);
            }
        }
        return Optional.empty();
    }

    // a tarefa daquela escala q foi respondida no dia e ainda vale nele
    private Optional<ScaleTask> taskAnsweredOn(Long patientId, ScaleType scaleType, LocalDate day) {
        List<ScaleTask> answered = taskRepository.findByPatientIdAndScaleTypeAndStatusOrderByPeriodStartDesc(
                patientId, scaleType, ScaleTaskStatus.RESPONDIDA);
        for (ScaleTask task : answered) {
            if (day.equals(task.getAnsweredAt()) && task.coversDay(day)) {
                return Optional.of(task);
            }
        }
        return Optional.empty();
    }

    // a resposta valida daquele dia, q eh a data q a tela sugere: o envio do paciente com ela so corrigiria
    // essa resposta. na pontual eh a avaliacao q comeca no dia, e na de periodo eh a semana q termina nele,
    // pq a tela e o servidor mandam o inicio 6 dias antes. no diario cada dia eh um registro e a tarefa
    // cobre a semana toda, entao n entra
    private boolean answeredOn(Long patientId, ScaleType scaleType, LocalDate day) {
        if (isDaily(scaleType)) {
            return false;
        }
        if (isPeriod(scaleType)) {
            return responseRepository.existsByPatientIdAndScaleTypeAndPeriodEndAndAnnulmentAnnulledAtIsNull(
                    patientId, scaleType, day);
        }
        return responseRepository.findByPatientIdAndScaleTypeAndPeriodStartAndAnnulmentAnnulledAtIsNull(
                patientId, scaleType, day).isPresent();
    }

    // resposta anulada deixa de contar: sem nenhuma valida a tarefa volta a pendente
    // devolve se ela estava fechada e foi reaberta
    @Transactional
    public boolean reopenIfWithoutAnswers(ScaleTask task) {
        if (task == null || validAnswersOf(task) > 0) {
            return false;
        }
        boolean wasClosed = task.getStatus() != ScaleTaskStatus.PENDENTE;
        task.setStatus(ScaleTaskStatus.PENDENTE);
        task.setAnsweredAt(null);
        task.setReminderSentAt(null);
        taskRepository.save(task);
        return wasClosed;
    }

    // a anulacao q reabre a tarefa avisa o paciente pra responder de novo, com o motivo
    // a anulacao costuma vir dps do prazo, e o job das 8h fechava a tarefa como nao respondida no dia
    // seguinte, antes de o lembrete sair. entao o prazo q acaba antes de depois de amanha passa pra
    // depois de amanha. o inicio fica, e a resposta nova da data anulada continua caindo nela
    // o aviso abre a tela ja na data anulada (?data=): a tela sugere hoje, e com a rodada seguinte ja
    // pendente a resposta de hoje fechava a rodada nova e a reaberta ficava pendente ate virar nao respondida
    // no diario o periodo eh a semana da grade e n muda, senao os dias da semana seguinte caiam nas duas,
    // entao o job das 8h de amanha fecha a semana reaberta e o aviso diz isso
    @Transactional
    public void reopenAfterAnnulment(ScaleResponse annulledResponse) {
        ScaleTask task = annulledResponse.getTask();
        if (!reopenIfWithoutAnswers(task)) {
            return;
        }
        ScaleType scaleType = task.getScaleType();
        LocalDate annulledDay = annulledResponse.getPeriodStart();
        LocalDate today = LocalDate.now();
        String whatToDo;
        if (isDaily(scaleType)) {
            whatToDo = "Preencha o dia " + formatDate(annulledDay) + " de novo até amanhã de manhã.";
        } else {
            LocalDate lastDay = today.plusDays(REOPENED_TASK_DAYS);
            if (task.getPeriodEnd().isBefore(lastDay)) {
                task.setPeriodEnd(lastDay);
                taskRepository.save(task);
            }
            whatToDo = "Você tem até " + formatDate(task.getPeriodEnd()) + " para responder de novo.";
            // com outra pendente da mesma escala cobrindo hoje, a data de hoje eh dela e n da reaberta
            if (anotherPendingCoversToday(task, today)) {
                whatToDo = whatToDo + " Responda com " + periodTextOf(annulledResponse)
                        + ", como na resposta anulada: a data de hoje vale pela pendência mais nova desta escala.";
            }
        }
        Patient patient = task.getPatient();
        notificationService.createNotification(patient, "Escala para responder de novo",
                scaleType.getDisplayName() + ": sua resposta foi anulada pelo prescritor. " + whatToDo
                        + " Motivo: " + annulledResponse.getAnnulment().getAnnulmentReason(),
                "FORM", scaleType.getPath() + "?data=" + annulledDay);
    }

    // a pendente q cobre hoje eh outra q n a reaberta (a rodada seguinte, ou uma avulsa enviada depois)
    private boolean anotherPendingCoversToday(ScaleTask reopened, LocalDate today) {
        Optional<ScaleTask> taskOfToday = pendingTaskOfDay(reopened.getPatient().getId(), reopened.getScaleType(),
                today);
        return taskOfToday.isPresent() && !taskOfToday.get().getId().equals(reopened.getId());
    }

    // a data q a tela pede: na de periodo eh o inicio e o fim, nas outras a data da avaliacao
    private String periodTextOf(ScaleResponse response) {
        if (isPeriod(response.getScaleType())) {
            return "o período de " + formatDate(response.getPeriodStart()) + " a "
                    + formatDate(response.getPeriodEnd());
        }
        return "a data " + formatDate(response.getPeriodStart());
    }

    // RF15 a conclusao de uma escala avisa o prescritor, e a avaliacao inicial tbm
    // o diario avisa no fechamento do periodo, no closeOverdue
    public void notifyPrescriberOfAnswer(Patient patient, ScaleType scaleType) {
        Prescriber prescriber = patient.getPrescriber();
        if (prescriber == null) {
            return;
        }
        notificationService.createNotification(prescriber, "Escala respondida",
                patient.getDisplayName() + " respondeu " + scaleType.getDisplayName() + ".",
                "FORM", "/paciente/" + patient.getId() + "/historico");
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
    // cada tarefa na propria transacao, com o paciente travado e dps a tarefa lida de novo, na ordem da resposta
    // e do arquivamento (a vencida aceita resposta ate o job das 8h): a resposta gravada antes de o job ler a
    // tarefa conta, e a q fica esperando o job encontra a tarefa fechada. o job nunca grava nao respondida por
    // cima de uma resposta ja gravada. uma tarefa com problema n segura as outras (#110)
    public int closeOverdue(LocalDate today) {
        int closed = 0;
        int failures = 0;
        for (ScaleTask found : taskRepository.findByStatusAndPeriodEndBefore(ScaleTaskStatus.PENDENTE, today)) {
            try {
                Boolean wasClosed = transactionTemplate.execute(status -> closeIfStillOverdue(found.getId(), today));
                if (Boolean.TRUE.equals(wasClosed)) {
                    closed++;
                }
            } catch (RuntimeException exception) {
                failures++;
                log.error("Tarefas do dia: falha ao fechar a tarefa {}", found.getId(), exception);
            }
        }
        // o dia n conta como feito e a proxima rodada tenta de novo, sem repetir o q ja saiu
        if (failures > 0) {
            throw new IllegalStateException(failures + " tarefa(s) vencida(s) com falha, "
                    + closed + " fechadas nas outras");
        }
        return closed;
    }

    private boolean closeIfStillOverdue(Long taskId, LocalDate today) {
        Long patientId = taskRepository.findPatientIdById(taskId).orElse(null);
        if (patientId == null) {
            return false;
        }
        patientRepository.lockById(patientId);
        ScaleTask task = rowLock.reload(ScaleTask.class, taskId).orElse(null);
        if (task == null || task.getStatus() != ScaleTaskStatus.PENDENTE || !task.getPeriodEnd().isBefore(today)) {
            return false;
        }
        closeByAnswers(task, task.getPeriodEnd());
        // no diario o prescritor recebe um aviso por periodo e n um por dia (RF15)
        if (isDaily(task.getScaleType())) {
            notifyDailyPeriodClosed(task);
        }
        return true;
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

    private boolean isPeriod(ScaleType scaleType) {
        return catalog.hasDefinition(scaleType)
                && catalog.definitionOf(scaleType).fillMode() == ScaleDefinition.FillMode.PERIODO;
    }

    // conta pela data os dias q caem no periodo, inclusive os preenchidos atrasados antes do fim
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
                        + formatDate(task.getPeriodEnd()) + ": " + patient.getDisplayName() + " preencheu "
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

        return new PatientScalesPageDTO(patient.getDisplayName(), pending, history);
    }

    @Transactional(readOnly = true)
    public List<ScaleTaskDTO> getTasksOfPatient(Long patientId) {
        LocalDate today = LocalDate.now();
        return taskRepository.findByPatientIdOrderByPeriodStartDesc(patientId)
                .stream()
                .map(task -> new ScaleTaskDTO(task, validAnswersOf(task), today, isDaily(task.getScaleType())))
                .toList();
    }

    // a tarefa q ja existia na hora da rodada passa a ser a rodada, com o prazo dela: a avulsa aberta
    // ou respondida naquele dia, e a do acompanhamento anterior. o prazo da tarefa acaba junto com a
    // rodada, pra mais ou pra menos, senao sobrava dia sem tarefa ate a proxima. o prazo novo ganha
    // lembrete de novo, senao o q ja saiu pro prazo antigo calava o do novo
    @Transactional
    public void adoptAsProtocolRound(ScaleTask task, LocalDate roundEnd) {
        task.setFromProtocol(true);
        if (!task.getPeriodEnd().equals(roundEnd)) {
            task.setPeriodEnd(roundEnd);
            task.setReminderSentAt(null);
        }
        taskRepository.save(task);
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

    // a tarefa pendente q cobre o dia, olhando todas e n so a mais nova
    private Optional<ScaleTask> pendingTaskOfDay(Long patientId, ScaleType scaleType, LocalDate day) {
        List<ScaleTask> pending = taskRepository.findByPatientIdAndScaleTypeAndStatusOrderByPeriodStartDesc(
                patientId, scaleType, ScaleTaskStatus.PENDENTE);
        for (ScaleTask task : pending) {
            if (task.coversDay(day)) {
                return Optional.of(task);
            }
        }
        return Optional.empty();
    }

    // dia preenchido entre a meia noite e o job das 8h ainda n tinha tarefa cobrindo ele,
    // entao a tarefa nova pega os dias dela q ficaram soltos. ela segue pendente ate o fim do periodo
    private void linkDaysAlreadyFilled(ScaleTask task) {
        List<ScaleResponse> days = responseRepository
                .findByPatientIdAndScaleTypeAndPeriodStartBetweenOrderByPeriodStartAsc(task.getPatient().getId(),
                        task.getScaleType(), task.getPeriodStart(), task.getPeriodEnd());
        for (ScaleResponse response : days) {
            if (response.getTask() == null && !response.isAnnulled()) {
                response.setTask(task);
                responseRepository.save(response);
            }
        }
    }

    private Patient findPatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado com o id: " + patientId));
    }

    private String formatDate(LocalDate date) {
        return String.format("%02d/%02d/%d", date.getDayOfMonth(), date.getMonthValue(), date.getYear());
    }
}
