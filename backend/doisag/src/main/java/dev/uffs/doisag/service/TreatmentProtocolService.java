package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.AssignScaleDTO;
import dev.uffs.doisag.dto.TreatmentProtocolCreateDTO;
import dev.uffs.doisag.enums.AssignmentStatus;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.AssignedScale;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.ProtocolItem;
import dev.uffs.doisag.model.TreatmentProtocol;
import dev.uffs.doisag.repository.AssignedScaleRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.TreatmentProtocolRepository;
import jakarta.validation.ValidationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

// cuida do acompanhamento automatico dos 90 dias (RF32).
//
// o prescritor monta o protocolo uma vez, dizendo quais escalas o
// paciente responde e de quanto em quanto tempo. dai um job diario
// designa o que venceu, sem ninguem precisar lembrar.
//
// era a dor principal da entrevista de 2025: a prescritora acumula a
// parte clinica e a administrativa, e mandava formulario um por um
@Service
public class TreatmentProtocolService {

    // o acompanhamento dura 90 dias por padrao (RN01)
    private static final int DURACAO_PADRAO_EM_DIAS = 90;

    private final TreatmentProtocolRepository protocolRepository;
    private final PatientRepository patientRepository;
    private final AssignedScaleRepository assignedScaleRepository;
    private final ScaleAssignmentService scaleAssignmentService;

    public TreatmentProtocolService(TreatmentProtocolRepository protocolRepository,
                                    PatientRepository patientRepository,
                                    AssignedScaleRepository assignedScaleRepository,
                                    ScaleAssignmentService scaleAssignmentService) {
        this.protocolRepository = protocolRepository;
        this.patientRepository = patientRepository;
        this.assignedScaleRepository = assignedScaleRepository;
        this.scaleAssignmentService = scaleAssignmentService;
    }

    public TreatmentProtocol create(Long patientId, TreatmentProtocolCreateDTO dados, Prescriber prescriber) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado com o id: " + patientId));

        // dois protocolos ativos ao mesmo tempo designariam a mesma escala
        // duas vezes, entao o anterior precisa ser encerrado antes
        protocolRepository.findFirstByPatientIdAndActiveTrue(patientId).ifPresent(anterior -> {
            throw new ValidationException("Este paciente já tem um acompanhamento em andamento");
        });

        LocalDate inicio = dados.startDate() != null ? dados.startDate() : LocalDate.now();
        int duracao = dados.durationDays() != null ? dados.durationDays() : DURACAO_PADRAO_EM_DIAS;

        TreatmentProtocol protocol = new TreatmentProtocol();
        protocol.setPatient(patient);
        protocol.setPrescriber(prescriber);
        protocol.setStartDate(inicio);
        protocol.setEndDate(inicio.plusDays(duracao));
        protocol.setActive(true);

        dados.items().forEach(itemDto -> {
            // o MEEM eh aplicado pelo prescritor na consulta, n entra no
            // acompanhamento automatico do paciente (RN09)
            if (itemDto.scaleType() == ScaleType.MINI_EXAME_ESTADO_MENTAL) {
                throw new ValidationException(
                        "O Mini-Exame do Estado Mental é aplicado pelo prescritor durante a consulta");
            }
            ProtocolItem item = new ProtocolItem();
            item.setScaleType(itemDto.scaleType());
            item.setPeriodicity(itemDto.periodicity());
            protocol.addItem(item);
        });

        return protocolRepository.save(protocol);
    }

    public TreatmentProtocol getActiveByPatient(Long patientId) {
        return protocolRepository.findFirstByPatientIdAndActiveTrue(patientId)
                .orElseThrow(() -> new NotFoundException(
                        "Nenhum acompanhamento em andamento para o paciente " + patientId));
    }

    public TreatmentProtocol encerrar(Long patientId) {
        TreatmentProtocol protocol = getActiveByPatient(patientId);
        protocol.setActive(false);
        return protocolRepository.save(protocol);
    }

    // roda uma vez por dia. recebe a data de fora em vez de chamar
    // LocalDate.now() aqui dentro, senao n daria pra testar
    public int designarEscalasVencidas(LocalDate hoje) {
        int designadas = 0;

        for (TreatmentProtocol protocol : protocolRepository.findByActiveTrue()) {
            if (hoje.isBefore(protocol.getStartDate())) {
                continue;
            }

            // passou dos 90 dias: encerra e n designa mais nada
            if (hoje.isAfter(protocol.getEndDate())) {
                protocol.setActive(false);
                protocolRepository.save(protocol);
                continue;
            }

            for (ProtocolItem item : protocol.getItems()) {
                if (estaNaHora(protocol, item, hoje)) {
                    scaleAssignmentService.assignScaleToPatient(
                            protocol.getPatient().getId(),
                            new AssignScaleDTO(item.getScaleType()),
                            hoje);
                    designadas++;
                }
            }
        }

        return designadas;
    }

    // olha quando essa escala foi designada pela ultima vez em vez de
    // contar os dias desde o inicio. assim, se o servidor ficar fora do
    // ar por uns dias, o acompanhamento continua de onde parou em vez de
    // pular a rodada
    private boolean estaNaHora(TreatmentProtocol protocol, ProtocolItem item, LocalDate hoje) {
        Optional<AssignedScale> ultima = assignedScaleRepository
                .findFirstByPatientIdAndScaleTypeOrderByAssignedDateDesc(
                        protocol.getPatient().getId(), item.getScaleType());

        if (ultima.isEmpty()) {
            // primeira vez: designa assim que o acompanhamento comeca
            return true;
        }

        long diasDesdeAUltima = ChronoUnit.DAYS.between(ultima.get().getAssignedDate(), hoje);
        return diasDesdeAUltima >= item.getPeriodicity().getDays();
    }

    // escalas que o paciente ainda n respondeu e ja passaram do prazo.
    // o prescritor ve isso no painel dele
    public long contarPendenciasAtrasadas(Long patientId, LocalDate hoje) {
        return assignedScaleRepository
                .findByPatientIdAndStatus(patientId, AssignmentStatus.PENDENTE)
                .stream()
                .filter(escala -> ChronoUnit.DAYS.between(escala.getAssignedDate(), hoje) > 7)
                .count();
    }
}
