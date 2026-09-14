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
import dev.uffs.doisag.repository.ScaleResponseRepository;
import dev.uffs.doisag.repository.ScaleTaskRepository;
import dev.uffs.doisag.scale.ScaleCatalog;
import dev.uffs.doisag.scale.ScaleDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
    private final ScaleCatalog catalog;
    private final AuditService auditService;
    private NotificationService notificationService;

    public ScaleTaskService(ScaleTaskRepository taskRepository,
                            ScaleResponseRepository responseRepository,
                            PatientRepository patientRepository,
                            ScaleCatalog catalog,
                            AuditService auditService) {
        this.taskRepository = taskRepository;
        this.responseRepository = responseRepository;
        this.patientRepository = patientRepository;
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
    @Transactional
    public ScaleTask assign(Long patientId, ScaleType scaleType) {
        return assign(patientId, scaleType, LocalDate.now(), DEFAULT_TASK_DAYS);
    }

    // a versao com data e prazo serve pro acompanhamento automatico: o
    // job manda o dia q ele esta processando e o prazo da periodicidade
    @Transactional
    public ScaleTask assign(Long patientId, ScaleType scaleType, LocalDate today, int taskDays) {
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
        if (openTask.isEmpty() || !openTask.get().coversDay(response.getPeriodStart())) {
            return;
        }
        ScaleTask task = openTask.get();
        response.setTask(task);
        if (catalog.definitionOf(response.getScaleType()).fillMode() != ScaleDefinition.FillMode.DIARIO) {
            closeAsAnswered(task, response.getPeriodStart());
        }
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
            long answers = responseRepository.countByTaskId(task.getId());
            task.setStatus(answers > 0 ? ScaleTaskStatus.RESPONDIDA : ScaleTaskStatus.NAO_RESPONDIDA);
            if (answers > 0 && task.getAnsweredAt() == null) {
                task.setAnsweredAt(task.getPeriodEnd());
            }
            taskRepository.save(task);
        }
        return overdue.size();
    }

    // a central do paciente: o q esta em aberto e o q ja foi respondido
    @Transactional(readOnly = true)
    public PatientScalesPageDTO getPatientScalesPage(Long patientId) {
        auditService.recordChartView(patientId);
        Patient patient = findPatient(patientId);
        LocalDate today = LocalDate.now();

        List<ScaleTaskDTO> pending = taskRepository
                .findByPatientIdAndStatusOrderByPeriodEndAsc(patientId, ScaleTaskStatus.PENDENTE)
                .stream()
                .map(task -> new ScaleTaskDTO(task, responseRepository.countByTaskId(task.getId()), today))
                .toList();

        List<ScaleResponseSummaryDTO> history = responseRepository
                .findByPatientIdOrderByPeriodStartDesc(patientId)
                .stream()
                .map(this::summaryOf)
                .toList();

        return new PatientScalesPageDTO(patient.getName(), pending, history);
    }

    // as pendencias q o prescritor ve no painel dele
    @Transactional(readOnly = true)
    public List<ScaleTaskDTO> getPendingOfPrescriber(Long prescriberId) {
        LocalDate today = LocalDate.now();
        return taskRepository.findByPrescriberIdAndStatusOrderByPeriodEndAsc(prescriberId, ScaleTaskStatus.PENDENTE)
                .stream()
                .map(task -> new ScaleTaskDTO(task, responseRepository.countByTaskId(task.getId()), today))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ScaleTaskDTO> getTasksOfPatient(Long patientId) {
        LocalDate today = LocalDate.now();
        return taskRepository.findByPatientIdOrderByPeriodStartDesc(patientId)
                .stream()
                .map(task -> new ScaleTaskDTO(task, responseRepository.countByTaskId(task.getId()), today))
                .toList();
    }

    public Optional<ScaleTask> lastTaskOf(Long patientId, ScaleType scaleType) {
        return taskRepository.findFirstByPatientIdAndScaleTypeOrderByPeriodStartDesc(patientId, scaleType);
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
