package dev.uffs.doisag.service;

import dev.uffs.doisag.model.DailyCycle;
import dev.uffs.doisag.repository.DailyCycleRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// o gatilho das tarefas do dia: uma etapa n derruba a outra e a api q voltou
// dps do horario roda o q ficou pra tras
class DailySchedulerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);
    private static final String EVERY_DAY_AT_EIGHT = "0 0 8 * * *";

    private final TreatmentProtocolService treatmentProtocolService = mock(TreatmentProtocolService.class);
    private final ScaleTaskService scaleTaskService = mock(ScaleTaskService.class);
    private final ReminderService reminderService = mock(ReminderService.class);
    private final AppointmentService appointmentService = mock(AppointmentService.class);
    private final DailyCycleRepository cycleRepository = mock(DailyCycleRepository.class);

    private DailyScheduler schedulerWith(String cron) {
        return new DailyScheduler(treatmentProtocolService, scaleTaskService, reminderService, appointmentService,
                cycleRepository, cron);
    }

    private void lastCompletedDayIs(LocalDate day) {
        DailyCycle cycle = new DailyCycle();
        cycle.setLastCompletedDate(day);
        when(cycleRepository.findById(DailyCycle.ID)).thenReturn(Optional.of(cycle));
    }

    @Test
    void etapaQueQuebraNaoParaAsOutrasENaoMarcaODia() {
        when(scaleTaskService.closeOverdue(TODAY)).thenThrow(new IllegalStateException("falha de teste"));
        LocalDateTime now = TODAY.atTime(8, 0);

        schedulerWith(EVERY_DAY_AT_EIGHT).runCycle(now);

        verify(treatmentProtocolService).designarEscalasVencidas(TODAY);
        verify(appointmentService).declineExpiredRequests(now);
        verify(reminderService).sendAppointmentReminders(TODAY);
        verify(reminderService).sendScaleReminders(TODAY);
        verify(cycleRepository, never()).save(any());
    }

    @Test
    void etapaDoMeioQueQuebraNaoImpedeOsLembretes() {
        when(treatmentProtocolService.designarEscalasVencidas(TODAY))
                .thenThrow(new IllegalStateException("falha de teste"));

        schedulerWith(EVERY_DAY_AT_EIGHT).runCycle(TODAY.atTime(8, 0));

        verify(scaleTaskService).closeOverdue(TODAY);
        verify(reminderService).sendAppointmentReminders(TODAY);
        verify(reminderService).sendScaleReminders(TODAY);
        verify(cycleRepository, never()).save(any());
    }

    @Test
    void cicloSemFalhaGravaODia() {
        when(cycleRepository.findById(DailyCycle.ID)).thenReturn(Optional.empty());

        schedulerWith(EVERY_DAY_AT_EIGHT).runCycle(TODAY.atTime(8, 0));

        ArgumentCaptor<DailyCycle> savedCycle = ArgumentCaptor.forClass(DailyCycle.class);
        verify(cycleRepository).save(savedCycle.capture());
        assertThat(savedCycle.getValue().getLastCompletedDate()).isEqualTo(TODAY);
    }

    // a api estava fora do ar as 8h e voltou as 9h
    @Test
    void apiQueSobeDepoisDoHorarioRodaOCicloPerdido() {
        lastCompletedDayIs(TODAY.minusDays(1));

        schedulerWith(EVERY_DAY_AT_EIGHT).runMissedCycle(TODAY.atTime(9, 0));

        verify(reminderService).sendAppointmentReminders(TODAY);
        verify(scaleTaskService).closeOverdue(TODAY);
    }

    @Test
    void primeiraSubidaDepoisDoHorarioTambemRoda() {
        when(cycleRepository.findById(DailyCycle.ID)).thenReturn(Optional.empty());

        schedulerWith(EVERY_DAY_AT_EIGHT).runMissedCycle(TODAY.atTime(9, 0));

        verify(reminderService).sendAppointmentReminders(TODAY);
    }

    // antes das 8h quem roda eh o agendamento, senao o lembrete sairia de madrugada
    @Test
    void apiQueSobeAntesDoHorarioEsperaOAgendamento() {
        lastCompletedDayIs(TODAY.minusDays(1));

        schedulerWith(EVERY_DAY_AT_EIGHT).runMissedCycle(TODAY.atTime(7, 30));

        verifyNoInteractions(treatmentProtocolService, scaleTaskService, reminderService, appointmentService);
    }

    @Test
    void diaJaProcessadoNaoRodaDeNovoNaSubida() {
        lastCompletedDayIs(TODAY);

        schedulerWith(EVERY_DAY_AT_EIGHT).runMissedCycle(TODAY.atTime(10, 0));

        verifyNoInteractions(treatmentProtocolService, scaleTaskService, reminderService, appointmentService);
    }

    // com o agendamento desligado, q eh o caso dos testes, nada roda sozinho na subida
    @Test
    void agendamentoDesligadoNaoRodaNadaNaSubida() {
        schedulerWith("-").runMissedCycle(TODAY.atTime(10, 0));

        verifyNoInteractions(treatmentProtocolService, scaleTaskService, reminderService, appointmentService,
                cycleRepository);
    }

    @Test
    void falhaAoLerOUltimoCicloNaoDerrubaASubida() {
        when(cycleRepository.findById(DailyCycle.ID)).thenThrow(new IllegalStateException("banco fora"));

        assertThatCode(() -> schedulerWith(EVERY_DAY_AT_EIGHT).runMissedCycle(TODAY.atTime(10, 0)))
                .doesNotThrowAnyException();

        verifyNoInteractions(reminderService);
    }
}
