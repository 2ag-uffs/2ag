package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.AnamnesisCorrectionDTO;
import dev.uffs.doisag.dto.AnamnesisResponseDTO;
import dev.uffs.doisag.dto.AnamnesisDTO;
import dev.uffs.doisag.dto.AnnulmentDTO;
import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.enums.UserRole;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.infra.RowLock;
import dev.uffs.doisag.model.Anamnesis;
import dev.uffs.doisag.model.AnamnesisCorrection;
import dev.uffs.doisag.model.Annulment;
import dev.uffs.doisag.model.Notification;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.AnamnesisCorrectionRepository;
import dev.uffs.doisag.repository.AnamnesisRepository;
import dev.uffs.doisag.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ficha de anamnese (RF19)
// o paciente preenche e corrige e o prescritor anula quando o registro foi feito por engano
@Service
public class AnamnesisService {

    // as duas dizem o msm q a tela da anamnese ja diz qnd abre
    public static final String ANNULLED_MESSAGE =
            "Esta ficha foi anulada pelo prescritor e fica no histórico como está";
    public static final String ARCHIVED_PATIENT_MESSAGE =
            "Seu acompanhamento foi encerrado. Fale com a clínica antes de preencher uma anamnese nova";
    public static final String ALREADY_ANNULLED_MESSAGE = "Esta anamnese já foi anulada";
    // o aviso q o paciente recebe qnd a ficha eh anulada, com o motivo no fim
    public static final String ANNULLED_NOTICE_TITLE = "Avaliação inicial para preencher de novo";
    public static final String ANNULLED_NOTICE_MESSAGE =
            "Sua avaliação inicial (anamnese) foi anulada pelo prescritor. Preencha a ficha de novo.";
    public static final String ANNULLED_NOTICE_ARCHIVED_MESSAGE =
            "Sua avaliação inicial (anamnese) foi anulada pelo prescritor. Seu acompanhamento foi encerrado: "
                    + "fale com a clínica antes de preencher uma ficha nova.";

    private final AnamnesisRepository anamnesisRepository;
    private final AnamnesisCorrectionRepository correctionRepository;
    private final PatientRepository patientRepository;
    private final AuditService auditService;
    private final ScaleTaskService scaleTaskService;
    private final NotificationService notificationService;
    private final RowLock rowLock;

    public AnamnesisService(AnamnesisRepository anamnesisRepository,
                            AnamnesisCorrectionRepository correctionRepository,
                            PatientRepository patientRepository,
                            AuditService auditService, ScaleTaskService scaleTaskService,
                            NotificationService notificationService, RowLock rowLock) {
        this.anamnesisRepository = anamnesisRepository;
        this.correctionRepository = correctionRepository;
        this.patientRepository = patientRepository;
        this.auditService = auditService;
        this.scaleTaskService = scaleTaskService;
        this.notificationService = notificationService;
        this.rowLock = rowLock;
    }

    @Transactional
    public AnamnesisResponseDTO create(AnamnesisDTO anamnesisData, Patient patient) {
        // o paciente fica travado antes de tudo, igual na resposta de escala, pq a anamnese da baixa na tarefa:
        // o job q fecha a tarefa vencida trava o paciente antes de ler ela, entao um espera o outro
        patientRepository.lockById(patient.getId());
        // o paciente da sessao eh do login: lido de novo dps da trava, o arquivamento gravado enquanto
        // a anamnese esperava aparece
        Patient lockedPatient = patientRepository.findById(patient.getId())
                .orElseThrow(() -> NotFoundException.patient(patient.getId()));
        // prontuario de quem foi arquivado n recebe registro novo, o acompanhamento acabou
        if (lockedPatient.isArchived()) {
            throw new BusinessException(ARCHIVED_PATIENT_MESSAGE);
        }
        Anamnesis anamnesis = new Anamnesis();
        anamnesis.setPatient(lockedPatient);
        anamnesis.setAssessmentDate(anamnesisData.assessmentDate() == null ? LocalDate.now() : anamnesisData.assessmentDate());
        copyAnswers(anamnesisData, anamnesis);

        Anamnesis savedAnamnesis = anamnesisRepository.save(anamnesis);
        auditService.recordCreation(AuditRecordType.ANAMNESE, savedAnamnesis.getId(), lockedPatient.getId());
        // preencher a anamnese da baixa na tarefa q o prescritor enviou e avisa ele, igual as outras escalas
        scaleTaskService.completeTask(lockedPatient.getId(), ScaleType.ANAMNESE);
        scaleTaskService.notifyPrescriberOfAnswer(lockedPatient, ScaleType.ANAMNESE);
        return new AnamnesisResponseDTO(savedAnamnesis);
    }

    // o paciente corrige o q ele mesmo respondeu e o dono do registro nunca muda
    // a versao de antes fica guardada pra prescritora, em vez de sumir (decisao de 14/11/2026, revisao R4)
    @Transactional
    public AnamnesisResponseDTO update(Long anamnesisId, AnamnesisDTO anamnesisData) {
        Anamnesis anamnesis = findAnamnesisForUpdate(anamnesisId);
        if (anamnesis.isAnnulled()) {
            throw new BusinessException(ANNULLED_MESSAGE);
        }

        AnamnesisCorrection previousVersion = new AnamnesisCorrection(anamnesis);
        if (anamnesisData.assessmentDate() != null) {
            anamnesis.setAssessmentDate(anamnesisData.assessmentDate());
        }
        copyAnswers(anamnesisData, anamnesis);

        Anamnesis savedAnamnesis = anamnesisRepository.save(anamnesis);
        if (previousVersion.differsFrom(savedAnamnesis)) {
            correctionRepository.save(previousVersion);
        }
        auditService.recordChange(AuditRecordType.ANAMNESE, savedAnamnesis.getId(),
                savedAnamnesis.getPatient().getId());
        return new AnamnesisResponseDTO(savedAnamnesis);
    }

    // anamnese feita por engano fica no historico marcada como anulada com o motivo
    @Transactional
    public AnamnesisResponseDTO annul(Long anamnesisId, AnnulmentDTO annulmentData, Prescriber loggedPrescriber) {
        Anamnesis anamnesis = findAnamnesisForUpdate(anamnesisId);
        if (anamnesis.isAnnulled()) {
            throw new BusinessException(ALREADY_ANNULLED_MESSAGE);
        }

        anamnesis.setAnnulment(new Annulment(loggedPrescriber, annulmentData.reason()));
        Anamnesis savedAnamnesis = anamnesisRepository.save(anamnesis);
        auditService.recordAnnulment(AuditRecordType.ANAMNESE, savedAnamnesis.getId(),
                savedAnamnesis.getPatient().getId());
        notifyPatientOfAnnulment(savedAnamnesis);
        return new AnamnesisResponseDTO(savedAnamnesis);
    }

    // o paciente fica sabendo da anulacao com o motivo e o caminho pra preencher de novo, como na resposta
    // de escala anulada: antes ele so descobria abrindo o historico (decisao da dona, 11/2026)
    // a tarefa da avaliacao inicial fica como esta, e o aviso eh so dentro do sistema, nada vai por e-mail
    // o arquivado n consegue mandar ficha nova, entao o aviso dele manda falar com a clinica
    private void notifyPatientOfAnnulment(Anamnesis anamnesis) {
        Patient patient = anamnesis.getPatient();
        String message = patient.isArchived() ? ANNULLED_NOTICE_ARCHIVED_MESSAGE : ANNULLED_NOTICE_MESSAGE;
        notificationService.createNotification(patient, ANNULLED_NOTICE_TITLE,
                message + " Motivo: " + anamnesis.getAnnulment().getAnnulmentReason(),
                Notification.TYPE_FORM, ScaleType.ANAMNESE.getPath());
    }

    // anamneses de um paciente da mais recente pra mais antiga
    // as versoes de antes das correcoes vao so pra prescritora, a tela do paciente n precisa delas
    @Transactional
    public List<AnamnesisResponseDTO> getByPatientId(Long patientId, Users loggedUser) {
        auditService.recordChartView(patientId);
        Map<Long, List<AnamnesisCorrectionDTO>> correctionsByAnamnesis = new HashMap<>();
        if (loggedUser.getRole() == UserRole.PRESCRIBER) {
            for (AnamnesisCorrection correction : correctionRepository.findByPatientId(patientId)) {
                correctionsByAnamnesis.computeIfAbsent(correction.getAnamnesis().getId(), id -> new ArrayList<>())
                        .add(new AnamnesisCorrectionDTO(correction));
            }
        }
        return anamnesisRepository.findByPatientIdOrderByAssessmentDateDescIdDesc(patientId)
                .stream()
                .map(anamnesis -> new AnamnesisResponseDTO(anamnesis,
                        correctionsByAnamnesis.getOrDefault(anamnesis.getId(), List.of())))
                .toList();
    }

    @Transactional
    public AnamnesisResponseDTO getById(Long anamnesisId, Users loggedUser) {
        Anamnesis anamnesis = findAnamnesis(anamnesisId);
        auditService.recordChartView(anamnesis.getPatient().getId());
        if (loggedUser.getRole() != UserRole.PRESCRIBER) {
            return new AnamnesisResponseDTO(anamnesis);
        }
        List<AnamnesisCorrectionDTO> corrections = correctionRepository
                .findByAnamnesisIdOrderByCorrectedAtAscIdAsc(anamnesisId)
                .stream()
                .map(AnamnesisCorrectionDTO::new)
                .toList();
        return new AnamnesisResponseDTO(anamnesis, corrections);
    }

    private Anamnesis findAnamnesis(Long anamnesisId) {
        return anamnesisRepository.findById(anamnesisId)
                .orElseThrow(() -> anamnesisNotFound(anamnesisId));
    }

    // a correcao e a anulacao gravam a linha inteira, entao uma apagava a outra qnd vinham juntas
    // com a trava quem chega dps espera e le a anamnese de novo: a correcao encontra ela anulada
    // e eh recusada, e a anulacao mantem o q o paciente acabou de corrigir
    // o paciente eh travado antes da anamnese, igual na resposta de escala: a trilha das duas encosta
    // no paciente e a da correcao na conta dele tbm, entao elas esperam a confirmacao do e-mail
    // (paciente, dps conta) em vez de segurar a conta q ela vai travar
    private Anamnesis findAnamnesisForUpdate(Long anamnesisId) {
        Long patientId = anamnesisRepository.findPatientIdById(anamnesisId)
                .orElseThrow(() -> anamnesisNotFound(anamnesisId));
        patientRepository.lockById(patientId);
        return rowLock.reload(Anamnesis.class, anamnesisId)
                .orElseThrow(() -> anamnesisNotFound(anamnesisId));
    }

    private NotFoundException anamnesisNotFound(Long anamnesisId) {
        return new NotFoundException("Anamnese não encontrada com o id: " + anamnesisId);
    }

    private void copyAnswers(AnamnesisDTO anamnesisData, Anamnesis anamnesis) {
        anamnesis.setProfession(anamnesisData.profession());
        anamnesis.setReasonForVisit(anamnesisData.reasonForVisit());
        anamnesis.setPreviousDiagnosis(anamnesisData.previousDiagnosis());
        anamnesis.setPreviousTreatment(anamnesisData.previousTreatment());
        anamnesis.setCurrentMedication(anamnesisData.currentMedication());
        anamnesis.setFamilyHistory(anamnesisData.familyHistory());
        anamnesis.setAdverseReaction(anamnesisData.adverseReaction());
        anamnesis.setGeneticCondition(anamnesisData.geneticCondition());
        anamnesis.setDiet(anamnesisData.diet());
        anamnesis.setSmokingHabits(anamnesisData.smokingHabits());
        anamnesis.setAlcoholConsumption(anamnesisData.alcoholConsumption());
        anamnesis.setWeight(anamnesisData.weight());
        anamnesis.setHeight(anamnesisData.height());
        anamnesis.setSubstanceUse(anamnesisData.substanceUse());
        anamnesis.setPhysicalActivity(anamnesisData.physicalActivity());
        anamnesis.setSleepHabits(anamnesisData.sleepHabits());
        anamnesis.setAnxiety(anamnesisData.anxiety());
        anamnesis.setPain(anamnesisData.pain());
        anamnesis.setExpectations(anamnesisData.expectations());
        anamnesis.setTreatmentAwareness(anamnesisData.treatmentAwareness());
        anamnesis.setObservation(anamnesisData.observation());
    }
}
