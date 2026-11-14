package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.TrackableAttributeDTO;
import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.Cannabinoid;
import dev.uffs.doisag.enums.ConcentrationUnit;
import dev.uffs.doisag.enums.PrescriptionStatus;
import dev.uffs.doisag.enums.Spectrum;
import dev.uffs.doisag.enums.TimePeriod;
import dev.uffs.doisag.enums.TrackableAttribute;
import dev.uffs.doisag.enums.UserRole;
import dev.uffs.doisag.infra.BrazilianFormat;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.CsvBuilder;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Anamnesis;
import dev.uffs.doisag.model.Annulment;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.DoseEscalationStep;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Prescription;
import dev.uffs.doisag.model.PrescriptionComponent;
import dev.uffs.doisag.model.ScaleResponse;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AnamnesisRepository;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriptionRepository;
import dev.uffs.doisag.repository.ScaleResponseRepository;
import dev.uffs.doisag.scale.ScaleCatalog;
import dev.uffs.doisag.scale.ScaleItem;
import dev.uffs.doisag.scale.ScaleItemType;
import dev.uffs.doisag.scale.ScaleOption;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

// exportacao dos dados em csv (RF33)
//
// um arquivo por tipo, pra abrir direto na planilha. o modo sem identificacao
// eh pra pesquisa e sai sem nome, cpf, e-mail, telefone e endereco, com o
// paciente e o prescritor identificados so por um numero (RNF04)
// n eh anonimo: a clinica sabe de quem eh cada numero, entao pela lgpd o arquivo continua sendo dado pessoal (art. 12)
@Service
public class ExportService {

    public static final String DEIDENTIFIED_IS_FOR_PRESCRIBER_MESSAGE =
            "O modo sem identificação é da exportação do prescritor, para pesquisa";
    public static final String NO_RESEARCH_CONSENT_MESSAGE =
            "Este paciente não aceitou participar da pesquisa. O arquivo sem identificação só sai de quem aceitou";

    // os campos da anamnese com o nome q o paciente leu na tela (RF19)
    private static final List<AnamnesisField> ANAMNESIS_FIELDS = List.of(
            new AnamnesisField("Motivo principal", Anamnesis::getReasonForVisit),
            new AnamnesisField("Ocupação", Anamnesis::getProfession),
            new AnamnesisField("Diagnósticos anteriores", Anamnesis::getPreviousDiagnosis),
            new AnamnesisField("Tratamentos anteriores", Anamnesis::getPreviousTreatment),
            new AnamnesisField("Medicações em uso", Anamnesis::getCurrentMedication),
            new AnamnesisField("Doenças importantes na família", Anamnesis::getFamilyHistory),
            new AnamnesisField("Reações ruins a medicamentos", Anamnesis::getAdverseReaction),
            new AnamnesisField("Condições genéticas conhecidas", Anamnesis::getGeneticCondition),
            new AnamnesisField("Alimentação", Anamnesis::getDiet),
            new AnamnesisField("Fuma", Anamnesis::getSmokingHabits),
            new AnamnesisField("Bebe álcool", Anamnesis::getAlcoholConsumption),
            // com virgula decimal como o peso da consulta, senao a planilha em portugues le 68.5 como texto
            new AnamnesisField("Peso (kg)", anamnesis -> BrazilianFormat.decimalAnswer(anamnesis.getWeight())),
            new AnamnesisField("Altura (cm)", anamnesis -> BrazilianFormat.decimalAnswer(anamnesis.getHeight())),
            new AnamnesisField("Uso de outras substâncias", Anamnesis::getSubstanceUse),
            new AnamnesisField("Exercícios físicos", Anamnesis::getPhysicalActivity),
            new AnamnesisField("Sono", Anamnesis::getSleepHabits),
            new AnamnesisField("Ansiedade", Anamnesis::getAnxiety),
            new AnamnesisField("Dor", Anamnesis::getPain),
            new AnamnesisField("Expectativas", Anamnesis::getExpectations),
            new AnamnesisField("Sabe que precisa de acompanhamento regular", Anamnesis::getTreatmentAwareness),
            new AnamnesisField("Observações", Anamnesis::getObservation));

    private record AnamnesisField(String label, Function<Anamnesis, String> value) {
    }

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final AnamnesisRepository anamnesisRepository;
    private final ScaleResponseRepository responseRepository;
    private final ScaleCatalog catalog;
    private final ProgressReportService progressReportService;
    private final AuditService auditService;
    private final ConsentTermService consentTermService;

    public ExportService(PatientRepository patientRepository,
                         AppointmentRepository appointmentRepository,
                         PrescriptionRepository prescriptionRepository,
                         AnamnesisRepository anamnesisRepository,
                         ScaleResponseRepository responseRepository,
                         ScaleCatalog catalog,
                         ProgressReportService progressReportService,
                         AuditService auditService,
                         ConsentTermService consentTermService) {
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.anamnesisRepository = anamnesisRepository;
        this.responseRepository = responseRepository;
        this.catalog = catalog;
        this.progressReportService = progressReportService;
        this.auditService = auditService;
        this.consentTermService = consentTermService;
    }

    @Transactional
    public String appointmentsCsv(Long patientId, boolean deidentified, Users loggedUser) {
        Patient patient = startExport(patientId, deidentified, loggedUser);
        CsvBuilder csv = new CsvBuilder(patientColumn(deidentified), "Data", "Hora", "Modalidade",
                "Código da modalidade", "Situação", "Código da situação", "Prescritor", "Queixa e observações",
                "Exame físico", "Evolução", "Hipótese diagnóstica", "Conduta", "Exames complementares",
                "Pressão arterial", "Peso", "Altura", "Anulada", "Motivo da anulação");

        for (Appointment appointment : appointmentRepository.findByPatientIdOrderByDateTimeDesc(patientId)) {
            if (!belongsToHistory(appointment)) {
                continue;
            }
            if (appointment.isAnnulled() && isPatient(loggedUser)) {
                annulledLine(csv, appointment.getAnnulment(), patientCell(patient, deidentified),
                        BrazilianFormat.date(appointment.getDateTime().toLocalDate()),
                        BrazilianFormat.time(appointment.getDateTime()));
                continue;
            }
            csv.line(patientCell(patient, deidentified),
                    BrazilianFormat.date(appointment.getDateTime().toLocalDate()),
                    BrazilianFormat.time(appointment.getDateTime()),
                    modalityText(appointment.getModality()),
                    codeOf(appointment.getModality()),
                    statusText(appointment.getStatus()),
                    codeOf(appointment.getStatus()),
                    prescriberCell(appointment.getPrescriber(), deidentified),
                    appointment.getClinicalObservation(),
                    appointment.getPhysicalExam(),
                    appointment.getEvolution(),
                    appointment.getDiagnosis(),
                    appointment.getTherapeuticPlan(),
                    appointment.getComplementaryExams(),
                    appointment.getBloodPressure(),
                    BrazilianFormat.decimal(appointment.getWeight()),
                    appointment.getHeight(),
                    yesOrNo(appointment.isAnnulled()),
                    Annulment.reasonOf(appointment.getAnnulment()));
        }
        return csv.build();
    }

    // a mesma regra do historico e da impressao do front: consulta q ja passou e foi atendida,
    // estava marcada ou virou falta. pedido sem resposta, recusado, cancelada e consulta futura
    // n sao atendimento e ficam fora do arquivo, senao o csv mistura pedido com atendimento feito
    private boolean belongsToHistory(Appointment appointment) {
        boolean attended = appointment.getStatus().isConfirmed()
                || appointment.getStatus() == AppointmentStatus.NAO_COMPARECEU;
        return attended && !appointment.getDateTime().isAfter(LocalDateTime.now());
    }

    // a receita inteira (RF05): plano de dose, instrucoes, cuidados, efeitos esperados e quem
    // prescreveu, senao a linha com posologia "conforme o plano" ficava sem dose nenhuma
    @Transactional
    public String prescriptionsCsv(Long patientId, boolean deidentified, Users loggedUser) {
        Patient patient = startExport(patientId, deidentified, loggedUser);
        CsvBuilder csv = new CsvBuilder(patientColumn(deidentified), "Data da consulta", "Situação",
                "Código da situação", "Prescritor", "Produto", "Espectro", "Código do espectro", "Composição",
                "Marca", "Lote", "Volume", "Posologia", "Plano de dose", "Via", "Duração em dias", "Próxima consulta",
                "Instruções de uso", "Precauções e contraindicações", "Efeitos esperados", "Observações", "Anulada",
                "Motivo da anulação");

        for (Prescription prescription
                : prescriptionRepository.findByAppointmentPatientIdOrderByAppointmentDateTimeDescCreatedAtDesc(
                        patientId)) {
            if (prescription.isAnnulled() && isPatient(loggedUser)) {
                annulledLine(csv, prescription.getAnnulment(), patientCell(patient, deidentified),
                        BrazilianFormat.date(prescription.getAppointment().getDateTime().toLocalDate()));
                continue;
            }
            csv.line(patientCell(patient, deidentified),
                    BrazilianFormat.date(prescription.getAppointment().getDateTime().toLocalDate()),
                    prescriptionStatusText(prescription),
                    prescriptionStatusCode(prescription),
                    prescriberCell(prescription.getAppointment().getPrescriber(), deidentified),
                    prescription.getProductDescription(),
                    spectrumText(prescription.getSpectrum()),
                    codeOf(prescription.getSpectrum()),
                    composition(prescription),
                    prescription.getBrand(),
                    prescription.getBatch(),
                    prescription.getVolume(),
                    prescription.getPosology(),
                    dosePlan(prescription),
                    prescription.getAdministrationRoute(),
                    prescription.getTreatmentDurationDays(),
                    BrazilianFormat.date(prescription.getNextConsultationDate()),
                    prescription.getInstructions(),
                    prescription.getPrecautions(),
                    prescription.getExpectedEffects(),
                    prescription.getObservation(),
                    yesOrNo(prescription.isAnnulled()),
                    Annulment.reasonOf(prescription.getAnnulment()));
        }
        return csv.build();
    }

    // a consulta e a receita anuladas podem ser de outra pessoa lancadas no paciente errado, entao o arquivo do
    // paciente traz delas so a data, a marca de anulada e o motivo, com o resto da linha em branco. o do prescritor
    // continua inteiro (revisao das regras R7). a marca e o motivo sao sempre as duas ultimas colunas
    private void annulledLine(CsvBuilder csv, Annulment annulment, Object... firstCells) {
        Object[] cells = new Object[csv.columnCount()];
        for (int index = 0; index < firstCells.length; index++) {
            cells[index] = firstCells[index];
        }
        cells[cells.length - 2] = yesOrNo(true);
        cells[cells.length - 1] = Annulment.reasonOf(annulment);
        csv.line(cells);
    }

    private boolean isPatient(Users loggedUser) {
        return loggedUser.getRole() == UserRole.PATIENT;
    }

    // o plano de subida de dose numa celula so, um degrau atras do outro do jeito q a tela mostra,
    // tipo "Semana 1: 2 gotas à noite · começar devagar; Semana 2: 4 gotas à noite"
    // a celula comeca sempre com a palavra Semana, entao degrau com sinal na frente n vira formula
    private String dosePlan(Prescription prescription) {
        return prescription.getEscalationSteps().stream()
                .map(this::stepText)
                .collect(Collectors.joining("; "));
    }

    private String stepText(DoseEscalationStep step) {
        String text = "Semana " + step.getWeek() + ": " + step.getDosage();
        if (step.getNote() == null || step.getNote().isBlank()) {
            return text;
        }
        return text + " · " + step.getNote();
    }

    // uma linha por item respondido, q eh o formato q a planilha filtra
    // e agrupa sem precisar de uma coluna por escala
    @Transactional
    public String scaleResponsesCsv(Long patientId, boolean deidentified, Users loggedUser) {
        Patient patient = startExport(patientId, deidentified, loggedUser);
        CsvBuilder csv = new CsvBuilder(patientColumn(deidentified), "Escala", "Início do período", "Fim do período",
                "Escore", "Faixa", "Item", "Resposta", "Código", "Anulada", "Motivo da anulação");

        for (ScaleResponse response : responseRepository.findByPatientIdOrderByPeriodStartDescIdDesc(patientId)) {
            if (!catalog.hasDefinition(response.getScaleType())) {
                continue;
            }
            // o arquivo do paciente sai sem o MEEM. o prontuario inteiro ele pede pra clinica (lgpd art. 18)
            if (response.getScaleType().isHiddenFrom(loggedUser.getRole())) {
                continue;
            }
            for (ScaleItem item : catalog.definitionOf(response.getScaleType()).items()) {
                Object answer = response.getAnswers().get(item.key());
                if (answer == null) {
                    continue;
                }
                csv.line(patientCell(patient, deidentified),
                        response.getScaleType().getDisplayName(),
                        BrazilianFormat.date(response.getPeriodStart()),
                        BrazilianFormat.date(response.getPeriodEnd()),
                        response.getScore(),
                        response.getScoreBand(),
                        item.label(),
                        answerText(item, answer),
                        answerCode(item, answer),
                        yesOrNo(response.isAnnulled()),
                        Annulment.reasonOf(response.getAnnulment()));
            }
        }
        return csv.build();
    }

    // o mesmo texto q a tela mostra, pq codigo cru so da pra ler com um livro de
    // codigo q n existe. a nota continua como numero, q eh o q a planilha tabula
    private String answerText(ScaleItem item, Object answer) {
        return switch (item.type()) {
            case SIM_NAO -> Boolean.parseBoolean(String.valueOf(answer)) ? "Sim" : "Não";
            case ESCOLHA -> optionLabel(item, answer);
            case MINUTOS, CALCULADO -> minutesText(answer);
            case HORAS -> hoursText(answer);
            default -> String.valueOf(answer);
        };
    }

    // o codigo eh a resposta como o banco guarda, so as horas trocam o ponto pela virgula
    private Object answerCode(ScaleItem item, Object answer) {
        return item.type() == ScaleItemType.HORAS ? hoursText(answer) : answer;
    }

    // as horas aceitam meia hora e o banco guarda 6.5 e 7.0, q a planilha em portugues le como texto ou data
    // saem como 6,5 e 7, com a virgula e sem o zero a toa (revisao das regras R16)
    private String hoursText(Object answer) {
        if (answer instanceof Number number) {
            return BrazilianFormat.decimal(number);
        }
        return String.valueOf(answer);
    }

    private String optionLabel(ScaleItem item, Object answer) {
        Integer value = wholeNumberOf(answer);
        if (value == null) {
            return String.valueOf(answer);
        }
        for (ScaleOption option : item.options()) {
            if (option.value() == value) {
                return option.label();
            }
        }
        return String.valueOf(answer);
    }

    // minutos em horas e minutos, do mesmo jeito q a tela mostra
    private String minutesText(Object answer) {
        Integer minutes = wholeNumberOf(answer);
        if (minutes == null) {
            return String.valueOf(answer);
        }
        return BrazilianFormat.minutes(minutes);
    }

    // o json do banco devolve o numero as vezes inteiro e as vezes decimal
    private Integer wholeNumberOf(Object answer) {
        if (answer instanceof Number number) {
            return number.intValue();
        }
        try {
            return (int) Double.parseDouble(String.valueOf(answer));
        } catch (NumberFormatException error) {
            // n eh numero entao quem chamou mostra a resposta como veio
            return null;
        }
    }

    // o escore de escala validada sai com a faixa do lado, igual no scales.csv,
    // pq numero sozinho n quer dizer nada (RN14). item da ficha fica com a faixa em branco
    // e cada linha diz o q o minimo e o maximo querem dizer e se o maior eh melhor, como a tela, pq em
    // ansiedade o 10 eh tranquilo e em dor eh dor intensa (RF27, revisao das regras R16)
    @Transactional
    public String progressCsv(Long patientId, TrackableAttribute attribute, TimePeriod period,
                              boolean deidentified, Users loggedUser) {
        Patient patient = startExport(patientId, deidentified, loggedUser);
        CsvBuilder csv = new CsvBuilder(patientColumn(deidentified), "Escala", "Atributo", "Código do atributo",
                "Data", "Valor", "Faixa", "Significado do mínimo", "Significado do máximo", "Maior é melhor");
        TrackableAttributeDTO reading = progressReportService.attributeOf(attribute);

        progressReportService.getPatientProgress(patientId, attribute, period, loggedUser)
                .forEach(point -> csv.line(patientCell(patient, deidentified),
                        attribute.getScaleType().getDisplayName(),
                        reading.displayName(),
                        attribute.name(),
                        BrazilianFormat.date(point.date()),
                        point.value(),
                        point.band(),
                        anchorText(reading.minValue(), reading.lowAnchor()),
                        anchorText(reading.maxValue(), reading.highAnchor()),
                        reading.higherIsBetter() == null ? null : yesOrNo(reading.higherIsBetter())));
        return csv.build();
    }

    // tipo "0 = muito ansioso", igual embaixo do nome do atributo na tela. contagem e minutos n tem ancora
    private String anchorText(Integer value, String anchor) {
        if (anchor == null) {
            return null;
        }
        return value + " = " + anchor;
    }

    @Transactional
    public String anamnesisCsv(Long patientId, boolean deidentified, Users loggedUser) {
        Patient patient = startExport(patientId, deidentified, loggedUser);
        CsvBuilder csv = new CsvBuilder(patientColumn(deidentified), "Data", "Pergunta", "Resposta", "Anulada",
                "Motivo da anulação");

        for (Anamnesis anamnesis : anamnesisRepository.findByPatientIdOrderByAssessmentDateDescIdDesc(patientId)) {
            for (AnamnesisField field : ANAMNESIS_FIELDS) {
                String answer = field.value().apply(anamnesis);
                if (answer == null || answer.isBlank()) {
                    continue;
                }
                csv.line(patientCell(patient, deidentified),
                        BrazilianFormat.date(anamnesis.getAssessmentDate()),
                        field.label(),
                        answer,
                        yesOrNo(anamnesis.isAnnulled()),
                        Annulment.reasonOf(anamnesis.getAnnulment()));
            }
        }
        return csv.build();
    }

    public String fileName(String content, Long patientId, boolean deidentified) {
        String owner = deidentified ? "sem-identificacao" : "paciente-" + patientId;
        return content + "-" + owner + "-" + LocalDate.now() + ".csv";
    }

    // toda exportacao eh leitura de prontuario e entra na trilha (RF31)
    private Patient startExport(Long patientId, boolean deidentified, Users loggedUser) {
        if (deidentified && !(loggedUser instanceof Prescriber)) {
            throw new BusinessException(DEIDENTIFIED_IS_FOR_PRESCRIBER_MESSAGE);
        }
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> NotFoundException.patient(patientId));
        // o arquivo sem identificacao eh o de pesquisa e so sai de quem aceitou participar (RF37)
        if (deidentified && !consentTermService.hasResearchConsent(patient)) {
            throw new BusinessException(NO_RESEARCH_CONSENT_MESSAGE);
        }
        auditService.recordChartView(patientId);
        return patient;
    }

    private String yesOrNo(boolean value) {
        return value ? "sim" : "não";
    }

    // modalidade, situacao, espectro e atributo saem com o texto q a tela mostra,
    // pq o codigo do banco tipo NAO_COMPARECEU so quem fez o sistema entende.
    // o codigo vai na coluna do lado, q eh o q n muda pra quem filtra na planilha (revisao das regras R16)
    private String codeOf(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String modalityText(AppointmentModality modality) {
        if (modality == null) {
            return null;
        }
        return modality == AppointmentModality.PRESENCIAL ? "Presencial" : "Remota";
    }

    private String statusText(AppointmentStatus status) {
        return switch (status) {
            case SOLICITADA -> "Aguardando resposta";
            case AGENDADA -> "Agendada";
            case EM_ANDAMENTO -> "Em andamento";
            case CONCLUIDA -> "Concluída";
            case CANCELADA -> "Cancelada";
            case RECUSADA -> "Recusada";
            case NAO_COMPARECEU -> "Falta";
        };
    }

    // anular a vigente troca o status pra SUBSTITUIDA so pra ela n contar como vigente (NOOP-04),
    // mas ninguem substituiu ela. entao a anulada diz anulada, igual a tela
    private String prescriptionStatusText(Prescription prescription) {
        if (prescription.isAnnulled()) {
            return "Anulada";
        }
        return prescription.getStatus() == PrescriptionStatus.VIGENTE ? "Vigente" : "Substituída";
    }

    // pelo msm motivo o codigo da anulada eh ANULADA e n o SUBSTITUIDA q ficou gravado
    private String prescriptionStatusCode(Prescription prescription) {
        if (prescription.isAnnulled()) {
            return "ANULADA";
        }
        return prescription.getStatus().name();
    }

    private String spectrumText(Spectrum spectrum) {
        if (spectrum == null) {
            return null;
        }
        return switch (spectrum) {
            case ISOLADO -> "Isolado";
            case BROAD_SPECTRUM -> "Broad spectrum";
            case FULL_SPECTRUM -> "Full spectrum";
        };
    }

    private String patientColumn(boolean deidentified) {
        return deidentified ? "Identificador" : "Paciente";
    }

    // no modo sem identificacao o paciente vira um numero, sem nome nem contato
    // no arquivo identificado o nome civil vai junto do social pq o registro identifica a pessoa (RN15)
    private String patientCell(Patient patient, boolean deidentified) {
        if (deidentified) {
            return "paciente " + patient.getId();
        }
        if (patient.getSocialName() == null) {
            return patient.getName();
        }
        return patient.getSocialName() + " (nome civil: " + patient.getName() + ")";
    }

    // o prescritor tbm vira numero, senao o nome dele entrega a clinica e o grupo de paciente
    private String prescriberCell(Prescriber prescriber, boolean deidentified) {
        if (prescriber == null) {
            return null;
        }
        return deidentified ? "prescritor " + prescriber.getId() : prescriber.getName();
    }

    private String composition(Prescription prescription) {
        return prescription.getComponents().stream()
                .map(this::componentText)
                .collect(Collectors.joining(" + "));
    }

    // do jeito q a tela mostra, tipo CBD 10% + THC 0,3%, com virgula e sem os zeros do banco
    private String componentText(PrescriptionComponent component) {
        String amount = BrazilianFormat.decimal(component.getConcentration());
        String unit = component.getUnit() == ConcentrationUnit.PERCENTUAL ? "%" : " mg/mL";
        String cannabinoid = component.getCannabinoid() == Cannabinoid.OUTRO
                ? "Outro" : component.getCannabinoid().name();
        return cannabinoid + " " + amount + unit;
    }
}
