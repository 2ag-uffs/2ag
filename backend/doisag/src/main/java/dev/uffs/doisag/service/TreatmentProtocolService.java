package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.TreatmentProtocolCreateDTO;
import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.ProtocolItem;
import dev.uffs.doisag.model.ScaleTask;
import dev.uffs.doisag.model.TreatmentProtocol;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.ScaleTaskRepository;
import dev.uffs.doisag.repository.TreatmentProtocolRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

    // o acompanhamento dura 90 dias por padrao (RN01)
    private static final int DURACAO_PADRAO_EM_DIAS = 90;

    public static final String ARCHIVED_PATIENT_MESSAGE =
            "Paciente arquivado não recebe acompanhamento automático. Reative o paciente antes";

    private final TreatmentProtocolRepository protocolRepository;
    private final PatientRepository patientRepository;
    private final ScaleTaskRepository taskRepository;
    private final ScaleTaskService scaleTaskService;
    private final AuditService auditService;

    public TreatmentProtocolService(TreatmentProtocolRepository protocolRepository,
                                    PatientRepository patientRepository,
                                    ScaleTaskRepository taskRepository,
                                    ScaleTaskService scaleTaskService,
                                    AuditService auditService) {
        this.protocolRepository = protocolRepository;
        this.patientRepository = patientRepository;
        this.taskRepository = taskRepository;
        this.scaleTaskService = scaleTaskService;
        this.auditService = auditService;
    }

    @Transactional
    public TreatmentProtocol create(Long patientId, TreatmentProtocolCreateDTO dados, Prescriber prescriber) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado com o id: " + patientId));
        // paciente arquivado n recebe envio automatico ate o prescritor reativar
        if (patient.isArchived()) {
            throw new BusinessException(ARCHIVED_PATIENT_MESSAGE);
        }
        // dois protocolos ativos ao mesmo tempo designariam a mesma escala
        // duas vezes, entao o anterior precisa ser encerrado antes
        protocolRepository.findFirstByPatientIdAndActiveTrue(patientId).ifPresent(anterior -> {
            throw new BusinessException("Este paciente já tem um acompanhamento em andamento");
        });

        LocalDate inicio = dados.startDate() != null ? dados.startDate() : LocalDate.now();
        int duracao = dados.durationDays() != null ? dados.durationDays() : DURACAO_PADRAO_EM_DIAS;

        TreatmentProtocol protocol = new TreatmentProtocol();
        protocol.setPatient(patient);
        protocol.setPrescriber(prescriber);
        protocol.setStartDate(inicio);
        protocol.setEndDate(inicio.plusDays(duracao));
        protocol.setActive(true);
        // a programacao de horarios q aparece no topo do diario do sono (RF22)
        protocol.setSleepBedTime(dados.sleepBedTime());
        protocol.setSleepWakeTime(dados.sleepWakeTime());

        dados.items().forEach(itemDto -> {
            // o MEEM eh aplicado pelo prescritor na consulta, n entra no
            // acompanhamento automatico do paciente (RN09)
            if (!itemDto.scaleType().isFilledByPatient()) {
                throw new BusinessException(ScaleTaskService.PRESCRIBER_SCALE_MESSAGE);
            }
            ProtocolItem item = new ProtocolItem();
            item.setScaleType(itemDto.scaleType());
            item.setPeriodicity(itemDto.periodicity());
            protocol.addItem(item);
        });

        TreatmentProtocol savedProtocol = protocolRepository.save(protocol);
        auditService.recordCreation(AuditRecordType.ACOMPANHAMENTO_AUTOMATICO, savedProtocol.getId(), patientId);
        return savedProtocol;
    }

    public TreatmentProtocol getActiveByPatient(Long patientId) {
        auditService.recordChartView(patientId);
        return findActiveProtocol(patientId);
    }

    // busca usada dentro do servico q n conta como abrir o prontuario
    private TreatmentProtocol findActiveProtocol(Long patientId) {
        return protocolRepository.findFirstByPatientIdAndActiveTrue(patientId)
                .orElseThrow(() -> new NotFoundException(
                        "Nenhum acompanhamento em andamento para o paciente " + patientId));
    }

    @Transactional
    public TreatmentProtocol encerrar(Long patientId) {
        return endProtocol(findActiveProtocol(patientId));
    }

    // arquivar o paciente encerra o acompanhamento automatico q estiver andando
    @Transactional
    public void endActiveProtocolIfAny(Long patientId) {
        protocolRepository.findFirstByPatientIdAndActiveTrue(patientId).ifPresent(this::endProtocol);
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
    // tudo numa transacao so pra ler os itens de cada protocolo e gravar a auditoria junto
    @Transactional
    public int designarEscalasVencidas(LocalDate hoje) {
        int designadas = 0;
        for (TreatmentProtocol protocol : protocolRepository.findByActiveTrue()) {
            if (hoje.isBefore(protocol.getStartDate())) {
                continue;
            }
            // passou dos 90 dias: encerra e n designa mais nada
            if (hoje.isAfter(protocol.getEndDate())) {
                endProtocol(protocol);
                continue;
            }
            for (ProtocolItem item : protocol.getItems()) {
                if (estaNaHora(protocol, item, hoje)) {
                    scaleTaskService.assign(protocol.getPatient().getId(), item.getScaleType(), hoje,
                            item.getPeriodicity().getDays());
                    designadas++;
                }
            }
        }
        return designadas;
    }

    // olha quando essa escala foi enviada pela ultima vez em vez de
    // contar os dias desde o inicio. assim, se o servidor ficar fora do
    // ar por uns dias, o acompanhamento continua de onde parou em vez de
    // pular a rodada
    private boolean estaNaHora(TreatmentProtocol protocol, ProtocolItem item, LocalDate hoje) {
        Optional<ScaleTask> ultima = scaleTaskService.lastTaskOf(protocol.getPatient().getId(), item.getScaleType());
        if (ultima.isEmpty()) {
            // primeira vez: envia assim que o acompanhamento comeca
            return true;
        }
        // a tarefa anterior so eh substituida quando o periodo dela acaba,
        // entao a mesma escala nunca fica pendente duas vezes
        return hoje.isAfter(ultima.get().getPeriodEnd());
    }

    // escalas q venceram sem resposta. o prescritor ve isso no painel dele
    public long contarPendenciasAtrasadas(Long patientId) {
        return taskRepository.countByPatientIdAndStatus(patientId, ScaleTaskStatus.NAO_RESPONDIDA);
    }

    public ScaleType[] escalasDoProtocolo(Long patientId) {
        return findActiveProtocol(patientId).getItems().stream()
                .map(ProtocolItem::getScaleType)
                .toArray(ScaleType[]::new);
    }
}
