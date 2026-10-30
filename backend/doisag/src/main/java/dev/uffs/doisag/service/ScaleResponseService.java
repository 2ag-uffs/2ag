package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.AnnulmentDTO;
import dev.uffs.doisag.dto.ScaleResponseCreateDTO;
import dev.uffs.doisag.dto.ScaleResponseDTO;
import dev.uffs.doisag.dto.ScaleResponseSummaryDTO;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.DateCheck;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.infra.RowLock;
import dev.uffs.doisag.model.Annulment;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.ScaleResponse;
import dev.uffs.doisag.model.ScaleTask;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.ScaleResponseRepository;
import dev.uffs.doisag.scale.ScaleCatalog;
import dev.uffs.doisag.scale.ScaleDefinition;
import dev.uffs.doisag.scale.ScaleItem;
import dev.uffs.doisag.scale.ScaleOption;
import dev.uffs.doisag.scale.ScaleScorer;
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
    public static final String EXAM_ALREADY_APPLIED_MESSAGE =
            "Esta consulta já tem um Mini-Exame aplicado. Para aplicar de novo, anule o anterior com o motivo";
    public static final String NOT_CONFIRMED_APPOINTMENT_MESSAGE =
            "Só consulta confirmada na agenda recebe o Mini-Exame";
    public static final String INCOMPLETE_EXAM_MESSAGE = "Falta preencher no Mini-Exame";
    public static final String FUTURE_APPOINTMENT_MESSAGE = "Consulta que ainda não aconteceu não recebe o Mini-Exame";
    public static final String DIARY_DAY_MESSAGE =
            "No diário a data é o dia escolhido na grade e não muda na correção";
    public static final String PERIOD_START_TAKEN_MESSAGE = "Já existe uma resposta dessa escala começando nesse dia";
    public static final String EMPTY_ANSWERS_MESSAGE = "Responda ao menos um item da escala";

    private static final int MAX_TEXT_LENGTH = 2000;
    // teto das contagens e dos minutos so p/ barrar numero absurdo
    private static final int MAX_COUNT_VALUE = 10000;
    private static final int HOURS_IN_DAY = 24;
    // o horario chega como hh:mm ou hh:mm:ss e os segundos caem fora
    private static final int HOUR_MINUTE_LENGTH = 5;
    // os acompanhamentos de dor e de TEA falam da ultima semana
    private static final int DEFAULT_PERIOD_DAYS = 7;

    private final ScaleResponseRepository responseRepository;
    private final PatientRepository patientRepository;
    private final PrescriberRepository prescriberRepository;
    private final ScaleTaskService taskService;
    private final ConsultationService consultationService;
    private final ScaleCatalog catalog;
    private final ScaleScorer scorer;
    private final AuditService auditService;
    private final RowLock rowLock;

    public ScaleResponseService(ScaleResponseRepository responseRepository,
                                PatientRepository patientRepository,
                                PrescriberRepository prescriberRepository,
                                ScaleTaskService taskService,
                                ConsultationService consultationService,
                                ScaleCatalog catalog,
                                ScaleScorer scorer,
                                AuditService auditService,
                                RowLock rowLock) {
        this.responseRepository = responseRepository;
        this.patientRepository = patientRepository;
        this.prescriberRepository = prescriberRepository;
        this.taskService = taskService;
        this.consultationService = consultationService;
        this.catalog = catalog;
        this.scorer = scorer;
        this.auditService = auditService;
        this.rowLock = rowLock;
    }

    // o paciente responde uma escala dele
    // no diario, responder de novo o mesmo dia corrige aquele dia em vez
    // de criar um segundo registro da mesma data
    @Transactional
    public AnswerResult answer(Long patientId, ScaleType scaleType, ScaleResponseCreateDTO answerData) {
        if (!scaleType.isFilledByPatient()) {
            throw new BusinessException(PRESCRIBER_SCALE_MESSAGE);
        }
        ScaleDefinition definition = catalog.definitionOf(scaleType);
        // a linha do paciente fica travada ate o fim: dois envios do mesmo dia ao mesmo tempo
        // (duplo toque, retry da rede) passavam os dois pela busca vazia e viravam duas linhas
        // do mesmo dia, e dai toda leitura daquele dia quebrava. agora o segundo espera o
        // primeiro gravar e acha a resposta dele, virando a correcao do dia
        patientRepository.lockById(patientId);
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
        boolean wasComplete = !isNew && !definition.isIncomplete(response.getScore());

        response.setPatient(patient);
        response.setScaleType(scaleType);
        response.setPeriodStart(periodStart);
        response.setPeriodEnd(periodEnd);
        fillAnswersAndScore(response, definition, answerData.answers());

        ScaleResponse savedResponse = responseRepository.save(response);
        boolean isComplete = !definition.isIncomplete(savedResponse.getScore());
        if (isNew) {
            taskService.linkResponse(savedResponse);
            auditService.recordCreation(scaleType.getAuditRecordType(), savedResponse.getId(), patientId);
        } else {
            linkOrUnlinkAfterCorrection(savedResponse, wasComplete, isComplete);
            auditService.recordChange(scaleType.getAuditRecordType(), savedResponse.getId(), patientId);
        }
        // o aviso sai qnd a escala fica completa, e o diario avisa uma vez so no fim do periodo
        // resposta nova sempre tem wasComplete falso
        boolean becameComplete = isComplete && !wasComplete;
        if (becameComplete && definition.fillMode() != ScaleDefinition.FillMode.DIARIO) {
            taskService.notifyPrescriberOfAnswer(savedResponse.getPatient(), savedResponse.getScaleType());
        }
        return new AnswerResult(dtoOf(savedResponse), isNew);
    }

    // a correcao do dia reaproveita o registro, entao a rota precisa saber
    // se criou ou alterou pra responder 201 ou 200
    public record AnswerResult(ScaleResponseDTO response, boolean created) {}

    // o MEEM eh aplicado pelo prescritor durante a consulta (RF26)
    @Transactional
    public ScaleResponseDTO applyMentalStateExam(Long appointmentId, ScaleResponseCreateDTO answerData,
                                                 Prescriber loggedPrescriber) {
        // a consulta fica travada ate o fim, senao dois cliques em aplicar passavam os dois
        // pela conferencia de exame repetido e gravavam dois exames validos no mesmo atendimento
        Appointment appointment = rowLock.reload(Appointment.class, appointmentId)
                .orElseThrow(() -> NotFoundException.appointment(appointmentId));
        checkAppointmentCanReceiveExam(appointment);
        boolean concludedNow = concludeAppointment(appointment);
        // dps da consulta vem o prescritor e so dps o paciente: no postgres o insert do exame toma o paciente e
        // dps o prescritor em modo compartilhado, e o cadastro do acompanhamento segura o prescritor e espera o
        // paciente. com o prescritor travado antes os dois n ficam esperando um pelo outro
        prescriberRepository.lockById(loggedPrescriber.getId());
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
        checkCompleteExam(definition, response.getAnswers());

        ScaleResponse savedResponse = responseRepository.save(response);
        Long patientId = appointment.getPatient().getId();
        auditService.recordCreation(scaleType.getAuditRecordType(), savedResponse.getId(), patientId);
        // a trilha encosta no paciente e na conta, entao a da consulta concluida vem dps da trava do prescritor
        if (concludedNow) {
            auditService.recordChange(AuditRecordType.CONSULTA, appointment.getId(), patientId);
        }
        return dtoOf(savedResponse);
    }

    // o meem na consulta passada q ficou agendada mostra q ela aconteceu, entao ela fica concluida igual
    // na receita: a proxima sai como retorno e o grafico marca o dia. o registro clinico fica vazio ate o
    // prescritor preencher. a consulta ja esta travada e o jpa grava a mudanca no fim
    private boolean concludeAppointment(Appointment appointment) {
        if (appointment.getStatus() == AppointmentStatus.CONCLUIDA) {
            return false;
        }
        appointment.setStatus(AppointmentStatus.CONCLUIDA);
        return true;
    }

    private void checkAppointmentCanReceiveExam(Appointment appointment) {
        if (!appointment.getStatus().isConfirmed() || appointment.isAnnulled()) {
            throw new BusinessException(NOT_CONFIRMED_APPOINTMENT_MESSAGE);
        }
        DateCheck.checkAlreadyHappened(appointment.getDateTime(), FUTURE_APPOINTMENT_MESSAGE);
        // dois exame valido no mesmo atendimento ninguem sabe qual vale, e o meem
        // n tem correcao: pra refazer, o anterior precisa ser anulado com motivo
        if (responseRepository.existsByAppointmentIdAndAnnulmentAnnulledAtIsNull(appointment.getId())) {
            throw new BusinessException(EXAM_ALREADY_APPLIED_MESSAGE);
        }
    }

    // excecao a RN10 pq o meem n tem correcao e incompleto fica sem escore ou sem faixa (RN14)
    private void checkCompleteExam(ScaleDefinition definition, Map<String, Object> answers) {
        List<String> missingItems = definition.items().stream()
                .filter(item -> !answers.containsKey(item.key()))
                .map(ScaleItem::label)
                .toList();
        if (!missingItems.isEmpty()) {
            throw new BusinessException(INCOMPLETE_EXAM_MESSAGE + ": " + String.join(", ", missingItems));
        }
    }

    // o exame ja aplicado naquela consulta, pra tela abrir no resultado
    // em vez de um formulario em branco q n vai poder ser salvo
    @Transactional(readOnly = true)
    public ScaleResponseDTO getMentalStateExamOf(Long appointmentId) {
        return responseRepository.findByAppointmentIdOrderByPeriodStartAsc(appointmentId)
                .stream()
                .filter(exam -> !exam.isAnnulled())
                .findFirst()
                .map(this::dtoOf)
                .orElse(null);
    }

    // correcao da resposta
    // o paciente corrige enquanto o prescritor n analisou, e depois
    // disso a correcao passa a ser anulacao com motivo
    // as datas vem junto: corrigir o periodo mexe nessa resposta, em vez de
    // criar outra no dia novo como acontecia pelo envio
    @Transactional
    public ScaleResponseDTO update(Long responseId, ScaleResponseCreateDTO answerData) {
        ScaleResponse response = findResponseForUpdate(responseId);
        // o MEEM eh do prescritor e corrigir ele eh anular e aplicar de novo
        if (!response.getScaleType().isFilledByPatient()) {
            throw new BusinessException(PRESCRIBER_SCALE_MESSAGE);
        }
        checkCanBeChanged(response);

        ScaleDefinition definition = catalog.definitionOf(response.getScaleType());
        boolean wasComplete = !definition.isIncomplete(response.getScore());
        changePeriod(response, definition, answerData);
        fillAnswersAndScore(response, definition, answerData.answers());

        ScaleResponse savedResponse = responseRepository.save(response);
        auditService.recordChange(response.getScaleType().getAuditRecordType(), savedResponse.getId(),
                savedResponse.getPatient().getId());
        boolean isComplete = !definition.isIncomplete(savedResponse.getScore());
        linkOrUnlinkAfterCorrection(savedResponse, wasComplete, isComplete);
        if (!wasComplete && isComplete) {
            taskService.notifyPrescriberOfAnswer(savedResponse.getPatient(), savedResponse.getScaleType());
        }
        return dtoOf(savedResponse);
    }

    // a escala pela metade q foi completada na correcao passa a valer pra tarefa, e a completa q ficou
    // pela metade deixa de valer: a tarefa volta a pendente e o lembrete volta a cobrar (RN10)
    private void linkOrUnlinkAfterCorrection(ScaleResponse response, boolean wasComplete, boolean isComplete) {
        if (isComplete && !wasComplete) {
            taskService.linkCompletedResponse(response);
        }
        if (wasComplete && !isComplete && response.getTask() != null) {
            ScaleTask task = response.getTask();
            response.setTask(null);
            responseRepository.save(response);
            taskService.reopenIfWithoutAnswers(task);
        }
    }

    // o prescritor marca q ja olhou a resposta (trava a edicao do paciente)
    @Transactional
    public ScaleResponseDTO review(Long responseId, Prescriber loggedPrescriber) {
        ScaleResponse response = findResponseForUpdate(responseId);
        if (response.isAnnulled()) {
            throw new BusinessException(ANNULLED_MESSAGE);
        }
        if (!response.isReviewed()) {
            response.markReviewed(loggedPrescriber);
            responseRepository.save(response);
            auditService.recordChange(response.getScaleType().getAuditRecordType(), response.getId(),
                    response.getPatient().getId());
        }
        return dtoOf(response);
    }

    // resposta registrada por engano n eh apagada, fica anulada com motivo
    @Transactional
    public ScaleResponseDTO annul(Long responseId, AnnulmentDTO annulmentData, Prescriber loggedPrescriber) {
        // o meem pode devolver a consulta dele pra agendada, entao ela trava antes do paciente (ordem das travas)
        Long appointmentId = responseRepository.findAppointmentIdById(responseId).orElse(null);
        if (appointmentId != null) {
            rowLock.reload(Appointment.class, appointmentId);
        }
        ScaleResponse response = findResponseForUpdate(responseId);
        if (response.isAnnulled()) {
            throw new BusinessException(ALREADY_ANNULLED_MESSAGE);
        }
        response.setAnnulment(new Annulment(loggedPrescriber, annulmentData.reason()));
        responseRepository.save(response);
        // sem resposta valida a tarefa volta a ser cobrada, senao aquele periodo
        // fica sem ninguem preencher e vira buraco no grafico sem aviso nenhum
        taskService.reopenAfterAnnulment(response);
        auditService.recordAnnulment(response.getScaleType().getAuditRecordType(), response.getId(),
                response.getPatient().getId());
        // igual na receita: o meem q era o unico sinal de q a consulta aconteceu devolve ela pra agendada
        if (response.getAppointment() != null) {
            consultationService.reopenIfConcludedOnlyByPrescriptionOrExam(response.getAppointment());
        }
        return dtoOf(response);
    }

    @Transactional
    public ScaleResponseDTO getById(Long responseId) {
        ScaleResponse response = findResponse(responseId);
        auditService.recordChartView(response.getPatient().getId());
        return dtoOf(response);
    }

    @Transactional
    public List<ScaleResponseSummaryDTO> getByPatientId(Long patientId) {
        auditService.recordChartView(patientId);
        return responseRepository.findByPatientIdOrderByPeriodStartDescIdDesc(patientId)
                .stream()
                .map(this::summaryOf)
                .toList();
    }

    // as respostas de uma escala so, q a grade da semana usa pra mostrar
    // os dias q ja foram preenchidos
    @Transactional
    public List<ScaleResponseDTO> getByPatientIdAndType(Long patientId, ScaleType scaleType) {
        auditService.recordChartView(patientId);
        return responseRepository.findByPatientIdAndScaleTypeOrderByPeriodStartDescIdDesc(patientId, scaleType)
                .stream()
                .map(this::dtoOf)
                .toList();
    }

    private void fillAnswersAndScore(ScaleResponse response, ScaleDefinition definition,
                                     Map<String, Object> rawAnswers) {
        Map<String, Object> answers = cleanAnswers(definition, rawAnswers);
        // so item em branco tbm eh resposta vazia, senao virava dia preenchido ou tarefa respondida sem nada
        if (answers.isEmpty()) {
            throw new BusinessException(EMPTY_ANSWERS_MESSAGE);
        }
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
            case NUMERO, MINUTOS -> wholeNumberInRange(item, value, 0, MAX_COUNT_VALUE);
            case HORAS -> decimalHours(item, value);
            case HORA -> timeValue(item, value);
            case SIM_NAO -> value instanceof Boolean flag ? flag : throwInvalidValue(item);
            case TEXTO -> textValue(item, value);
        };
    }

    private Object wholeNumberInRange(ScaleItem item, Object value, Integer minValue, Integer maxValue) {
        Long wholeNumber = wholeNumberOf(value);
        if (wholeNumber == null || wholeNumber < minValue || wholeNumber > maxValue) {
            return throwInvalidValue(item);
        }
        return wholeNumber.intValue();
    }

    private Object optionValue(ScaleItem item, Object value) {
        Long chosen = wholeNumberOf(value);
        if (chosen == null) {
            return throwInvalidValue(item);
        }
        boolean exists = item.options().stream().anyMatch(option -> option.value() == chosen);
        return exists ? chosen.intValue() : throwInvalidValue(item);
    }

    // so numero inteiro de vdd: o 2.9 virava 2 e o numero gigante dava a volta no int
    // e caia dentro da faixa, entao a faixa eh conferida antes de converter
    private Long wholeNumberOf(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        long wholeNumber = number.longValue();
        if (number.doubleValue() != wholeNumber) {
            return null;
        }
        return wholeNumber;
    }

    private Object decimalHours(ScaleItem item, Object value) {
        if (!(value instanceof Number number)) {
            return throwInvalidValue(item);
        }
        double hours = number.doubleValue();
        if (hours < 0 || hours > HOURS_IN_DAY) {
            return throwInvalidValue(item);
        }
        return hours;
    }

    private Object timeValue(ScaleItem item, Object value) {
        // horario q chega como numero era jogado fora calado e o pittsburgh ficava sem escore
        if (!(value instanceof String text)) {
            return throwInvalidValue(item);
        }
        if (text.isBlank()) {
            return null;
        }
        try {
            String hourAndMinute = text.length() > HOUR_MINUTE_LENGTH ? text.substring(0, HOUR_MINUTE_LENGTH) : text;
            return LocalTime.parse(hourAndMinute).toString();
        } catch (DateTimeParseException error) {
            return throwInvalidValue(item);
        }
    }

    private Object textValue(ScaleItem item, Object value) {
        if (!(value instanceof String text)) {
            return throwInvalidValue(item);
        }
        String trimmedText = text.trim();
        if (trimmedText.isEmpty()) {
            return null;
        }
        if (trimmedText.length() > MAX_TEXT_LENGTH) {
            throw new BusinessException(INVALID_TEXT_MESSAGE + ": " + item.label());
        }
        return trimmedText;
    }

    // sempre lanca e o retorno Object eh so p/ caber no switch
    private Object throwInvalidValue(ScaleItem item) {
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
        checkPeriod(periodStart, periodEnd);
        return periodEnd;
    }

    private void checkPeriod(LocalDate periodStart, LocalDate periodEnd) {
        if (periodEnd.isBefore(periodStart)) {
            throw new BusinessException(INVALID_PERIOD_MESSAGE);
        }
        // o acompanhamento fala do q ja passou, entao o fim do periodo tbm n pode ser um dia q n chegou
        if (periodStart.isAfter(LocalDate.now()) || periodEnd.isAfter(LocalDate.now())) {
            throw new BusinessException(FUTURE_PERIOD_MESSAGE);
        }
    }

    // data q n veio fica como estava, e o fim q n veio junto de um inicio novo segue a regra
    // do envio. no diario a data eh o dia da grade e n muda por aqui
    private void changePeriod(ScaleResponse response, ScaleDefinition definition,
                              ScaleResponseCreateDTO answerData) {
        LocalDate newStart = answerData.periodStart() != null ? answerData.periodStart() : response.getPeriodStart();
        boolean startChanged = !newStart.equals(response.getPeriodStart());
        LocalDate newEnd = answerData.periodEnd();
        if (newEnd == null) {
            newEnd = startChanged ? endOfDefaultPeriod(definition, newStart) : response.getPeriodEnd();
        }
        if (!startChanged && newEnd.equals(response.getPeriodEnd())) {
            return;
        }
        if (definition.fillMode() == ScaleDefinition.FillMode.DIARIO) {
            throw new BusinessException(DIARY_DAY_MESSAGE);
        }
        checkPeriod(newStart, newEnd);
        // o dia de inicio identifica a resposta na tela e no envio, entao n pode ter duas no mesmo
        boolean dayTaken = startChanged && responseRepository
                .findByPatientIdAndScaleTypeAndPeriodStartAndAnnulmentAnnulledAtIsNull(
                        response.getPatient().getId(), response.getScaleType(), newStart)
                .isPresent();
        if (dayTaken) {
            throw new BusinessException(PERIOD_START_TAKEN_MESSAGE);
        }
        response.setPeriodStart(newStart);
        response.setPeriodEnd(newEnd);
    }

    // a semana q comeca ha menos de 7 dias termina hoje, e n num dia q ainda n chegou
    private LocalDate endOfDefaultPeriod(ScaleDefinition definition, LocalDate periodStart) {
        if (definition.fillMode() != ScaleDefinition.FillMode.PERIODO) {
            return periodStart;
        }
        LocalDate periodEnd = periodStart.plusDays(DEFAULT_PERIOD_DAYS - 1L);
        return periodEnd.isAfter(LocalDate.now()) ? LocalDate.now() : periodEnd;
    }

    private void checkCanBeChanged(ScaleResponse response) {
        if (response.isAnnulled()) {
            throw new BusinessException(ANNULLED_MESSAGE);
        }
        if (response.isReviewed()) {
            throw new BusinessException(REVIEWED_MESSAGE);
        }
    }

    private ScaleResponseDTO dtoOf(ScaleResponse response) {
        return new ScaleResponseDTO(response, catalog.resultTextOf(response));
    }

    private ScaleResponseSummaryDTO summaryOf(ScaleResponse response) {
        return new ScaleResponseSummaryDTO(response, catalog.resultTextOf(response));
    }

    private ScaleResponse findResponse(Long responseId) {
        return responseRepository.findById(responseId).orElseThrow(() -> responseNotFound(responseId));
    }

    // quem vai mudar a resposta trava o paciente (a mesma trava do envio, e sempre antes da
    // resposta, senao duas transacoes ficam esperando uma pela outra) e so dps trava a resposta
    // e le ela de novo: a analise ou a anulacao q o outro lado acabou de gravar aparece,
    // em vez de ser sobrescrita pela correcao
    private ScaleResponse findResponseForUpdate(Long responseId) {
        Long patientId = responseRepository.findPatientIdById(responseId)
                .orElseThrow(() -> responseNotFound(responseId));
        patientRepository.lockById(patientId);
        return rowLock.reload(ScaleResponse.class, responseId).orElseThrow(() -> responseNotFound(responseId));
    }

    private NotFoundException responseNotFound(Long responseId) {
        return new NotFoundException("Resposta de escala não encontrada com o id: " + responseId);
    }

    private Patient findPatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> NotFoundException.patient(patientId));
    }
}
