package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.ProtocolItemDTO;
import dev.uffs.doisag.dto.TreatmentProtocolCreateDTO;
import dev.uffs.doisag.dto.TreatmentProtocolResponseDTO;
import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.ProtocolItem;
import dev.uffs.doisag.model.ScaleTask;
import dev.uffs.doisag.model.TreatmentProtocol;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.ScaleTaskRepository;
import dev.uffs.doisag.repository.TreatmentProtocolRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// cuida do acompanhamento automatico dos 90 dias (RF32)
//
// o prescritor monta o protocolo uma vez, dizendo quais escalas o
// paciente responde e de quanto em quanto tempo. dai um job diario
// fecha o q venceu e envia o q chegou a hora, sem ninguem lembrar
//
// era a dor principal da entrevista de 2025: a prescritora acumula a
// parte clinica e a administrativa, e mandava formulario um por um
@Service
public class TreatmentProtocolService {

    private static final Logger log = LoggerFactory.getLogger(TreatmentProtocolService.class);

    // o acompanhamento dura 90 dias por padrao (RN01)
    private static final int DEFAULT_DURATION_DAYS = 90;

    public static final String ARCHIVED_PATIENT_MESSAGE =
            "Paciente arquivado não recebe acompanhamento automático. Reative o paciente antes";
    public static final String SINGLE_SCALE_MESSAGE =
            "A avaliação inicial é respondida uma vez, então ela não entra no acompanhamento automático. "
                    + "Envie ela em Enviar escalas";

    private final TreatmentProtocolRepository protocolRepository;
    private final PatientRepository patientRepository;
    private final PrescriberRepository prescriberRepository;
    private final ScaleTaskRepository taskRepository;
    private final ScaleTaskService scaleTaskService;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;

    public TreatmentProtocolService(TreatmentProtocolRepository protocolRepository,
                                    PatientRepository patientRepository,
                                    PrescriberRepository prescriberRepository,
                                    ScaleTaskRepository taskRepository,
                                    ScaleTaskService scaleTaskService,
                                    AuditService auditService,
                                    PlatformTransactionManager transactionManager) {
        this.protocolRepository = protocolRepository;
        this.patientRepository = patientRepository;
        this.prescriberRepository = prescriberRepository;
        this.taskRepository = taskRepository;
        this.scaleTaskService = scaleTaskService;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public TreatmentProtocolResponseDTO create(Long patientId, TreatmentProtocolCreateDTO protocolData,
                                               Prescriber prescriber) {
        // a linha do prescritor fica travada ate o fim: dois cadastros ao mesmo tempo passavam
        // os dois pela busca vazia e o segundo acompanhamento seguia invisivel por 90 dias
        // o paciente vem logo dps, antes de ler ele: o arquivamento gravado enquanto o cadastro esperava aparece,
        // em vez de o paciente arquivado ficar com um acompanhamento ativo
        prescriberRepository.lockById(prescriber.getId());
        patientRepository.lockById(patientId);
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado com o id: " + patientId));
        // paciente arquivado n recebe envio automatico ate o prescritor reativar
        if (patient.isArchived()) {
            throw new BusinessException(ARCHIVED_PATIENT_MESSAGE);
        }
        // dois protocolos ativos ao mesmo tempo designariam a mesma escala
        // duas vezes, entao o anterior precisa ser encerrado antes
        if (protocolRepository.findFirstByPatientIdAndActiveTrue(patientId).isPresent()) {
            throw new BusinessException("Este paciente já tem um acompanhamento em andamento");
        }

        LocalDate startDate = protocolData.startDate() != null ? protocolData.startDate() : LocalDate.now();
        int durationDays = protocolData.durationDays() != null ? protocolData.durationDays() : DEFAULT_DURATION_DAYS;

        TreatmentProtocol protocol = new TreatmentProtocol();
        protocol.setPatient(patient);
        protocol.setPrescriber(prescriber);
        protocol.setStartDate(startDate);
        // o primeiro dia conta, entao 90 dias terminam no dia 90 e n no 91
        protocol.setEndDate(startDate.plusDays(durationDays - 1L));
        protocol.setActive(true);
        // a programacao de horarios q aparece no topo do diario do sono (RF22)
        protocol.setSleepBedTime(protocolData.sleepBedTime());
        protocol.setSleepWakeTime(protocolData.sleepWakeTime());
        addItems(protocol, protocolData.items());

        TreatmentProtocol savedProtocol = protocolRepository.save(protocol);
        auditService.recordCreation(AuditRecordType.ACOMPANHAMENTO_AUTOMATICO, savedProtocol.getId(), patientId);
        // acompanhamento q comeca hoje manda a primeira rodada agora, sem esperar o job de amanha
        // conta desativada n recebe, igual no job
        if (startDate.equals(LocalDate.now()) && patient.isActive()) {
            assignRound(savedProtocol, startDate);
        }
        return new TreatmentProtocolResponseDTO(savedProtocol);
    }

    private void addItems(TreatmentProtocol protocol, List<ProtocolItemDTO> itemsData) {
        for (ProtocolItemDTO itemData : itemsData) {
            // o MEEM eh aplicado pelo prescritor na consulta, n entra no
            // acompanhamento automatico do paciente (RN09)
            if (!itemData.scaleType().isFilledByPatient()) {
                throw new BusinessException(ScaleTaskService.PRESCRIBER_SCALE_MESSAGE);
            }
            // a avaliacao inicial eh da triagem: no acompanhamento ela voltaria
            // a cada periodicidade e o paciente refaria a ficha inteira (RF19)
            if (!itemData.scaleType().isAssignablePeriodically()) {
                throw new BusinessException(SINGLE_SCALE_MESSAGE);
            }
            ProtocolItem item = new ProtocolItem();
            item.setScaleType(itemData.scaleType());
            item.setPeriodicity(itemData.periodicity());
            protocol.addItem(item);
        }
    }

    // abrir a pagina do acompanhamento conta como abrir o prontuario, mesmo sem acompanhamento
    // em andamento: por isso o "n tem" volta vazio em vez de erro, senao a transacao desfazia
    // a trilha gravada aqui
    @Transactional
    public Optional<TreatmentProtocolResponseDTO> getActiveByPatient(Long patientId) {
        auditService.recordChartView(patientId);
        return protocolRepository.findFirstByPatientIdAndActiveTrue(patientId)
                .map(TreatmentProtocolResponseDTO::new);
    }

    // busca usada dentro do servico q n conta como abrir o prontuario
    private TreatmentProtocol findActiveProtocol(Long patientId) {
        return protocolRepository.findFirstByPatientIdAndActiveTrue(patientId)
                .orElseThrow(() -> new NotFoundException(
                        "Nenhum acompanhamento em andamento para o paciente " + patientId));
    }

    @Transactional
    public TreatmentProtocolResponseDTO end(Long patientId) {
        return new TreatmentProtocolResponseDTO(endProtocol(findActiveProtocol(patientId)));
    }

    // arquivar o paciente encerra o acompanhamento automatico q estiver andando
    @Transactional
    public void endActiveProtocolIfAny(Long patientId) {
        protocolRepository.findFirstByPatientIdAndActiveTrue(patientId).ifPresent(this::endProtocol);
    }

    // desativar o prescritor encerra o acompanhamento automatico dos pacientes dele
    @Transactional
    public void endActiveProtocolsOfPrescriber(Long prescriberId) {
        for (Patient patient : patientRepository.findAllByPrescriberIdAndArchivedAtIsNullOrderByNameAsc(prescriberId)) {
            endActiveProtocolIfAny(patient.getId());
        }
    }

    // encerrar n apaga nada e o protocolo fica guardado como inativo
    private TreatmentProtocol endProtocol(TreatmentProtocol protocol) {
        protocol.setActive(false);
        TreatmentProtocol savedProtocol = protocolRepository.save(protocol);
        auditService.recordChange(AuditRecordType.ACOMPANHAMENTO_AUTOMATICO, savedProtocol.getId(),
                savedProtocol.getPatient().getId());
        return savedProtocol;
    }

    // roda uma vez por dia. recebe a data de fora em vez de chamar
    // LocalDate.now() aqui dentro, senao n daria pra testar
    // cada protocolo na propria transacao: um com problema n desfaz nem trava o dos outros (#110)
    public int designarEscalasVencidas(LocalDate today) {
        int assignedCount = 0;
        int failures = 0;
        for (Long protocolId : protocolRepository.findActiveIds()) {
            try {
                Integer sent = transactionTemplate.execute(status -> assignDueRound(protocolId, today));
                assignedCount += sent == null ? 0 : sent;
            } catch (RuntimeException exception) {
                failures++;
                log.error("Tarefas do dia: falha no acompanhamento {}", protocolId, exception);
            }
        }
        // o dia n conta como feito e a proxima rodada tenta de novo, sem repetir o q ja saiu
        if (failures > 0) {
            throw new IllegalStateException(failures + " acompanhamento(s) com falha, "
                    + assignedCount + " escalas enviadas nos outros");
        }
        return assignedCount;
    }

    private int assignDueRound(Long protocolId, LocalDate today) {
        Long patientId = protocolRepository.findPatientIdById(protocolId).orElse(null);
        if (patientId == null) {
            return 0;
        }
        // trava o prescritor da tarefa nova e dps o paciente, na mesma ordem do cadastro e do envio avulso,
        // senao no postgres o job segurava o paciente e esperava o prescritor no insert da tarefa, e o envio
        // avulso segurava o prescritor e esperava o paciente. a trava solta no fim deste acompanhamento
        // as duas vem antes de ler o protocolo e o paciente: o job q lia antes e so dps esperava a trava mandava
        // a rodada pra quem foi arquivado, encerrado ou desativado nesse meio tempo
        Long prescriberId = patientRepository.findPrescriberIdById(patientId).orElse(null);
        if (prescriberId != null) {
            prescriberRepository.lockById(prescriberId);
        }
        patientRepository.lockById(patientId);
        TreatmentProtocol protocol = protocolRepository.findById(protocolId).orElse(null);
        // pode ter sido encerrado entre a busca dos ids e agora
        if (protocol == null || !protocol.isActive() || today.isBefore(protocol.getStartDate())) {
            return 0;
        }
        // passou dos 90 dias: encerra e n designa mais nada
        if (today.isAfter(protocol.getEndDate())) {
            endProtocol(protocol);
            return 0;
        }
        // conta desativada n recebe escala nova. se a do paciente for reativada o envio continua
        Patient patient = protocol.getPatient();
        Prescriber prescriber = patient.getPrescriber();
        boolean bothAccountsActive = patient.isActive() && prescriber != null && prescriber.isActive();
        if (!bothAccountsActive) {
            return 0;
        }
        return assignRound(protocol, today);
    }

    // manda as escalas do protocolo q estao na hora e devolve quantas foram
    // quem chama ja travou o prescritor e dps o paciente: o cadastro e o job
    // a trava do paciente vem antes de ler as tarefas dele, igual na resposta da escala, senao o job lia a
    // tarefa enquanto o paciente respondia ela e um gravava por cima do outro: a rodada voltava a tarefa
    // respondida pra pendente, ou a resposta desfazia o prazo novo da rodada
    private int assignRound(TreatmentProtocol protocol, LocalDate today) {
        Long patientId = protocol.getPatient().getId();
        int assignedCount = 0;
        for (ProtocolItem item : protocol.getItems()) {
            if (!isRoundDue(item, today)) {
                continue;
            }
            int periodDays = item.getPeriodicity().getDays();
            LocalDate roundEnd = today.plusDays(periodDays - 1L);
            ScaleTask task = scaleTaskService.assign(patientId, item.getScaleType(), today, periodDays, true);
            // a tarefa q ja existia vira a rodada: a avulsa aberta ou respondida no dia, e a do
            // acompanhamento anterior ainda pendente ou respondida no dia. ela fica com o fim da rodada,
            // senao a tarefa q o paciente tem acabava antes do lastRoundEnd e os dias entre os dois
            // ficavam sem tarefa nenhuma
            if (!task.isFromProtocol() || !task.getPeriodEnd().equals(roundEnd)) {
                scaleTaskService.adoptAsProtocolRound(task, roundEnd);
            }
            item.setLastRoundEnd(roundEnd);
            assignedCount++;
        }
        return assignedCount;
    }

    // olha quando a ultima rodada desse acompanhamento acaba em vez de
    // contar os dias desde o inicio. assim, se o servidor ficar fora do
    // ar por uns dias, o acompanhamento continua de onde parou em vez de
    // pular a rodada
    // a rodada fica no item, entao o acompanhamento q substitui outro comeca o proprio ciclo com a
    // periodicidade nova, em vez de esperar o prazo da rodada mensal do anterior
    private boolean isRoundDue(ProtocolItem item, LocalDate today) {
        // primeira vez envia assim q o acompanhamento comeca
        if (item.getLastRoundEnd() == null) {
            return true;
        }
        // a rodada anterior so eh substituida quando o periodo dela acaba,
        // entao a mesma escala nunca fica pendente duas vezes
        return today.isAfter(item.getLastRoundEnd());
    }

    // escalas q venceram sem resposta. o prescritor ve isso no painel dele
    public long contarPendenciasAtrasadas(Long patientId) {
        return taskRepository.countByPatientIdAndStatus(patientId, ScaleTaskStatus.NAO_RESPONDIDA);
    }

}
