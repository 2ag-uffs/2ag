package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.AnnulmentDTO;
import dev.uffs.doisag.dto.ScaleResponseCreateDTO;
import dev.uffs.doisag.dto.ScaleResponseDTO;
import dev.uffs.doisag.dto.ScaleResponseSummaryDTO;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Annulment;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.ScaleResponse;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.ScaleResponseRepository;
import dev.uffs.doisag.scale.ScaleCatalog;
import dev.uffs.doisag.scale.ScaleDefinition;
import dev.uffs.doisag.scale.ScaleItem;
import dev.uffs.doisag.scale.ScaleOption;
import dev.uffs.doisag.scale.ScaleScorer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// as escalas respondidas, de qualquer escala (RF08, RF21 a RF26)
//
// o formulario vem do catalogo, entao aqui n tem nome de campo de
// escala nenhuma: o servico confere o q chegou contra a definicao,
// calcula o escore e grava
@Service
public class ScaleResponseService {

    public static final String UNKNOWN_ITEM_MESSAGE = "Essa resposta tem um item que não é dessa escala";
    public static final String COMPUTED_ITEM_MESSAGE = "Esse item é calculado pelo sistema";
    public static final String INVALID_VALUE_MESSAGE = "Resposta fora da escala do item";
    public static final String INVALID_TEXT_MESSAGE = "O texto pode ter até 2000 caracteres";
    public static final String FUTURE_PERIOD_MESSAGE = "Não dá para responder por um dia que ainda não chegou";
    public static final String INVALID_PERIOD_MESSAGE = "O fim do período precisa ser depois do início";
    public static final String PRESCRIBER_SCALE_MESSAGE =
            "O Mini-Exame do Estado Mental é aplicado pelo prescritor durante a consulta";
    public static final String REVIEWED_MESSAGE =
            "O prescritor já analisou esta resposta. Fale com ele para corrigir";
    public static final String ANNULLED_MESSAGE = "Resposta anulada não muda mais";
    public static final String ALREADY_ANNULLED_MESSAGE = "Esta resposta já está anulada";
    public static final String NOT_CONFIRMED_APPOINTMENT_MESSAGE =
            "Só consulta confirmada na agenda recebe o Mini-Exame";

    private static final int MAX_TEXT_LENGTH = 2000;
    // os acompanhamentos de dor e de TEA falam da ultima semana
    private static final int DEFAULT_PERIOD_DAYS = 7;

    private final ScaleResponseRepository responseRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final ScaleTaskService taskService;
    private final ScaleCatalog catalog;
    private final ScaleScorer scorer;
    private final AuditService auditService;
    private NotificationService notificationService;

    public ScaleResponseService(ScaleResponseRepository responseRepository,
                                PatientRepository patientRepository,
                                AppointmentRepository appointmentRepository,
                                ScaleTaskService taskService,
                                ScaleCatalog catalog,
                                ScaleScorer scorer,
                                AuditService auditService) {
        this.responseRepository = responseRepository;
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.taskService = taskService;
        this.catalog = catalog;
        this.scorer = scorer;
        this.auditService = auditService;
    }

    @Autowired
    public void setNotificationService(@Lazy NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // o paciente responde uma escala dele
    // no diario, responder de novo o mesmo dia corrige aquele dia em vez
    // de criar um segundo registro da mesma data
    @Transactional
    public ScaleResponseDTO answer(Long patientId, ScaleType scaleType, ScaleResponseCreateDTO answerData) {
        if (!scaleType.isFilledByPatient()) {
            throw new BusinessException(PRESCRIBER_SCALE_MESSAGE);
        }
        ScaleDefinition definition = catalog.definitionOf(scaleType);
        Patient patient = findPatient(patientId);
        LocalDate periodStart = periodStartOf(definition, answerData);
        LocalDate periodEnd = periodEndOf(definition, answerData, periodStart);

        ScaleResponse response = responseRepository
                .findByPatientIdAndScaleTypeAndPeriodStartAndAnnulmentAnnulledAtIsNull(patientId, scaleType,
                        periodStart)
                .orElseGet(ScaleResponse::new);
        boolean isNew = response.getId() == null;
        if (!isNew) {
            checkCanBeChanged(response);
        }

        response.setPatient(patient);
        response.setScaleType(scaleType);
        response.setPeriodStart(periodStart);
        response.setPeriodEnd(periodEnd);
        fillAnswersAndScore(response, definition, answerData.answers());

        ScaleResponse savedResponse = responseRepository.save(response);
        if (isNew) {
            taskService.linkResponse(savedResponse);
            auditService.recordCreation(scaleType.getAuditRecordType(), savedResponse.getId(), patientId);
            notifyPrescriber(savedResponse);
        } else {
            auditService.recordChange(scaleType.getAuditRecordType(), savedResponse.getId(), patientId);
        }
        return dtoOf(savedResponse);
    }

    // o MEEM eh aplicado pelo prescritor durante a consulta (RF26)
    @Transactional
    public ScaleResponseDTO applyMentalStateExam(Long appointmentId, ScaleResponseCreateDTO answerData,
                                                 Prescriber loggedPrescriber) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Consulta não encontrada com o id: " + appointmentId));
        if (!appointment.getStatus().isConfirmed() || appointment.isAnnulled()) {
            throw new BusinessException(NOT_CONFIRMED_APPOINTMENT_MESSAGE);
        }
        ScaleType scaleType = ScaleType.MINI_EXAME_ESTADO_MENTAL;
        ScaleDefinition definition = catalog.definitionOf(scaleType);
        LocalDate examDay = appointment.getDateTime().toLocalDate();

        ScaleResponse response = new ScaleResponse();
        response.setPatient(appointment.getPatient());
        response.setPrescriber(loggedPrescriber);
        response.setAppointment(appointment);
        response.setScaleType(scaleType);
        response.setPeriodStart(examDay);
        response.setPeriodEnd(examDay);
        fillAnswersAndScore(response, definition, answerData.answers());

        ScaleResponse savedResponse = responseRepository.save(response);
        auditService.recordCreation(scaleType.getAuditRecordType(), savedResponse.getId(),
                appointment.getPatient().getId());
        return dtoOf(savedResponse);
    }

    // correcao da resposta
    // o paciente corrige enquanto o prescritor n analisou, e depois
    // disso a correcao passa a ser anulacao com motivo
    @Transactional
    public ScaleResponseDTO update(Long responseId, ScaleResponseCreateDTO answerData) {
        ScaleResponse response = findResponse(responseId);
        // o MEEM eh do prescritor e corrigir ele eh anular e aplicar de novo
        if (!response.getScaleType().isFilledByPatient()) {
            throw new BusinessException(PRESCRIBER_SCALE_MESSAGE);
        }
        checkCanBeChanged(response);

        ScaleDefinition definition = catalog.definitionOf(response.getScaleType());
        fillAnswersAndScore(response, definition, answerData.answers());

        ScaleResponse savedResponse = responseRepository.save(response);
        auditService.recordChange(response.getScaleType().getAuditRecordType(), savedResponse.getId(),
                savedResponse.getPatient().getId());
        return dtoOf(savedResponse);
    }

    // o prescritor marca q ja olhou a resposta (trava a edicao do paciente)
    @Transactional
    public ScaleResponseDTO review(Long responseId, Users loggedUser) {
        ScaleResponse response = findResponse(responseId);
        if (response.isAnnulled()) {
            throw new BusinessException(ANNULLED_MESSAGE);
        }
        if (!response.isReviewed()) {
            response.markReviewed(loggedUser);
            responseRepository.save(response);
            auditService.recordChange(response.getScaleType().getAuditRecordType(), response.getId(),
                    response.getPatient().getId());
        }
        return dtoOf(response);
    }

    // resposta registrada por engano n eh apagada, fica anulada com motivo
    @Transactional
    public ScaleResponseDTO annul(Long responseId, AnnulmentDTO annulmentData, Users loggedUser) {
        ScaleResponse response = findResponse(responseId);
        if (response.isAnnulled()) {
            throw new BusinessException(ALREADY_ANNULLED_MESSAGE);
        }
        response.setAnnulment(new Annulment(loggedUser, annulmentData.reason()));
        responseRepository.save(response);
        // sem resposta valida a tarefa volta a ser cobrada, senao aquele periodo
        // fica sem ninguem preencher e vira buraco no grafico sem aviso nenhum
        taskService.reopenIfWithoutAnswers(response.getTask());
        auditService.recordChange(response.getScaleType().getAuditRecordType(), response.getId(),
                response.getPatient().getId());
        return dtoOf(response);
    }

    @Transactional(readOnly = true)
    public ScaleResponseDTO getById(Long responseId) {
        ScaleResponse response = findResponse(responseId);
        auditService.recordChartView(response.getPatient().getId());
        return dtoOf(response);
    }

    @Transactional(readOnly = true)
    public List<ScaleResponseSummaryDTO> getByPatientId(Long patientId) {
        auditService.recordChartView(patientId);
        return responseRepository.findByPatientIdOrderByPeriodStartDesc(patientId)
                .stream()
                .map(this::summaryOf)
                .toList();
    }

    // as respostas de uma escala so, q a grade da semana usa pra mostrar
    // os dias q ja foram preenchidos
    @Transactional(readOnly = true)
    public List<ScaleResponseDTO> getByPatientIdAndType(Long patientId, ScaleType scaleType) {
        auditService.recordChartView(patientId);
        return responseRepository.findByPatientIdAndScaleTypeOrderByPeriodStartDesc(patientId, scaleType)
                .stream()
                .map(this::dtoOf)
                .toList();
    }

    private void fillAnswersAndScore(ScaleResponse response, ScaleDefinition definition,
                                     Map<String, Object> rawAnswers) {
        Map<String, Object> answers = cleanAnswers(definition, rawAnswers);
        answers = scorer.withComputedAnswers(definition.type(), answers);
        ScaleScorer.ScoreResult result = scorer.score(definition.type(), answers);

        response.setAnswers(answers);
        response.setScore(result.score());
        response.setScoreBand(result.band());
    }

    // confere item por item contra a definicao da escala
    // item em branco simplesmente n entra, pq em branco n vale zero (RN10)
    private Map<String, Object> cleanAnswers(ScaleDefinition definition, Map<String, Object> rawAnswers) {
        Map<String, Object> answers = new LinkedHashMap<>();
        rawAnswers.forEach((key, value) -> {
            ScaleItem item = definition.itemOf(key)
                    .orElseThrow(() -> new BusinessException(UNKNOWN_ITEM_MESSAGE + ": " + key));
            Object cleanValue = cleanValue(item, value);
            if (cleanValue != null) {
                answers.put(key, cleanValue);
            }
        });
        return answers;
    }

    private Object cleanValue(ScaleItem item, Object value) {
        if (value == null) {
            return null;
        }
        return switch (item.type()) {
            case CALCULADO -> throw new BusinessException(COMPUTED_ITEM_MESSAGE + ": " + item.label());
            case NOTA -> wholeNumberInRange(item, value, item.minValue(), item.maxValue());
            case ESCOLHA -> optionValue(item, value);
            case NUMERO, MINUTOS -> wholeNumberInRange(item, value, 0, 10000);
            case HORAS -> decimalHours(item, value);
            case HORA -> time(item, value);
            case SIM_NAO -> value instanceof Boolean flag ? flag : invalid(item);
            case TEXTO -> text(item, value);
        };
    }

    private Object wholeNumberInRange(ScaleItem item, Object value, Integer minValue, Integer maxValue) {
        if (!(value instanceof Number number)) {
            return invalid(item);
        }
        int wholeNumber = number.intValue();
        if (wholeNumber < minValue || wholeNumber > maxValue) {
            return invalid(item);
        }
        return wholeNumber;
    }

    private Object optionValue(ScaleItem item, Object value) {
        if (!(value instanceof Number number)) {
            return invalid(item);
        }
        int chosen = number.intValue();
        boolean exists = item.options().stream().anyMatch(option -> option.value() == chosen);
        return exists ? chosen : invalid(item);
    }

    private Object decimalHours(ScaleItem item, Object value) {
        if (!(value instanceof Number number)) {
            return invalid(item);
        }
        double hours = number.doubleValue();
        if (hours < 0 || hours > 24) {
            return invalid(item);
        }
        return hours;
    }

    private Object time(ScaleItem item, Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        try {
            String shortText = text.length() > 5 ? text.substring(0, 5) : text;
            return LocalTime.parse(shortText).toString();
        } catch (DateTimeParseException error) {
            return invalid(item);
        }
    }

    private Object text(ScaleItem item, Object value) {
        if (!(value instanceof String text)) {
            return invalid(item);
        }
        String cleanText = text.trim();
        if (cleanText.isEmpty()) {
            return null;
        }
        if (cleanText.length() > MAX_TEXT_LENGTH) {
            throw new BusinessException(INVALID_TEXT_MESSAGE + ": " + item.label());
        }
        return cleanText;
    }

    private Object invalid(ScaleItem item) {
        String expected = item.options().isEmpty()
                ? ""
                : " Valores aceitos: " + item.options().stream().map(ScaleOption::label).toList();
        throw new BusinessException(INVALID_VALUE_MESSAGE + ": " + item.label() + "." + expected);
    }

    private LocalDate periodStartOf(ScaleDefinition definition, ScaleResponseCreateDTO answerData) {
        if (answerData.periodStart() != null) {
            return answerData.periodStart();
        }
        return definition.fillMode() == ScaleDefinition.FillMode.PERIODO
                ? LocalDate.now().minusDays(DEFAULT_PERIOD_DAYS - 1L)
                : LocalDate.now();
    }

    private LocalDate periodEndOf(ScaleDefinition definition, ScaleResponseCreateDTO answerData,
                                  LocalDate periodStart) {
        LocalDate periodEnd = answerData.periodEnd() != null
                ? answerData.periodEnd()
                : endOfDefaultPeriod(definition, periodStart);
        if (periodEnd.isBefore(periodStart)) {
            throw new BusinessException(INVALID_PERIOD_MESSAGE);
        }
        if (periodStart.isAfter(LocalDate.now())) {
            throw new BusinessException(FUTURE_PERIOD_MESSAGE);
        }
        return periodEnd;
    }

    private LocalDate endOfDefaultPeriod(ScaleDefinition definition, LocalDate periodStart) {
        return definition.fillMode() == ScaleDefinition.FillMode.PERIODO
                ? periodStart.plusDays(DEFAULT_PERIOD_DAYS - 1L)
                : periodStart;
    }

    private void checkCanBeChanged(ScaleResponse response) {
        if (response.isAnnulled()) {
            throw new BusinessException(ANNULLED_MESSAGE);
        }
        if (response.isReviewed()) {
            throw new BusinessException(REVIEWED_MESSAGE);
        }
    }

    // RF15 a conclusao de uma escala avisa o prescritor
    private void notifyPrescriber(ScaleResponse response) {
        Prescriber prescriber = response.getPatient().getPrescriber();
        if (prescriber == null) {
            return;
        }
        notificationService.createNotification(prescriber, "Escala respondida",
                response.getPatient().getName() + " respondeu " + response.getScaleType().getDisplayName() + ".",
                "FORM", "/paciente/" + response.getPatient().getId() + "/historico");
    }

    private ScaleResponseDTO dtoOf(ScaleResponse response) {
        return new ScaleResponseDTO(response, resultTextOf(response));
    }

    private ScaleResponseSummaryDTO summaryOf(ScaleResponse response) {
        return new ScaleResponseSummaryDTO(response, resultTextOf(response));
    }

    private String resultTextOf(ScaleResponse response) {
        return catalog.resultTextOf(response.getScaleType(), response.getScore(), response.getScoreBand(),
                response.getAnswers());
    }

    private ScaleResponse findResponse(Long responseId) {
        return responseRepository.findById(responseId)
                .orElseThrow(() -> new NotFoundException("Resposta de escala não encontrada com o id: " + responseId));
    }

    private Patient findPatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado com o id: " + patientId));
    }
}
