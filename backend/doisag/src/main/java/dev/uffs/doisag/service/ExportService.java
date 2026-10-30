package dev.uffs.doisag.service;

import dev.uffs.doisag.enums.Cannabinoid;
import dev.uffs.doisag.enums.ConcentrationUnit;
import dev.uffs.doisag.enums.TimePeriod;
import dev.uffs.doisag.enums.TrackableAttribute;
import dev.uffs.doisag.infra.BrazilianFormat;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.CsvBuilder;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Anamnesis;
import dev.uffs.doisag.model.Annulment;
import dev.uffs.doisag.model.Appointment;
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
import dev.uffs.doisag.scale.ScaleOption;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
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
            new AnamnesisField("Peso (kg)", Anamnesis::getWeight),
            new AnamnesisField("Altura (cm)", Anamnesis::getHeight),
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
        CsvBuilder csv = new CsvBuilder(patientColumn(deidentified), "Data", "Hora", "Modalidade", "Situação",
                "Prescritor", "Queixa e observações", "Exame físico", "Evolução", "Hipótese diagnóstica",
                "Conduta", "Exames complementares", "Pressão arterial", "Peso", "Altura", "Anulada",
                "Motivo da anulação");

        for (Appointment appointment : appointmentRepository.findByPatientIdOrderByDateTimeDesc(patientId)) {
            csv.line(patientCell(patient, deidentified),
                    BrazilianFormat.date(appointment.getDateTime().toLocalDate()),
                    BrazilianFormat.time(appointment.getDateTime()),
                    appointment.getModality(),
                    appointment.getStatus(),
                    prescriberCell(appointment.getPrescriber(), deidentified),
                    appointment.getClinicalObservation(),
                    appointment.getPhysicalExam(),
                    appointment.getEvolution(),
                    appointment.getDiagnosis(),
                    appointment.getTherapeuticPlan(),
                    appointment.getComplementaryExams(),
                    appointment.getBloodPressure(),
                    appointment.getWeight(),
                    appointment.getHeight(),
                    yesOrNo(appointment.isAnnulled()),
                    Annulment.reasonOf(appointment.getAnnulment()));
        }
        return csv.build();
    }

    @Transactional
    public String prescriptionsCsv(Long patientId, boolean deidentified, Users loggedUser) {
        Patient patient = startExport(patientId, deidentified, loggedUser);
        CsvBuilder csv = new CsvBuilder(patientColumn(deidentified), "Data da consulta", "Situação", "Produto",
                "Espectro", "Composição", "Marca", "Lote", "Volume", "Posologia", "Via", "Duração em dias",
                "Próxima consulta", "Observações", "Anulada", "Motivo da anulação");

        for (Prescription prescription
                : prescriptionRepository.findByAppointmentPatientIdOrderByAppointmentDateTimeDescCreatedAtDesc(
                        patientId)) {
            csv.line(patientCell(patient, deidentified),
                    BrazilianFormat.date(prescription.getAppointment().getDateTime().toLocalDate()),
                    prescription.getStatus(),
                    prescription.getProductDescription(),
                    prescription.getSpectrum(),
                    composition(prescription),
                    prescription.getBrand(),
                    prescription.getBatch(),
                    prescription.getVolume(),
                    prescription.getPosology(),
                    prescription.getAdministrationRoute(),
                    prescription.getTreatmentDurationDays(),
                    BrazilianFormat.date(prescription.getNextConsultationDate()),
                    prescription.getObservation(),
                    yesOrNo(prescription.isAnnulled()),
                    Annulment.reasonOf(prescription.getAnnulment()));
        }
        return csv.build();
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
                        answer,
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
            default -> String.valueOf(answer);
        };
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
    @Transactional
    public String progressCsv(Long patientId, TrackableAttribute attribute, TimePeriod period,
                              boolean deidentified, Users loggedUser) {
        Patient patient = startExport(patientId, deidentified, loggedUser);
        CsvBuilder csv = new CsvBuilder(patientColumn(deidentified), "Escala", "Atributo", "Data", "Valor", "Faixa");

        progressReportService.getPatientProgress(patientId, attribute, period)
                .forEach(point -> csv.line(patientCell(patient, deidentified),
                        attribute.getScaleType().getDisplayName(),
                        attribute.name(),
                        BrazilianFormat.date(point.date()),
                        point.value(),
                        point.band()));
        return csv.build();
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

    // do jeito q a tela mostra, tipo CBD 10% + THC 0,3%. o banco devolve 10.000
    // e no brasil o ponto separa o milhar, entao sai com virgula e sem os zeros
    private String componentText(PrescriptionComponent component) {
        String amount = NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR"))
                .format(component.getConcentration());
        String unit = component.getUnit() == ConcentrationUnit.PERCENTUAL ? "%" : " mg/mL";
        String cannabinoid = component.getCannabinoid() == Cannabinoid.OUTRO
                ? "Outro" : component.getCannabinoid().name();
        return cannabinoid + " " + amount + unit;
    }
}
