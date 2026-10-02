package dev.uffs.doisag.service;

import dev.uffs.doisag.model.DailyCycle;
import dev.uffs.doisag.repository.DailyCycleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// o dia concluido do job diario gravado no banco de verdade
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DailyCycleTest {

    @Autowired private DailyScheduler dailyScheduler;
    @Autowired private DailyCycleRepository cycleRepository;

    @Test
    void oCicloGravaODiaEDepoisAtualizaAMesmaLinha() {
        // no perfil de teste o job n roda sozinho na subida da api
        assertThat(cycleRepository.count()).isZero();
        LocalDate today = LocalDate.now();

        dailyScheduler.runCycle(today.minusDays(1).atTime(8, 0));
        dailyScheduler.runCycle(today.atTime(8, 0));

        assertThat(cycleRepository.count()).isEqualTo(1);
        DailyCycle cycle = cycleRepository.findById(DailyCycle.ID).orElseThrow();
        assertThat(cycle.getLastCompletedDate()).isEqualTo(today);
    }
}
