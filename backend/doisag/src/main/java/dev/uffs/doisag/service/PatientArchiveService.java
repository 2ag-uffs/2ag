package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.PatientResponseDTO;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// arquivamento de paciente
// o paciente arquivado sai da lista de ativos e o acompanhamento automatico dele acaba
// as escalas q estavam em aberto fecham na hora, pra ele n ser cobrado delas
// o pedido de consulta esperando resposta eh recusado, pq segurava horario de quem segue em tratamento
// a consulta ja marcada fica na agenda: cancelar eh decisao do prescritor, q ve quantas ficaram na resposta
// o prontuario continua inteiro e o paciente segue vendo o proprio historico
// as escalas q o prescritor enviar na mao continuam chegando pra ele
@Service
public class PatientArchiveService {

    public static final String ALREADY_ARCHIVED_MESSAGE = "Este paciente já está arquivado";
    // a lista de pacientes chama de ativos quem n esta arquivado
    public static final String NOT_ARCHIVED_MESSAGE = "Este paciente já está na lista de ativos";
    public static final String NEW_REQUEST_MESSAGE =
            "O paciente acabou de pedir uma consulta. Tente arquivar de novo para recusar o pedido";

    private final PatientRepository patientRepository;
    private final TreatmentProtocolService treatmentProtocolService;
    private final ScaleTaskService scaleTaskService;
    private final AppointmentService appointmentService;
    private final AuditService auditService;

    public PatientArchiveService(PatientRepository patientRepository, TreatmentProtocolService treatmentProtocolService,
                                 ScaleTaskService scaleTaskService, AppointmentService appointmentService,
                                 AuditService auditService) {
        this.patientRepository = patientRepository;
        this.treatmentProtocolService = treatmentProtocolService;
        this.scaleTaskService = scaleTaskService;
        this.appointmentService = appointmentService;
        this.auditService = auditService;
    }

    @Transactional
    public PatientResponseDTO archive(Long patientId, Prescriber loggedPrescriber) {
        // a consulta vem antes do paciente na ordem das travas, entao os pedidos em aberto sao travados antes
        List<Appointment> openRequests = appointmentService.lockOpenRequestsOf(patientId);
        Patient patient = lockAndFindPatient(patientId);
        if (patient.isArchived()) {
            throw new BusinessException(ALREADY_ARCHIVED_MESSAGE);
        }
        // o pedido q o paciente gravou entre a leitura dos pedidos e a trava dele ficou fora da lista
        // o arquivamento eh recusado e o prescritor tenta de novo, senao esse pedido ficava aberto no arquivado
        if (appointmentService.hasOpenRequestsBesides(patientId, openRequests)) {
            throw new BusinessException(NEW_REQUEST_MESSAGE);
        }
        // a consulta marcada fica na agenda, e o prescritor ve na resposta quantas ficaram
        long futureAppointments = appointmentService.countFutureConfirmedOf(patientId);

        patient.setArchivedAt(LocalDateTime.now());
        patient.setArchivedBy(loggedPrescriber);
        Patient savedPatient = patientRepository.save(patient);
        treatmentProtocolService.endActiveProtocolIfAny(patientId);
        scaleTaskService.closePendingOf(patientId);
        appointmentService.declineForArchiving(openRequests);
        auditService.recordArchiving(patientId);
        return PatientResponseDTO.forArchive(savedPatient, futureAppointments);
    }

    // reativar n recria o acompanhamento automatico e o prescritor monta um novo se quiser
    @Transactional
    public PatientResponseDTO reactivate(Long patientId) {
        Patient patient = lockAndFindPatient(patientId);
        if (!patient.isArchived()) {
            throw new BusinessException(NOT_ARCHIVED_MESSAGE);
        }

        patient.setArchivedAt(null);
        patient.setArchivedBy(null);
        Patient savedPatient = patientRepository.save(patient);
        auditService.recordReactivation(patientId);
        return new PatientResponseDTO(savedPatient);
    }

    // gravar o paciente grava a conta antes (o updated_at), entao o paciente eh travado primeiro,
    // na ordem da escala e da confirmacao do e-mail (paciente, dps conta)
    private Patient lockAndFindPatient(Long patientId) {
        patientRepository.lockById(patientId);
        return patientRepository.findById(patientId)
                .orElseThrow(() -> NotFoundException.patient(patientId));
    }
}
