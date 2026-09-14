package dev.uffs.doisag.service;

import dev.uffs.doisag.enums.TimePeriod;
import dev.uffs.doisag.enums.TrackableAttribute;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.CsvBuilder;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Anamnesis;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

// exportacao dos dados em csv (RF33)
//
// um arquivo por tipo, pra abrir direto na planilha. o modo anonimo eh
// pra pesquisa e sai sem nome, cpf, e-mail, telefone e endereco, com o
// paciente identificado so por um numero (RNF04)
@Service
public class ExportService {

    public static final String ANONYMOUS_IS_FOR_PRESCRIBER_MESSAGE =
            "O modo anônimo é da exportação do prescritor, para pesquisa";

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

    public ExportService(PatientRepository patientRepository,
                         AppointmentRepository appointmentRepository,
                         PrescriptionRepository prescriptionRepository,
                         AnamnesisRepository anamnesisRepository,
                         ScaleResponseRepository responseRepository,
                         ScaleCatalog catalog,
                         ProgressReportService progressReportService,
                         AuditService auditService) {
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.anamnesisRepository = anamnesisRepository;
        this.responseRepository = responseRepository;
        this.catalog = catalog;
        this.progressReportService = progressReportService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public String appointmentsCsv(Long patientId, boolean anonymous, Users loggedUser) {
        Patient patient = startExport(patientId, anonymous, loggedUser);
        CsvBuilder csv = new CsvBuilder(patientColumn(anonymous), "Data", "Hora", "Modalidade", "Situação",
                "Prescritor", "Queixa e observações", "Exame físico", "Evolução", "Hipótese diagnóstica",
                "Conduta", "Exames complementares", "Pressão arterial", "Peso", "Altura", "Anulada",
                "Motivo da anulação");

        for (Appointment appointment : appointmentRepository.findByPatientIdOrderByDateTimeDesc(patientId)) {
            csv.line(patientCell(patient, anonymous),
                    date(appointment.getDateTime().toLocalDate()),
                    time(appointment.getDateTime()),
                    appointment.getModality(),
                    appointment.getStatus(),
                    appointment.getPrescriber() == null ? null : appointment.getPrescriber().getName(),
                    appointment.getClinicalObservation(),
                    appointment.getPhysicalExam(),
                    appointment.getEvolution(),
                    appointment.getDiagnosis(),
                    appointment.getTherapeuticPlan(),
                    appointment.getComplementaryExams(),
                    appointment.getBloodPressure(),
                    appointment.getWeight(),
                    appointment.getHeight(),
                    appointment.isAnnulled() ? "sim" : "não",
                    appointment.getAnnulment() == null ? null : appointment.getAnnulment().getAnnulmentReason());
        }
        return csv.build();
    }

    @Transactional(readOnly = true)
    public String prescriptionsCsv(Long patientId, boolean anonymous, Users loggedUser) {
        Patient patient = startExport(patientId, anonymous, loggedUser);
        CsvBuilder csv = new CsvBuilder(patientColumn(anonymous), "Data da consulta", "Situação", "Produto",
                "Espectro", "Composição", "Marca", "Lote", "Volume", "Posologia", "Via", "Duração em dias",
                "Próxima consulta", "Anulada", "Motivo da anulação");

        for (Prescription prescription
                : prescriptionRepository.findByAppointmentPatientIdOrderByAppointmentDateTimeDescCreatedAtDesc(
                        patientId)) {
            csv.line(patientCell(patient, anonymous),
                    date(prescription.getAppointment().getDateTime().toLocalDate()),
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
                    date(prescription.getNextConsultationDate()),
                    prescription.isAnnulled() ? "sim" : "não",
                    prescription.getAnnulment() == null ? null : prescription.getAnnulment().getAnnulmentReason());
        }
        return csv.build();
    }

    // uma linha por item respondido, q eh o formato q a planilha filtra
    // e agrupa sem precisar de uma coluna por escala
    @Transactional(readOnly = true)
    public String scaleResponsesCsv(Long patientId, boolean anonymous, Users loggedUser) {
        Patient patient = startExport(patientId, anonymous, loggedUser);
        CsvBuilder csv = new CsvBuilder(patientColumn(anonymous), "Escala", "Início do período", "Fim do período",
                "Escore", "Faixa", "Item", "Resposta", "Anulada");

        for (ScaleResponse response : responseRepository.findByPatientIdOrderByPeriodStartDesc(patientId)) {
            if (!catalog.hasDefinition(response.getScaleType())) {
                continue;
            }
            for (ScaleItem item : catalog.definitionOf(response.getScaleType()).items()) {
                Object answer = response.getAnswers().get(item.key());
                if (answer == null) {
                    continue;
                }
                csv.line(patientCell(patient, anonymous),
                        response.getScaleType().getDisplayName(),
                        date(response.getPeriodStart()),
                        date(response.getPeriodEnd()),
                        response.getScore(),
                        response.getScoreBand(),
                        item.label(),
                        answer,
                        response.isAnnulled() ? "sim" : "não");
            }
        }
        return csv.build();
    }

    @Transactional(readOnly = true)
    public String progressCsv(Long patientId, TrackableAttribute attribute, TimePeriod period,
                              boolean anonymous, Users loggedUser) {
        Patient patient = startExport(patientId, anonymous, loggedUser);
        CsvBuilder csv = new CsvBuilder(patientColumn(anonymous), "Escala", "Atributo", "Data", "Valor");

        progressReportService.getPatientProgress(patientId, attribute, period)
                .forEach(point -> csv.line(patientCell(patient, anonymous),
                        attribute.getScaleType().getDisplayName(),
                        attribute.name(),
                        date(point.date()),
                        point.value()));
        return csv.build();
    }

    @Transactional(readOnly = true)
    public String anamnesisCsv(Long patientId, boolean anonymous, Users loggedUser) {
        Patient patient = startExport(patientId, anonymous, loggedUser);
        CsvBuilder csv = new CsvBuilder(patientColumn(anonymous), "Data", "Pergunta", "Resposta", "Anulada");

        for (Anamnesis anamnesis : anamnesisRepository.findByPatientIdOrderByAssessmentDateDesc(patientId)) {
            for (AnamnesisField field : ANAMNESIS_FIELDS) {
                String answer = field.value().apply(anamnesis);
                if (answer == null || answer.isBlank()) {
                    continue;
                }
                csv.line(patientCell(patient, anonymous),
                        date(anamnesis.getAssessmentDate()),
                        field.label(),
                        answer,
                        anamnesis.isAnnulled() ? "sim" : "não");
            }
        }
        return csv.build();
    }

    // o nome do arquivo q vai no download
    public String fileName(String content, Long patientId, boolean anonymous) {
        String owner = anonymous ? "anonimo" : "paciente-" + patientId;
        return content + "-" + owner + "-" + LocalDate.now() + ".csv";
    }

    // toda exportacao eh leitura de prontuario e entra na trilha (RF31)
    private Patient startExport(Long patientId, boolean anonymous, Users loggedUser) {
        if (anonymous && !(loggedUser instanceof Prescriber)) {
            throw new BusinessException(ANONYMOUS_IS_FOR_PRESCRIBER_MESSAGE);
        }
        auditService.recordChartView(patientId);
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado com o id: " + patientId));
    }

    private String patientColumn(boolean anonymous) {
        return anonymous ? "Identificador" : "Paciente";
    }

    // no modo anonimo o paciente vira um numero e nenhum dado pessoal sai
    private String patientCell(Patient patient, boolean anonymous) {
        return anonymous ? "paciente " + patient.getId() : patient.getName();
    }

    private String composition(Prescription prescription) {
        return prescription.getComponents().stream()
                .map(this::componentText)
                .collect(Collectors.joining(" + "));
    }

    private String componentText(PrescriptionComponent component) {
        return component.getCannabinoid() + " " + component.getConcentration() + " " + component.getUnit();
    }

    private String date(LocalDate date) {
        return date == null ? null : String.format("%02d/%02d/%d", date.getDayOfMonth(), date.getMonthValue(),
                date.getYear());
    }

    private String time(LocalDateTime dateTime) {
        return String.format("%02d:%02d", dateTime.getHour(), dateTime.getMinute());
    }
}
