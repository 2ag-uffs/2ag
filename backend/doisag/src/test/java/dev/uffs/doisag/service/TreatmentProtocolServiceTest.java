package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.PilotIndicatorsDTO;
import dev.uffs.doisag.dto.ProtocolItemDTO;
import dev.uffs.doisag.dto.TreatmentProtocolCreateDTO;
import dev.uffs.doisag.dto.TreatmentProtocolResponseDTO;
import dev.uffs.doisag.enums.AuditOperation;
import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.enums.Periodicity;
import dev.uffs.doisag.enums.ScaleTaskStatus;
import dev.uffs.doisag.enums.ScaleType;
import dev.uffs.doisag.infra.BrazilianFormat;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.InvalidFieldException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.AuditEvent;
import dev.uffs.doisag.model.Notification;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.ProtocolItem;
import dev.uffs.doisag.model.ScaleTask;
import dev.uffs.doisag.model.TreatmentProtocol;
import dev.uffs.doisag.repository.AuditEventRepository;
import dev.uffs.doisag.repository.NotificationRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.repository.ScaleTaskRepository;
import dev.uffs.doisag.repository.TreatmentProtocolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    @Autowired private TreatmentProtocolRepository protocolRepository;
    @Autowired private ReminderService reminderService;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private IndicatorsService indicatorsService;
    @Autowired private NotificationRepository notificationRepository;

    // comeca amanha: a primeira rodada fica pro job, q os testes chamam com a data na mao
    // uma data fixa vencia com o calendario, pq o acompanhamento q terminaria antes de hoje eh recusado
    private static final LocalDate INICIO = LocalDate.now().plusDays(1);

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

    // comeca hoje e manda a primeira rodada na hora: encerrar a mao fecha as rodadas no dia de vdd,
    // entao os testes do encerramento n podem ter tarefa q so comeca amanha
    private void criaProtocoloHoje(List<ProtocolItemDTO> itens) {
        treatmentProtocolService.create(
                paciente.getId(),
                new TreatmentProtocolCreateDTO(LocalDate.now(), 90, null, null, itens),
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

    // a avaliacao inicial eh da triagem: no acompanhamento ela voltaria a cada ciclo (RF19)
    @Test
    void anamneseNaoEntraNoAcompanhamento() {
        assertThatThrownBy(() -> criaProtocolo(List.of(item(ScaleType.ANAMNESE, Periodicity.SEMANAL))))
                .isInstanceOf(BusinessException.class)
                .hasMessage(TreatmentProtocolService.SINGLE_SCALE_MESSAGE);
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
        assertThat(treatmentProtocolService.getActiveByPatient(paciente.getId())).isEmpty();
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

    // a renovacao do RF32 no dia seguinte ao fim, antes do job das 8h encerrar o anterior
    @Test
    void renovaNoDiaSeguinteAoFimSemEncerrarNaMao() {
        LocalDate hoje = LocalDate.now();
        criaProtocolo(List.of(item(ScaleType.ESCALA_HAMILTON, Periodicity.SEMANAL)));
        TreatmentProtocol anterior = protocolRepository.findFirstByPatientIdAndActiveTrue(paciente.getId())
                .orElseThrow();
        // terminou ontem e o job ainda n passou por ele
        anterior.setStartDate(hoje.minusDays(90));
        anterior.setEndDate(hoje.minusDays(1));
        protocolRepository.save(anterior);

        TreatmentProtocolResponseDTO novo = treatmentProtocolService.create(paciente.getId(),
                new TreatmentProtocolCreateDTO(hoje, 90, null, null,
                        List.of(item(ScaleType.ESCALA_HAMILTON, Periodicity.SEMANAL))), prescritora);

        assertThat(protocolRepository.findById(anterior.getId()).orElseThrow().isActive()).isFalse();
        assertThat(protocolRepository.findFirstByPatientIdAndActiveTrue(paciente.getId()).orElseThrow().getId())
                .isEqualTo(novo.id());
        assertThat(quantasEnviadas(ScaleType.ESCALA_HAMILTON)).isEqualTo(1);
        // encerrado igual o job encerra, com a trilha
        LocalDateTime agora = LocalDateTime.now();
        assertThat(auditEventRepository.findPatientEvents(paciente.getId(), agora.minusDays(1), agora.plusDays(1),
                PageRequest.of(0, 10)).getContent())
                .anyMatch(evento -> evento.getOperation() == AuditOperation.ALTERACAO
                        && evento.getRecordId().equals(anterior.getId()));
    }

    // o q terminaria antes de hoje nascia vencido, sem rodada nenhuma, e travava o certo ate o job
    @Test
    void naoDeixaOAcompanhamentoNascerVencido() {
        LocalDate hoje = LocalDate.now();

        assertThatThrownBy(() -> treatmentProtocolService.create(paciente.getId(),
                new TreatmentProtocolCreateDTO(hoje.minusDays(90), 90, null, null,
                        List.of(item(ScaleType.ESCALA_HAMILTON, Periodicity.SEMANAL))), prescritora))
                .isInstanceOf(InvalidFieldException.class)
                .hasMessage(TreatmentProtocolService.ENDS_BEFORE_TODAY_MESSAGE)
                .extracting("field").isEqualTo("startDate");
        assertThat(protocolRepository.findFirstByPatientIdAndActiveTrue(paciente.getId())).isEmpty();
    }

    @Test
    void acompanhamentoQueTerminaHojeAindaVale() {
        LocalDate hoje = LocalDate.now();

        TreatmentProtocolResponseDTO criado = treatmentProtocolService.create(paciente.getId(),
                new TreatmentProtocolCreateDTO(hoje.minusDays(89), 90, null, null,
                        List.of(item(ScaleType.ESCALA_HAMILTON, Periodicity.SEMANAL))), prescritora);

        assertThat(criado.endDate()).isEqualTo(hoje);
        assertThat(criado.active()).isTrue();
    }

    // o MEEM eh aplicado pelo prescritor na consulta, n entra no
    // acompanhamento automatico do paciente (RN09)
    @Test
    void naoDeixaColocarOMiniExameNoAcompanhamento() {
        assertThatThrownBy(() -> criaProtocolo(List.of(
                item(ScaleType.MINI_EXAME_ESTADO_MENTAL, Periodicity.MENSAL))))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ScaleTaskService.PRESCRIBER_SCALE_MESSAGE);
    }

    // as duas rodadas adotavam a mesma tarefa e cada uma reescrevia o prazo dela
    @Test
    void naoDeixaAMesmaEscalaDuasVezesNoAcompanhamento() {
        assertThatThrownBy(() -> criaProtocolo(List.of(
                item(ScaleType.ESCALA_HAMILTON, Periodicity.SEMANAL),
                item(ScaleType.ESCALA_HAMILTON, Periodicity.MENSAL))))
                .isInstanceOf(BusinessException.class)
                .hasMessage(TreatmentProtocolService.DUPLICATE_SCALE_MESSAGE);
    }

    private ScaleTask enviaAvulsa(ScaleType escala, LocalDate dia) {
        return scaleTaskService.assign(paciente.getId(), escala, dia, 7, false);
    }

    private void marcaRespondida(ScaleTask tarefa) {
        tarefa.setStatus(ScaleTaskStatus.RESPONDIDA);
        taskRepository.save(tarefa);
    }

    private ScaleTask tarefaDoDia(LocalDate dia) {
        return taskRepository.findByPatientIdOrderByPeriodStartDesc(paciente.getId()).stream()
                .filter(task -> task.getPeriodStart().equals(dia))
                .findFirst()
                .orElseThrow();
    }

    // RF32 a escala avulsa n pode adiantar a rodada do acompanhamento
    @Test
    void escalaAvulsaNaoAdiantaARodadaMensal() {
        criaProtocolo(List.of(item(ScaleType.ESCALA_PITTSBURGH, Periodicity.MENSAL)));
        treatmentProtocolService.designarEscalasVencidas(INICIO);
        marcaRespondida(tarefaDoDia(INICIO));
        marcaRespondida(enviaAvulsa(ScaleType.ESCALA_PITTSBURGH, INICIO.plusDays(5)));

        assertThat(treatmentProtocolService.designarEscalasVencidas(INICIO.plusDays(12))).isZero();
        assertThat(treatmentProtocolService.designarEscalasVencidas(INICIO.plusDays(30))).isEqualTo(1);
        assertThat(quantasEnviadas(ScaleType.ESCALA_PITTSBURGH)).isEqualTo(3);
    }

    @Test
    void avulsaVencidaSemRespostaNaoAdiantaARodada() {
        criaProtocolo(List.of(item(ScaleType.ESCALA_PITTSBURGH, Periodicity.MENSAL)));
        treatmentProtocolService.designarEscalasVencidas(INICIO);
        marcaRespondida(tarefaDoDia(INICIO));
        enviaAvulsa(ScaleType.ESCALA_PITTSBURGH, INICIO.plusDays(5));
        scaleTaskService.closeOverdue(INICIO.plusDays(12));

        assertThat(treatmentProtocolService.designarEscalasVencidas(INICIO.plusDays(12))).isZero();
    }

    @Test
    void marcaAOrigemDaTarefa() {
        criaProtocolo(List.of(item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL)));
        treatmentProtocolService.designarEscalasVencidas(INICIO);

        assertThat(tarefaDoDia(INICIO).isFromProtocol()).isTrue();
        assertThat(taskRepository.findById(scaleTaskService.assign(paciente.getId(), ScaleType.REGISTRO_DOR).id())
                .orElseThrow().isFromProtocol()).isFalse();
    }

    // a mesma escala nunca fica pendente duas vezes, venha de onde vier
    @Test
    void avulsaReaproveitaATarefaDoProtocoloEmAberto() {
        criaProtocolo(List.of(item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL)));
        treatmentProtocolService.designarEscalasVencidas(INICIO);

        ScaleTask avulsa = enviaAvulsa(ScaleType.ACOMPANHAMENTO_SEMANAL, INICIO.plusDays(2));

        assertThat(avulsa.isFromProtocol()).isTrue();
        assertThat(quantasEnviadas(ScaleType.ACOMPANHAMENTO_SEMANAL)).isEqualTo(1);
    }

    // a avulsa aberta na hora da rodada vira a rodada, com o prazo da periodicidade
    @Test
    void protocoloAssumeAAvulsaAindaAberta() {
        criaProtocolo(List.of(item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL)));
        treatmentProtocolService.designarEscalasVencidas(INICIO);
        marcaRespondida(tarefaDoDia(INICIO));
        ScaleTask avulsa = enviaAvulsa(ScaleType.ACOMPANHAMENTO_SEMANAL, INICIO.plusDays(5));

        assertThat(treatmentProtocolService.designarEscalasVencidas(INICIO.plusDays(7))).isEqualTo(1);
        assertThat(quantasEnviadas(ScaleType.ACOMPANHAMENTO_SEMANAL)).isEqualTo(2);
        assertThat(avulsa.isFromProtocol()).isTrue();
        assertThat(avulsa.getPeriodEnd()).isEqualTo(INICIO.plusDays(13));

        assertThat(treatmentProtocolService.designarEscalasVencidas(INICIO.plusDays(12))).isZero();
        scaleTaskService.closeOverdue(INICIO.plusDays(14));
        assertThat(treatmentProtocolService.designarEscalasVencidas(INICIO.plusDays(14))).isEqualTo(1);
        assertThat(quantasEnviadas(ScaleType.ACOMPANHAMENTO_SEMANAL)).isEqualTo(3);
    }

    // o pittsburgh mandado na consulta n pode voltar dois dias dps pelo acompanhamento
    @Test
    void avulsaRespondidaNaPrimeiraRodadaNaoFazOProtocoloMandarDeNovo() {
        criaProtocolo(List.of(item(ScaleType.ESCALA_PITTSBURGH, Periodicity.MENSAL)));
        ScaleTask avulsa = enviaAvulsa(ScaleType.ESCALA_PITTSBURGH, INICIO);

        assertThat(treatmentProtocolService.designarEscalasVencidas(INICIO)).isEqualTo(1);
        assertThat(quantasEnviadas(ScaleType.ESCALA_PITTSBURGH)).isEqualTo(1);
        marcaRespondida(avulsa);

        assertThat(treatmentProtocolService.designarEscalasVencidas(INICIO.plusDays(2))).isZero();
        assertThat(treatmentProtocolService.designarEscalasVencidas(INICIO.plusDays(30))).isEqualTo(1);
        assertThat(quantasEnviadas(ScaleType.ESCALA_PITTSBURGH)).isEqualTo(2);
    }

    // o acompanhamento novo comeca o proprio ciclo com a periodicidade nova, e a rodada
    // mensal do anterior n segura a semanal dele ate o fim do mes
    @Test
    void oAcompanhamentoQueSubstituiOutroNaoFicaPresoNaRodadaDele() {
        criaProtocolo(List.of(item(ScaleType.ESCALA_HAMILTON, Periodicity.MENSAL)));
        treatmentProtocolService.designarEscalasVencidas(INICIO);
        marcaRespondida(tarefaDoDia(INICIO));
        treatmentProtocolService.end(paciente.getId());
        criaProtocolo(List.of(item(ScaleType.ESCALA_HAMILTON, Periodicity.SEMANAL)));

        assertThat(treatmentProtocolService.designarEscalasVencidas(INICIO.plusDays(2))).isEqualTo(1);
        assertThat(treatmentProtocolService.designarEscalasVencidas(INICIO.plusDays(8))).isZero();
        assertThat(treatmentProtocolService.designarEscalasVencidas(INICIO.plusDays(9))).isEqualTo(1);
        assertThat(quantasEnviadas(ScaleType.ESCALA_HAMILTON)).isEqualTo(3);
    }

    // decisao da dona (11/2026): encerrar a mao fecha na hora as rodadas do acompanhamento q ainda esperavam
    // resposta, como a troca e o arquivamento, com o dia da interrupcao marcado. a q estava no prazo e fecha
    // vazia ganha o dia e fica fora das vencidas e do indicador. a escala enviada a mao n eh do acompanhamento
    // e continua. antes encerrar so parava as rodadas novas e as abertas seguiam sendo cobradas ate o prazo
    @Test
    void encerrarAMaoFechaAsRodadasAbertasDoAcompanhamento() {
        LocalDate hoje = LocalDate.now();
        criaProtocoloHoje(List.of(
                item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL),
                item(ScaleType.ESCALA_PITTSBURGH, Periodicity.MENSAL)));
        ScaleTask semanal = tarefaMaisNova(ScaleType.ACOMPANHAMENTO_SEMANAL);
        ScaleTask pittsburgh = tarefaMaisNova(ScaleType.ESCALA_PITTSBURGH);
        ScaleTask avulsa = enviaAvulsa(ScaleType.ESCALA_HAMILTON, hoje);

        treatmentProtocolService.end(paciente.getId());

        for (ScaleTask rodada : List.of(semanal, pittsburgh)) {
            assertThat(rodada.getStatus()).as(rodada.getScaleType().name()).isEqualTo(ScaleTaskStatus.NAO_RESPONDIDA);
            assertThat(rodada.getPeriodEnd()).isEqualTo(hoje);
            assertThat(rodada.getStoppedOn()).isEqualTo(hoje);
            assertThat(rodada.getAnsweredAt()).isEqualTo(hoje);
        }
        assertThat(avulsa.getStatus()).isEqualTo(ScaleTaskStatus.PENDENTE);
        assertThat(avulsa.getPeriodEnd()).isEqualTo(hoje.plusDays(6));
        assertThat(avulsa.getStoppedOn()).isNull();
        assertThat(taskRepository.findByPatientIdAndStatusOrderByPeriodEndAsc(paciente.getId(),
                ScaleTaskStatus.PENDENTE))
                .containsExactly(avulsa);
        // amanha nada do acompanhamento vira escala vencida nem volta a ser enviado
        assertThat(taskRepository.findLateWithoutAnswers(prescritora.getId(),
                List.of(ScaleTaskStatus.PENDENTE, ScaleTaskStatus.NAO_RESPONDIDA), hoje.plusDays(1),
                PageRequest.of(0, 5)))
                .isEmpty();
        assertThat(scaleTaskService.closeOverdue(hoje.plusDays(1))).isZero();
        assertThat(treatmentProtocolService.designarEscalasVencidas(hoje.plusDays(1))).isZero();
    }

    // a rodada q ja tinha vencido antes de encerrar, com o job das 8h ainda sem rodar, foi atraso de vdd:
    // fecha como no job, sem o dia marcado, e segue nas vencidas
    @Test
    void encerrarAMaoNaoPerdoaARodadaQueJaTinhaVencido() {
        LocalDate hoje = LocalDate.now();
        criaProtocoloHoje(List.of(item(ScaleType.ESCALA_PITTSBURGH, Periodicity.MENSAL)));
        ScaleTask pittsburgh = tarefaMaisNova(ScaleType.ESCALA_PITTSBURGH);
        pittsburgh.setPeriodStart(hoje.minusDays(30));
        pittsburgh.setPeriodEnd(hoje.minusDays(1));
        taskRepository.save(pittsburgh);

        treatmentProtocolService.end(paciente.getId());

        assertThat(pittsburgh.getStatus()).isEqualTo(ScaleTaskStatus.NAO_RESPONDIDA);
        assertThat(pittsburgh.getPeriodEnd()).isEqualTo(hoje.minusDays(1));
        assertThat(pittsburgh.getAnsweredAt()).isNull();
        assertThat(taskRepository.findLateWithoutAnswers(prescritora.getId(),
                List.of(ScaleTaskStatus.PENDENTE, ScaleTaskStatus.NAO_RESPONDIDA), hoje, PageRequest.of(0, 5)))
                .containsExactly(pittsburgh);
    }

    // o acompanhamento criado dps de encerrar o anterior a mao manda a propria rodada: a do encerrado fechou
    // e o paciente fica com uma pendencia so. a rodada aberta so vira a primeira do novo na renovacao dos
    // ultimos 7 dias, q n passa pelo encerrar
    @Test
    void aRodadaAbertaDoEncerradoFechaEONovoMandaAPropria() {
        LocalDate hoje = LocalDate.now();
        criaProtocoloHoje(List.of(item(ScaleType.ESCALA_HAMILTON, Periodicity.MENSAL)));
        ScaleTask rodadaAntiga = tarefaMaisNova(ScaleType.ESCALA_HAMILTON);
        treatmentProtocolService.end(paciente.getId());
        criaProtocoloHoje(List.of(item(ScaleType.ESCALA_HAMILTON, Periodicity.SEMANAL)));

        assertThat(rodadaAntiga.getStatus()).isEqualTo(ScaleTaskStatus.NAO_RESPONDIDA);
        assertThat(taskRepository.findByPatientIdAndStatusOrderByPeriodEndAsc(paciente.getId(),
                ScaleTaskStatus.PENDENTE))
                .extracting(ScaleTask::getPeriodEnd)
                .containsExactly(hoje.plusDays(6));
        assertThat(quantasEnviadas(ScaleType.ESCALA_HAMILTON)).isEqualTo(2);
    }

    // encerrar e criar outro no meio da semana n deixa dia sem tarefa: as rodadas do encerrado fecham no dia
    // e o novo manda as dele na hora, cada uma com o fim da propria rodada
    @Test
    void trocarOAcompanhamentoNoMeioDaSemanaNaoDeixaDiaSemTarefa() {
        LocalDate troca = LocalDate.now();
        LocalDate comeco = troca.minusDays(4);
        treatmentProtocolService.create(paciente.getId(),
                new TreatmentProtocolCreateDTO(comeco, 90, null, null, List.of(
                        item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL),
                        item(ScaleType.ESCALA_HAMILTON, Periodicity.SEMANAL),
                        item(ScaleType.ESCALA_PITTSBURGH, Periodicity.MENSAL))), prescritora);
        for (LocalDate dia = comeco; !dia.isAfter(troca); dia = dia.plusDays(1)) {
            rodaOJobDoDia(dia);
        }
        ScaleTask hamiltonDaSemana = tarefaQueCobre(ScaleType.ESCALA_HAMILTON, troca);
        assertThat(hamiltonDaSemana.getReminderSentAt()).isNotNull();

        treatmentProtocolService.end(paciente.getId());
        criaProtocoloHoje(List.of(
                item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL),
                item(ScaleType.ESCALA_HAMILTON, Periodicity.MENSAL),
                item(ScaleType.ESCALA_PITTSBURGH, Periodicity.SEMANAL)));
        rodaOJobDoDia(troca);

        // a hamilton da semana fechou no dia da troca e a rodada mensal do novo eh outra tarefa
        assertThat(hamiltonDaSemana.getStatus()).isEqualTo(ScaleTaskStatus.NAO_RESPONDIDA);
        assertThat(hamiltonDaSemana.getPeriodEnd()).isEqualTo(troca);
        assertThat(hamiltonDaSemana.getStoppedOn()).isEqualTo(troca);
        assertThat(tarefaMaisNova(ScaleType.ESCALA_HAMILTON).getPeriodEnd()).isEqualTo(troca.plusDays(29));
        assertThat(tarefaMaisNova(ScaleType.ESCALA_HAMILTON).getReminderSentAt()).isNull();
        assertThat(tarefaMaisNova(ScaleType.ESCALA_PITTSBURGH).getPeriodEnd()).isEqualTo(troca.plusDays(6));

        for (LocalDate dia = troca; dia.isBefore(troca.plusDays(75)); dia = dia.plusDays(1)) {
            rodaOJobDoDia(dia);
            TreatmentProtocol protocolo = protocolRepository.findFirstByPatientIdAndActiveTrue(paciente.getId())
                    .orElseThrow();
            for (ProtocolItem item : protocolo.getItems()) {
                assertThat(tarefaQueCobre(item.getScaleType(), dia))
                        .as(item.getScaleType() + " no dia " + dia)
                        .isNotNull();
                assertThat(tarefaMaisNova(item.getScaleType()).getPeriodEnd())
                        .as("fim da tarefa e da rodada de " + item.getScaleType() + " no dia " + dia)
                        .isEqualTo(item.getLastRoundEnd());
            }
        }
        // a tarefa fechada pelo encerramento n volta a ser cobrada nem reaberta pelas rodadas do novo
        ScaleTask fechada = taskRepository.findById(hamiltonDaSemana.getId()).orElseThrow();
        assertThat(fechada.getStatus()).isEqualTo(ScaleTaskStatus.NAO_RESPONDIDA);
        assertThat(fechada.getPeriodEnd()).isEqualTo(troca);
    }

    // a escala q o prescritor tirou do acompanhamento novo para de ser cobrada na hora: a rodada dela fecha
    // no dia da troca com o dia marcado, sai de esperando resposta, n vira escala vencida amanha e o job n
    // manda ela de novo. a q ficou segue como rodada do novo. antes a rodada antiga seguia ate o prazo dela
    @Test
    void trocarOAcompanhamentoSemUmaEscalaFechaARodadaDela() {
        LocalDate hoje = LocalDate.now();
        treatmentProtocolService.create(paciente.getId(),
                new TreatmentProtocolCreateDTO(hoje, 90, null, null, List.of(
                        item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL),
                        item(ScaleType.ESCALA_PITTSBURGH, Periodicity.MENSAL))), prescritora);
        ScaleTask pittsburgh = tarefaMaisNova(ScaleType.ESCALA_PITTSBURGH);
        assertThat(pittsburgh.getPeriodEnd()).isEqualTo(hoje.plusDays(29));

        treatmentProtocolService.end(paciente.getId());
        treatmentProtocolService.create(paciente.getId(),
                new TreatmentProtocolCreateDTO(hoje, 90, null, null,
                        List.of(item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL))), prescritora);

        assertThat(pittsburgh.getStatus()).isEqualTo(ScaleTaskStatus.NAO_RESPONDIDA);
        assertThat(pittsburgh.getPeriodEnd()).isEqualTo(hoje);
        assertThat(pittsburgh.getAnsweredAt()).isEqualTo(hoje);
        assertThat(taskRepository.findByPatientIdAndStatusOrderByPeriodEndAsc(paciente.getId(),
                ScaleTaskStatus.PENDENTE))
                .extracting(ScaleTask::getScaleType)
                .containsExactly(ScaleType.ACOMPANHAMENTO_SEMANAL);
        assertThat(taskRepository.findLateWithoutAnswers(prescritora.getId(),
                List.of(ScaleTaskStatus.PENDENTE, ScaleTaskStatus.NAO_RESPONDIDA), hoje.plusDays(1),
                PageRequest.of(0, 5)))
                .isEmpty();
        assertThat(scaleTaskService.closeOverdue(hoje.plusDays(1))).isZero();
        assertThat(treatmentProtocolService.designarEscalasVencidas(hoje.plusDays(1))).isZero();
        assertThat(quantasEnviadas(ScaleType.ESCALA_PITTSBURGH)).isEqualTo(1);
    }

    // a rodada da escala tirada q ja tinha vencido antes da troca, com o job das 8h ainda sem rodar, foi
    // atraso de vdd: ela fecha como no job, sem o dia marcado, e segue nas vencidas e no indicador
    @Test
    void trocarOAcompanhamentoNaoPerdoaARodadaQueJaTinhaVencido() {
        LocalDate hoje = LocalDate.now();
        treatmentProtocolService.create(paciente.getId(),
                new TreatmentProtocolCreateDTO(hoje, 90, null, null,
                        List.of(item(ScaleType.ESCALA_PITTSBURGH, Periodicity.MENSAL))), prescritora);
        ScaleTask pittsburgh = tarefaMaisNova(ScaleType.ESCALA_PITTSBURGH);
        pittsburgh.setPeriodStart(hoje.minusDays(30));
        pittsburgh.setPeriodEnd(hoje.minusDays(1));
        taskRepository.save(pittsburgh);

        treatmentProtocolService.end(paciente.getId());
        treatmentProtocolService.create(paciente.getId(),
                new TreatmentProtocolCreateDTO(hoje, 90, null, null,
                        List.of(item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL))), prescritora);

        assertThat(pittsburgh.getStatus()).isEqualTo(ScaleTaskStatus.NAO_RESPONDIDA);
        assertThat(pittsburgh.getPeriodEnd()).isEqualTo(hoje.minusDays(1));
        assertThat(pittsburgh.getAnsweredAt()).isNull();
        assertThat(taskRepository.findLateWithoutAnswers(prescritora.getId(),
                List.of(ScaleTaskStatus.PENDENTE, ScaleTaskStatus.NAO_RESPONDIDA), hoje, PageRequest.of(0, 5)))
                .containsExactly(pittsburgh);
        PilotIndicatorsDTO indicadores = indicatorsService.getIndicators(prescritora.getId(), hoje.minusDays(1),
                hoje.minusDays(1));
        assertThat(indicadores.formsDue()).isEqualTo(1);
        assertThat(indicadores.formsOnTime()).isZero();
    }

    // a escala enviada a mao n eh do acompanhamento, entao a troca n mexe nela
    @Test
    void trocarOAcompanhamentoNaoFechaAEscalaEnviadaAMao() {
        LocalDate hoje = LocalDate.now();
        treatmentProtocolService.create(paciente.getId(),
                new TreatmentProtocolCreateDTO(hoje, 90, null, null,
                        List.of(item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL))), prescritora);
        ScaleTask avulsa = enviaAvulsa(ScaleType.ESCALA_PITTSBURGH, hoje);

        treatmentProtocolService.end(paciente.getId());
        treatmentProtocolService.create(paciente.getId(),
                new TreatmentProtocolCreateDTO(hoje, 90, null, null,
                        List.of(item(ScaleType.ESCALA_HAMILTON, Periodicity.SEMANAL))), prescritora);

        assertThat(avulsa.getStatus()).isEqualTo(ScaleTaskStatus.PENDENTE);
        assertThat(avulsa.getPeriodEnd()).isEqualTo(hoje.plusDays(6));
        assertThat(tarefaMaisNova(ScaleType.ACOMPANHAMENTO_SEMANAL).getStatus()).isEqualTo(ScaleTaskStatus.NAO_RESPONDIDA);
    }

    // a ordem do job de verdade: fecha o q venceu, manda a rodada e os lembretes
    private void rodaOJobDoDia(LocalDate dia) {
        scaleTaskService.closeOverdue(dia);
        treatmentProtocolService.designarEscalasVencidas(dia);
        reminderService.sendScaleReminders(dia);
    }

    private ScaleTask tarefaQueCobre(ScaleType escala, LocalDate dia) {
        return taskRepository.findByPatientIdOrderByPeriodStartDesc(paciente.getId()).stream()
                .filter(task -> task.getScaleType() == escala && task.coversDay(dia))
                .findFirst()
                .orElse(null);
    }

    private ScaleTask tarefaMaisNova(ScaleType escala) {
        return taskRepository.findByPatientIdOrderByPeriodStartDesc(paciente.getId()).stream()
                .filter(task -> task.getScaleType() == escala)
                .findFirst()
                .orElseThrow();
    }

    // sem o prescritor ninguem le o q o paciente responde
    @Test
    void naoDesignaParaPacienteDePrescritorDesativado() {
        criaProtocolo(List.of(item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL)));
        prescritora.setActive(false);
        prescriberRepository.save(prescritora);

        assertThat(treatmentProtocolService.designarEscalasVencidas(INICIO)).isZero();
    }

    @Test
    void contaDePacienteDesativadaPausaOEnvioEReativadaContinua() {
        criaProtocolo(List.of(item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL)));
        paciente.setActive(false);
        patientRepository.save(paciente);

        assertThat(treatmentProtocolService.designarEscalasVencidas(INICIO)).isZero();

        paciente.setActive(true);
        patientRepository.save(paciente);

        assertThat(treatmentProtocolService.designarEscalasVencidas(INICIO.plusDays(3))).isEqualTo(1);
    }

    @Test
    void protocoloVencidoEncerraMesmoComAContaDesativada() {
        criaProtocolo(List.of(item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL)));
        paciente.setActive(false);
        patientRepository.save(paciente);

        treatmentProtocolService.designarEscalasVencidas(INICIO.plusDays(91));

        assertThat(treatmentProtocolService.getActiveByPatient(paciente.getId())).isEmpty();
    }

    // o acompanhamento q comeca hoje n espera o job de amanha
    @Test
    void acompanhamentoQueComecaHojeMandaAPrimeiraRodadaNaCriacao() {
        LocalDate hoje = LocalDate.now();
        treatmentProtocolService.create(paciente.getId(),
                new TreatmentProtocolCreateDTO(hoje, 90, null, null,
                        List.of(item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL))), prescritora);

        assertThat(quantasEnviadas(ScaleType.ACOMPANHAMENTO_SEMANAL)).isEqualTo(1);
        assertThat(treatmentProtocolService.designarEscalasVencidas(hoje)).isZero();
        assertThat(treatmentProtocolService.getActiveByPatient(paciente.getId()).orElseThrow().endDate())
                .isEqualTo(hoje.plusDays(89));
    }

    // a conta desativada ganha o acompanhamento, mas a rodada de hoje so sai qnd ela voltar
    @Test
    void contaDesativadaGanhaOAcompanhamentoSemARodadaDeHoje() {
        paciente.setActive(false);
        patientRepository.save(paciente);

        treatmentProtocolService.create(paciente.getId(),
                new TreatmentProtocolCreateDTO(LocalDate.now(), 90, null, null,
                        List.of(item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL))), prescritora);

        assertThat(protocolRepository.findFirstByPatientIdAndActiveTrue(paciente.getId())).isPresent();
        assertThat(quantasEnviadas(ScaleType.ACOMPANHAMENTO_SEMANAL)).isZero();
    }

    @Test
    void usaADuracaoEscolhidaPeloPrescritor() {
        TreatmentProtocolResponseDTO criado = treatmentProtocolService.create(paciente.getId(),
                new TreatmentProtocolCreateDTO(INICIO, 30, null, null,
                        List.of(item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL))), prescritora);

        assertThat(criado.endDate()).isEqualTo(INICIO.plusDays(29));
    }

    // o de 30 dias encerra no dia 31 e n espera os 90 do padrao
    @Test
    void acompanhamentoDeTrintaDiasEncerraNoDiaSeguinteAoFim() {
        treatmentProtocolService.create(paciente.getId(),
                new TreatmentProtocolCreateDTO(INICIO, 30, null, null,
                        List.of(item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL))), prescritora);

        assertThat(treatmentProtocolService.designarEscalasVencidas(INICIO.plusDays(30))).isZero();
        assertThat(treatmentProtocolService.getActiveByPatient(paciente.getId())).isEmpty();
    }

    // os horarios q aparecem no topo do diario do sono (RF22)
    @Test
    void guardaOsHorariosDeDeitarEDeAcordar() {
        TreatmentProtocolResponseDTO criado = treatmentProtocolService.create(paciente.getId(),
                new TreatmentProtocolCreateDTO(INICIO, 90, LocalTime.of(22, 30), LocalTime.of(6, 45),
                        List.of(item(ScaleType.REGISTRO_SONO, Periodicity.SEMANAL))), prescritora);

        assertThat(criado.sleepBedTime()).isEqualTo(LocalTime.of(22, 30));
        assertThat(criado.sleepWakeTime()).isEqualTo(LocalTime.of(6, 45));
    }

    // os horarios sao a programacao do diario do sono. a tela ja n manda eles sem o diario, mas a api
    // guardava o q chegasse, e o paciente leria no diario enviado a mao um horario q o prescritor n programou
    @Test
    void semODiarioDoSonoOsHorariosNaoSaoGuardados() {
        TreatmentProtocolResponseDTO criado = treatmentProtocolService.create(paciente.getId(),
                new TreatmentProtocolCreateDTO(INICIO, 90, LocalTime.of(22, 30), LocalTime.of(6, 45),
                        List.of(item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL))), prescritora);

        assertThat(criado.sleepBedTime()).isNull();
        assertThat(criado.sleepWakeTime()).isNull();
        TreatmentProtocol guardado = protocolRepository.findFirstByPatientIdAndActiveTrue(paciente.getId())
                .orElseThrow();
        assertThat(guardado.getSleepBedTime()).isNull();
        assertThat(guardado.getSleepWakeTime()).isNull();
    }

    // RF31 a criacao do acompanhamento fica na trilha do paciente
    @Test
    void registraACriacaoNaTrilhaDoPaciente() {
        TreatmentProtocolResponseDTO criado = treatmentProtocolService.create(paciente.getId(),
                new TreatmentProtocolCreateDTO(INICIO, 90, null, null,
                        List.of(item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL))), prescritora);

        LocalDateTime agora = LocalDateTime.now();
        List<AuditEvent> eventos = auditEventRepository.findPatientEvents(paciente.getId(), agora.minusDays(1),
                agora.plusDays(1), PageRequest.of(0, 10)).getContent();
        assertThat(eventos).hasSize(1);
        assertThat(eventos.get(0).getOperation()).isEqualTo(AuditOperation.CRIACAO);
        assertThat(eventos.get(0).getRecordType()).isEqualTo(AuditRecordType.ACOMPANHAMENTO_AUTOMATICO);
        assertThat(eventos.get(0).getRecordId()).isEqualTo(criado.id());
    }

    // o acompanhamento acabava no dia 90 sem ninguem saber, e as escalas paravam de chegar. a prescritora
    // recebe um aviso 7 dias antes e outro no dia do fim, cada um uma vez so, com o link pra renovar
    // (decisao de 14/11/2026, revisao das regras R5)
    @Test
    void avisaAPrescritoraSeteDiasAntesDoFimENoDiaDoFim() {
        criaProtocolo(List.of(item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL)));
        LocalDate fim = INICIO.plusDays(89);

        treatmentProtocolService.designarEscalasVencidas(fim.minusDays(8));
        assertThat(avisosDaPrescritora()).isEmpty();

        treatmentProtocolService.designarEscalasVencidas(fim.minusDays(7));
        treatmentProtocolService.designarEscalasVencidas(fim.minusDays(6));
        assertThat(avisosDaPrescritora()).hasSize(1);
        Notification perto = avisosDaPrescritora().get(0);
        assertThat(perto.getTitle()).isEqualTo("Acompanhamento perto do fim");
        assertThat(perto.getMessage()).isEqualTo("O acompanhamento automático de Paciente do Protocolo termina em "
                + BrazilianFormat.date(fim) + ". Renove para as escalas continuarem chegando.");
        assertThat(perto.getLink()).isEqualTo("/paciente/" + paciente.getId() + "/acompanhamento");

        treatmentProtocolService.designarEscalasVencidas(fim);
        treatmentProtocolService.designarEscalasVencidas(fim);
        assertThat(avisosDaPrescritora()).extracting(Notification::getTitle)
                .containsExactlyInAnyOrder("Acompanhamento perto do fim", "Acompanhamento termina hoje");

        // no dia seguinte o job encerra sem avisar de novo
        treatmentProtocolService.designarEscalasVencidas(fim.plusDays(1));
        assertThat(treatmentProtocolService.getActiveByPatient(paciente.getId())).isEmpty();
        assertThat(avisosDaPrescritora()).hasSize(2);
    }

    // com a api fora do ar no dia do fim, o aviso sai no dia em q o job encerra, dizendo q ja terminou
    @Test
    void oAvisoDoFimSaiMesmoSeOJobNaoRodouNoDia() {
        criaProtocolo(List.of(item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL)));
        LocalDate fim = INICIO.plusDays(89);

        treatmentProtocolService.designarEscalasVencidas(fim.plusDays(2));

        assertThat(treatmentProtocolService.getActiveByPatient(paciente.getId())).isEmpty();
        assertThat(avisosDaPrescritora()).extracting(Notification::getMessage)
                .containsExactly("O acompanhamento automático de Paciente do Protocolo terminou em "
                        + BrazilianFormat.date(fim) + ". Renove para as escalas voltarem a chegar.");
    }

    // o acompanhamento de 7 dias ja nasce na ultima semana, entao so recebe o aviso do dia do fim
    @Test
    void acompanhamentoDeUmaSemanaSoAvisaNoDiaDoFim() {
        treatmentProtocolService.create(paciente.getId(), new TreatmentProtocolCreateDTO(INICIO, 7, null, null,
                List.of(item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL))), prescritora);

        for (int dia = 0; dia < 7; dia++) {
            treatmentProtocolService.designarEscalasVencidas(INICIO.plusDays(dia));
        }

        assertThat(avisosDaPrescritora()).extracting(Notification::getTitle)
                .containsExactly("Acompanhamento termina hoje");
    }

    // a renovacao abre nos ultimos 7 dias, junto com o aviso: o novo comeca hoje no lugar do atual, e a escala
    // q fica com a mesma frequencia segue a rodada de agora, sem chegar de novo nem ganhar prazo novo
    @Test
    void renovaNosUltimosSeteDiasSemRepetirAEscala() {
        LocalDate hoje = LocalDate.now();
        treatmentProtocolService.create(paciente.getId(), new TreatmentProtocolCreateDTO(hoje.minusDays(85), 90,
                null, null, List.of(item(ScaleType.ESCALA_HAMILTON, Periodicity.SEMANAL),
                item(ScaleType.ESCALA_PITTSBURGH, Periodicity.MENSAL))), prescritora);
        TreatmentProtocol atual = protocolRepository.findFirstByPatientIdAndActiveTrue(paciente.getId())
                .orElseThrow();
        treatmentProtocolService.designarEscalasVencidas(hoje);
        marcaRespondida(tarefaMaisNova(ScaleType.ESCALA_PITTSBURGH));

        TreatmentProtocolResponseDTO novo = treatmentProtocolService.create(paciente.getId(),
                new TreatmentProtocolCreateDTO(hoje, 90, null, null,
                        List.of(item(ScaleType.ESCALA_HAMILTON, Periodicity.SEMANAL),
                                item(ScaleType.ESCALA_PITTSBURGH, Periodicity.MENSAL))), prescritora);

        assertThat(protocolRepository.findById(atual.getId()).orElseThrow().isActive()).isFalse();
        assertThat(novo.active()).isTrue();
        assertThat(novo.endDate()).isEqualTo(hoje.plusDays(89));
        assertThat(quantasEnviadas(ScaleType.ESCALA_HAMILTON)).isEqualTo(1);
        assertThat(quantasEnviadas(ScaleType.ESCALA_PITTSBURGH)).isEqualTo(1);
        assertThat(tarefaMaisNova(ScaleType.ESCALA_HAMILTON).getPeriodEnd()).isEqualTo(hoje.plusDays(6));
        assertThat(treatmentProtocolService.designarEscalasVencidas(hoje.plusDays(6))).isZero();
        assertThat(treatmentProtocolService.designarEscalasVencidas(hoje.plusDays(7))).isEqualTo(1);
        assertThat(treatmentProtocolService.designarEscalasVencidas(hoje.plusDays(30))).isEqualTo(2);
    }

    // dps do fim o acompanhamento conta como encerrado, com o job das 8h ja rodado ou n: a renovacao eh um
    // ciclo novo e a primeira rodada sai de novo. antes, no dia seguinte ao fim e antes do job, ela herdava a
    // rodada do anterior, e dps do job n herdava (decisao de 17/11/2026)
    @Test
    void aRenovacaoDepoisDoFimEhCicloNovoAntesDoJob() {
        LocalDate hoje = LocalDate.now();
        TreatmentProtocol anterior = acompanhamentoQueTerminouOntemComAMensalRespondida();

        TreatmentProtocolResponseDTO novo = renova(hoje);

        assertThat(protocolRepository.findById(anterior.getId()).orElseThrow().isActive()).isFalse();
        assertThat(novo.active()).isTrue();
        assertThat(quantasEnviadas(ScaleType.ESCALA_PITTSBURGH)).isEqualTo(2);
        assertThat(tarefaMaisNova(ScaleType.ESCALA_PITTSBURGH).getPeriodStart()).isEqualTo(hoje);
    }

    @Test
    void aRenovacaoDepoisDoFimEhCicloNovoDepoisDoJob() {
        LocalDate hoje = LocalDate.now();
        TreatmentProtocol anterior = acompanhamentoQueTerminouOntemComAMensalRespondida();
        treatmentProtocolService.designarEscalasVencidas(hoje);
        assertThat(protocolRepository.findById(anterior.getId()).orElseThrow().isActive()).isFalse();

        renova(hoje);

        assertThat(quantasEnviadas(ScaleType.ESCALA_PITTSBURGH)).isEqualTo(2);
        assertThat(tarefaMaisNova(ScaleType.ESCALA_PITTSBURGH).getPeriodStart()).isEqualTo(hoje);
    }

    // a rodada mensal saiu 4 dias atras e foi respondida, entao ela ainda estava correndo qnd o acompanhamento
    // terminou, ontem
    private TreatmentProtocol acompanhamentoQueTerminouOntemComAMensalRespondida() {
        LocalDate hoje = LocalDate.now();
        treatmentProtocolService.create(paciente.getId(), new TreatmentProtocolCreateDTO(hoje.minusDays(64), 90,
                null, null, List.of(item(ScaleType.ESCALA_PITTSBURGH, Periodicity.MENSAL))), prescritora);
        treatmentProtocolService.designarEscalasVencidas(hoje.minusDays(4));
        marcaRespondida(tarefaMaisNova(ScaleType.ESCALA_PITTSBURGH));
        TreatmentProtocol anterior = protocolRepository.findFirstByPatientIdAndActiveTrue(paciente.getId())
                .orElseThrow();
        anterior.setStartDate(hoje.minusDays(90));
        anterior.setEndDate(hoje.minusDays(1));
        return protocolRepository.save(anterior);
    }

    private TreatmentProtocolResponseDTO renova(LocalDate hoje) {
        return treatmentProtocolService.create(paciente.getId(), new TreatmentProtocolCreateDTO(hoje, 90, null,
                null, List.of(item(ScaleType.ESCALA_PITTSBURGH, Periodicity.MENSAL))), prescritora);
    }

    // antes da ultima semana segue valendo um acompanhamento por vez
    @Test
    void naoRenovaAntesDosUltimosSeteDias() {
        LocalDate hoje = LocalDate.now();
        treatmentProtocolService.create(paciente.getId(), new TreatmentProtocolCreateDTO(hoje.minusDays(81), 90,
                null, null, List.of(item(ScaleType.ESCALA_HAMILTON, Periodicity.SEMANAL))), prescritora);

        assertThatThrownBy(() -> treatmentProtocolService.create(paciente.getId(),
                new TreatmentProtocolCreateDTO(hoje, 90, null, null,
                        List.of(item(ScaleType.ESCALA_HAMILTON, Periodicity.SEMANAL))), prescritora))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já tem um acompanhamento");
    }

    // o atual acaba na renovacao, entao o novo q comecasse dps de hoje deixava dias sem escala
    @Test
    void aRenovacaoAntesDoFimComecaHoje() {
        LocalDate hoje = LocalDate.now();
        treatmentProtocolService.create(paciente.getId(), new TreatmentProtocolCreateDTO(hoje.minusDays(87), 90,
                null, null, List.of(item(ScaleType.ESCALA_HAMILTON, Periodicity.SEMANAL))), prescritora);

        assertThatThrownBy(() -> treatmentProtocolService.create(paciente.getId(),
                new TreatmentProtocolCreateDTO(hoje.plusDays(1), 90, null, null,
                        List.of(item(ScaleType.ESCALA_HAMILTON, Periodicity.SEMANAL))), prescritora))
                .isInstanceOf(InvalidFieldException.class)
                .hasMessage(TreatmentProtocolService.RENEWAL_START_MESSAGE)
                .extracting("field").isEqualTo("startDate");
        assertThat(protocolRepository.findFirstByPatientIdAndActiveTrue(paciente.getId())).isPresent();
    }

    // a tela preenche a renovacao com o ultimo acompanhamento, mesmo qnd o job ja encerrou ele
    @Test
    void oUltimoAcompanhamentoVoltaMesmoEncerrado() {
        assertThat(treatmentProtocolService.getLastByPatient(paciente.getId())).isEmpty();
        treatmentProtocolService.create(paciente.getId(),
                new TreatmentProtocolCreateDTO(INICIO, 90, LocalTime.of(22, 30), LocalTime.of(6, 45),
                        List.of(item(ScaleType.REGISTRO_SONO, Periodicity.SEMANAL))), prescritora);
        treatmentProtocolService.end(paciente.getId());

        TreatmentProtocolResponseDTO ultimo = treatmentProtocolService.getLastByPatient(paciente.getId())
                .orElseThrow();

        assertThat(ultimo.active()).isFalse();
        assertThat(ultimo.sleepBedTime()).isEqualTo(LocalTime.of(22, 30));
        assertThat(ultimo.items()).extracting(ProtocolItemDTO::scaleType).containsExactly(ScaleType.REGISTRO_SONO);
    }

    private List<Notification> avisosDaPrescritora() {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(prescritora.getId());
    }

    @Test
    void depoisDeEncerradoNaoDesignaMais() {
        criaProtocoloHoje(List.of(item(ScaleType.ACOMPANHAMENTO_SEMANAL, Periodicity.SEMANAL)));
        treatmentProtocolService.end(paciente.getId());

        int designadas = treatmentProtocolService.designarEscalasVencidas(LocalDate.now().plusDays(7));

        assertThat(designadas).isZero();
    }
}
