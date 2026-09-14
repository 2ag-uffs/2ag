package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.ProtocolItemDTO;
import dev.uffs.doisag.dto.TreatmentProtocolCreateDTO;
import dev.uffs.doisag.enums.Periodicity;
import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.ScaleTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// o acompanhamento automatico dos 90 dias (RF32).
//
// o job de verdade roda uma vez por dia, entao aqui a gente chama a
// logica passando a data na mao. eh o unico jeito de testar o que
// acontece daqui a 7, 30 ou 91 dias sem esperar
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TreatmentProtocolServiceTest {

    @Autowired private TreatmentProtocolService treatmentProtocolService;
    @Autowired private ScaleTaskService scaleTaskService;
    @Autowired private PatientRepository patientRepository;
    @Autowired private PrescriberRepository prescriberRepository;
    @Autowired private ScaleTaskRepository taskRepository;

    private static final LocalDate INICIO = LocalDate.of(2026, 9, 1);

    private Patient paciente;
    private Prescriber prescritora;

    @BeforeEach
    void montaClinica() {
        prescritora = new Prescriber();
        prescritora.setName("Prescritora");
        prescritora.setEmail("presc-protocolo@email.com");
        prescritora.setPassword("hash");
        prescritora = prescriberRepository.save(prescritora);

        paciente = new Patient();
        paciente.setName("Paciente do Protocolo");
        paciente.setEmail("pac-protocolo@email.com");
        paciente.setPassword("hash");
        paciente.setPrescriber(prescritora);
        paciente = patientRepository.save(paciente);
    }

    private void criaProtocolo(List<ProtocolItemDTO> itens) {
        treatmentProtocolService.create(
                paciente.getId(),
                new TreatmentProtocolCreateDTO(INICIO, 90, null, null, itens),
                prescritora);
    }

    private ProtocolItemDTO item(ScaleType escala, Periodicity periodicidade) {
        return new ProtocolItemDTO(escala, escala.getDisplayName(), periodicidade);
    }

    private long quantasEnviadas(ScaleType escala) {
        return taskRepository.findByPatientIdOrderByPeriodStartDesc(paciente.getId())
                .stream()
                .filter(task -> task.getScaleType() == escala)
                .count();
    }

    @Test
    void designaAsEscalasLogoNoPrimeiroDia() {
        criaProtocolo(List.of(
                item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL),
                item(ScaleType.ESCALA_PITTSBURGH, Periodicity.MENSAL)));

        int designadas = treatmentProtocolService.designarEscalasVencidas(INICIO);

        assertThat(designadas).isEqualTo(2);
        assertThat(quantasEnviadas(ScaleType.ACOMPANHAMENTO_SEMANAL)).isEqualTo(1);
        assertThat(quantasEnviadas(ScaleType.ESCALA_PITTSBURGH)).isEqualTo(1);
    }

    // a tarefa vale pelo periodo da periodicidade: a semanal fecha em 7 dias
    @Test
    void aTarefaValePeloPeriodoDaPeriodicidade() {
        criaProtocolo(List.of(item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL)));
        treatmentProtocolService.designarEscalasVencidas(INICIO);

        var tarefa = taskRepository.findByPatientIdOrderByPeriodStartDesc(paciente.getId()).get(0);

        assertThat(tarefa.getPeriodStart()).isEqualTo(INICIO);
        assertThat(tarefa.getPeriodEnd()).isEqualTo(INICIO.plusDays(6));
        assertThat(tarefa.getStatus()).isEqualTo(ScaleTaskStatus.PENDENTE);
    }

    // se o job rodar de novo no dia seguinte ele n pode mandar tudo outra
    // vez, senao o paciente acorda com a caixa cheia de formulario
    @Test
    void naoDesignaDeNovoNoDiaSeguinte() {
        criaProtocolo(List.of(item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL)));
        treatmentProtocolService.designarEscalasVencidas(INICIO);

        int designadas = treatmentProtocolService.designarEscalasVencidas(INICIO.plusDays(1));

        assertThat(designadas).isZero();
        assertThat(quantasEnviadas(ScaleType.ACOMPANHAMENTO_SEMANAL)).isEqualTo(1);
    }

    @Test
    void designaDeNovoQuandoCompletaASemana() {
        criaProtocolo(List.of(item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL)));
        treatmentProtocolService.designarEscalasVencidas(INICIO);

        treatmentProtocolService.designarEscalasVencidas(INICIO.plusDays(7));

        assertThat(quantasEnviadas(ScaleType.ACOMPANHAMENTO_SEMANAL)).isEqualTo(2);
    }

    // a semana q acabou sem resposta vira lacuna, e n uma pendencia eterna
    @Test
    void aSemanaSemRespostaFechaComoNaoRespondida() {
        criaProtocolo(List.of(item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL)));
        treatmentProtocolService.designarEscalasVencidas(INICIO);

        int fechadas = scaleTaskService.closeOverdue(INICIO.plusDays(7));

        assertThat(fechadas).isEqualTo(1);
        assertThat(taskRepository.findByPatientIdOrderByPeriodStartDesc(paciente.getId()).get(0).getStatus())
                .isEqualTo(ScaleTaskStatus.NAO_RESPONDIDA);
        assertThat(treatmentProtocolService.contarPendenciasAtrasadas(paciente.getId())).isEqualTo(1);
    }

    // cada escala tem o seu proprio prazo. a semanal volta em 7 dias, a
    // mensal n
    @Test
    void cadaEscalaSegueAPropriaPeriodicidade() {
        criaProtocolo(List.of(
                item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL),
                item(ScaleType.ESCALA_PITTSBURGH, Periodicity.MENSAL)));
        treatmentProtocolService.designarEscalasVencidas(INICIO);

        treatmentProtocolService.designarEscalasVencidas(INICIO.plusDays(7));

        assertThat(quantasEnviadas(ScaleType.ACOMPANHAMENTO_SEMANAL)).isEqualTo(2);
        assertThat(quantasEnviadas(ScaleType.ESCALA_PITTSBURGH)).isEqualTo(1);
    }

    // se o servidor ficar fora do ar uns dias, o acompanhamento continua
    // de onde parou em vez de pular a rodada
    @Test
    void recuperaARodadaQuandoOJobFicaDiasSemRodar() {
        criaProtocolo(List.of(item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL)));
        treatmentProtocolService.designarEscalasVencidas(INICIO);

        // o job so volta a rodar 10 dias depois
        int designadas = treatmentProtocolService.designarEscalasVencidas(INICIO.plusDays(10));

        assertThat(designadas).isEqualTo(1);
    }

    @Test
    void encerraSozinhoDepoisDosNoventaDias() {
        criaProtocolo(List.of(item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL)));

        int designadas = treatmentProtocolService.designarEscalasVencidas(INICIO.plusDays(91));

        assertThat(designadas).isZero();
        assertThatThrownBy(() -> treatmentProtocolService.getActiveByPatient(paciente.getId()))
                .hasMessageContaining("Nenhum acompanhamento em andamento");
    }

    @Test
    void naoDesignaAntesDeOAcompanhamentoComecar() {
        criaProtocolo(List.of(item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL)));

        int designadas = treatmentProtocolService.designarEscalasVencidas(INICIO.minusDays(1));

        assertThat(designadas).isZero();
    }

    // dois protocolos ativos designariam a mesma escala duas vezes
    @Test
    void naoDeixaDoisAcompanhamentosAoMesmoTempo() {
        criaProtocolo(List.of(item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL)));

        assertThatThrownBy(() -> criaProtocolo(List.of(item(ScaleType.REGISTRO_DOR, Periodicity.SEMANAL))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já tem um acompanhamento");
    }

    // o MEEM eh aplicado pelo prescritor na consulta, n entra no
    // acompanhamento automatico do paciente (RN09)
    @Test
    void naoDeixaColocarOMiniExameNoAcompanhamento() {
        assertThatThrownBy(() -> criaProtocolo(List.of(
                item(ScaleType.MINI_EXAME_ESTADO_MENTAL, Periodicity.MENSAL))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void depoisDeEncerradoNaoDesignaMais() {
        criaProtocolo(List.of(item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL)));
        treatmentProtocolService.designarEscalasVencidas(INICIO);
        treatmentProtocolService.encerrar(paciente.getId());

        int designadas = treatmentProtocolService.designarEscalasVencidas(INICIO.plusDays(7));

        assertThat(designadas).isZero();
    }
}
