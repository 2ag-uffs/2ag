package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.AssignScaleDTO;
import dev.uffs.doisag.dto.AssignedScaleResponseDTO;
import dev.uffs.doisag.enums.AssignmentStatus;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.model.AssignedScale;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.repository.AssignedScaleRepository;
import dev.uffs.doisag.repository.PatientRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.MessageSource;
import java.util.Locale;
import java.util.stream.Collectors;
import dev.uffs.doisag.dto.CompletedScaleInfoDTO;
import dev.uffs.doisag.dto.PatientScalesPageDTO;
import dev.uffs.doisag.dto.PendingScaleInfoDTO;
import dev.uffs.doisag.infra.ResourceNotFoundException;


@Service
public class ScaleAssignmentService {
    // injecoes
    private final AssignedScaleRepository assignedScaleRepository;
    private final PatientRepository patientRepository;
    private NotificationService notificationService;
    private final MessageSource messageSource;

    public ScaleAssignmentService(AssignedScaleRepository assignedScaleRepository, PatientRepository patientRepository, MessageSource messageSource, NotificationService notificationService) {
        this.assignedScaleRepository = assignedScaleRepository;
        this.patientRepository = patientRepository;
        this.messageSource = messageSource;
        this.notificationService = notificationService;
    }

    @Autowired
    public void setNotificationService(@Lazy NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public AssignedScaleResponseDTO assignScaleToPatient(Long patientId, AssignScaleDTO assignScaleDTO) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new EntityNotFoundException("Paciente não encontrado"));

        AssignedScale newAssignment = new AssignedScale();
        newAssignment.setPatient(patient);
        newAssignment.setPrescriber(patient.getPrescriber());
        newAssignment.setScaleType(assignScaleDTO.scaleType());
        newAssignment.setStatus(AssignmentStatus.PENDENTE);
        newAssignment.setAssignedDate(LocalDate.now());

        // a gente salva a entidade no banco
        AssignedScale savedAssignment = assignedScaleRepository.save(newAssignment);

        String scaleName = formatScaleName(savedAssignment.getScaleType());
        String notificationTitle = messageSource.getMessage("notification.new_task.title", new Object[]{scaleName}, Locale.getDefault());
        String notificationMessage = messageSource.getMessage("notification.new_task.message", null, Locale.getDefault());

        String linkParaCentralDeEscalas = String.format("/pacientes/%d/escalas", patient.getId());

        notificationService.createNotification(patient, notificationTitle, notificationMessage, "FORM", linkParaCentralDeEscalas);

        return new AssignedScaleResponseDTO(savedAssignment);
    }

    public void completeAssignedScale(Long patientId, ScaleType scaleType) {
        assignedScaleRepository.findFirstByPatientIdAndScaleTypeAndStatusOrderByAssignedDateDesc(
                patientId,
                scaleType,
                AssignmentStatus.PENDENTE
        ).ifPresent(assignment -> {
            assignment.setStatus(AssignmentStatus.CONCLUIDO);
            assignment.setCompletedDate(LocalDate.now());
            assignedScaleRepository.save(assignment);
        });
    }

    private String formatScaleName(ScaleType scaleType) {
        String name = scaleType.toString().replace("_", " ").toLowerCase();
        name = name.replace("escala ", "").replace("registro ", "");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    // busca a lista de escalas designadas para um paciente
    public List<AssignedScaleResponseDTO> getAssignedScalesForPatient(Long patientId) {
        // a gente busca a lista de entidades do banco
        List<AssignedScale> scales = assignedScaleRepository.findByPatientIdOrderByAssignedDateDesc(patientId);

        // transforma cada entidade em um DTO antes de retornar
        return scales.stream()
                .map(AssignedScaleResponseDTO::new) // para cada escala cria um dto
                .collect(Collectors.toList());
    }

    public PatientScalesPageDTO getPatientScalesPageData(Long patientId) {
        // busca o paciente pra pegar o nome dele
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente não encontrado com o id: " + patientId));

        // busco as escalas pendentes
        List<PendingScaleInfoDTO> pending = assignedScaleRepository
                .findByPatientIdAndStatus(patientId, AssignmentStatus.PENDENTE)
                .stream()
                .map(this::mapToPendingDTO) // usa um helper pra converter
                .collect(Collectors.toList());

        // busco as escalas concluídas
        List<CompletedScaleInfoDTO> completed = assignedScaleRepository
                .findByPatientIdAndStatus(patientId, AssignmentStatus.CONCLUIDO)
                .stream()
                .map(this::mapToCompletedDTO) // usa um helper pra converter
                .collect(Collectors.toList());

        // aqui monto e retorno o DTO principal
        return new PatientScalesPageDTO(patient.getName(), pending, completed);
    }
    // helper para map das escalas pendentes
    private PendingScaleInfoDTO mapToPendingDTO(AssignedScale assignedScale) {
        String name = getDisplayNameForScale(assignedScale.getScaleType());
        String path = getPathForScale(assignedScale.getScaleType());
        String description = "Esta avaliação está aguardando seu preenchimento.";
        return new PendingScaleInfoDTO(assignedScale.getId(), name, description, path);
    }

    // helper para map das escalas concluidas
    private CompletedScaleInfoDTO mapToCompletedDTO(AssignedScale assignedScale) {
        String name = getDisplayNameForScale(assignedScale.getScaleType());
        String path = getPathForScale(assignedScale.getScaleType());
        String result = "Concluído";
        return new CompletedScaleInfoDTO(assignedScale.getId(), name, assignedScale.getCompletedDate(), result, path);
    }

    // helper para pegar o nome das escalas
    private String getDisplayNameForScale(ScaleType scaleType) {
        switch (scaleType) {
            case REGISTRO_SONO:
                return "Registro diário de sono";
            case MINI_EXAME_ESTADO_MENTAL:
                return "Mini-Exame do Estado Mental (MEEM)";
            case ACOMPANHAMENTO_SEMANAL:
                return "Acompanhamento semanal";
            case ESCALA_HAMILTON:
                return "Escala de ansiedade de Hamilton";
            case ESCALA_PITTSBURGH:
                return "Índice de qualidade do sono de Pittsburgh";
            case REGISTRO_DOR:
                return "Registro diário de dor";
            case REGISTRO_TEA:
                return "Registro de sintomas (TEA)";
            case ANAMNESE:
                return "Avaliação inicial";
            default:
                return "Avaliação";
        }
    }

        // helper para pegar o path das escalas
        private String getPathForScale(ScaleType scaleType) {
            switch (scaleType) {
                case REGISTRO_SONO: return "/diario-sono";
                case MINI_EXAME_ESTADO_MENTAL: return "/mini-exame";
                case ACOMPANHAMENTO_SEMANAL: return "/acompanhamento-paciente";
                case ESCALA_HAMILTON: return "/escala-hamilton";
                case ESCALA_PITTSBURGH: return "/escala-pittsburgh";
                case REGISTRO_DOR: return "/diario-dor";
                case REGISTRO_TEA: return "/diario-tea";
                case ANAMNESE: return "/anamnese";
                default: return "/";
            }
        }

}
