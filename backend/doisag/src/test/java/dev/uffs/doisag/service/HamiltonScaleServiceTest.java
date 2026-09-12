package dev.uffs.doisag.service;

import dev.uffs.doisag.model.HamiltonScale;
import dev.uffs.doisag.repository.HamiltonScaleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// teste de unidade do calculo do escore. n sobe spring nem banco,
// so troca o repositorio por um mock e confere a conta
@ExtendWith(MockitoExtension.class)
class HamiltonScaleServiceTest {

    @Mock
    private HamiltonScaleRepository hamiltonScaleRepository;

    @Mock
    private ScaleAssignmentService scaleAssignmentService;

    @InjectMocks
    private HamiltonScaleService hamiltonScaleService;

    // o mock devolve o mesmo objeto q recebeu, ai a gente consegue
    // olhar o escore q o service gravou nele
    private void repositorySavesWhatItGets() {
        when(hamiltonScaleRepository.save(any(HamiltonScale.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldSumEveryItemIntoTheTotalScore() {
        repositorySavesWhatItGets();

        // todos os itens no grau maximo, q eh 4
        HamiltonScale scale = new HamiltonScale();
        scale.setAnxiousMood(4);
        scale.setTension(4);
        scale.setFears(4);
        scale.setInsomnia(4);
        scale.setCognition(4);
        scale.setDepressedMood(4);
        scale.setSomaticMotor(4);
        scale.setSomaticSensory(4);
        scale.setCardiovascularSymptoms(4);
        scale.setRespiratorySymptoms(4);
        scale.setGastrointestinalSymptoms(4);
        scale.setGenitourinarySymptoms(4);
        scale.setAutonomicSymptoms(4);

        HamiltonScale saved = hamiltonScaleService.create(scale);

        // 13 itens x 4 = 52. o instrumento tem 14 itens e vai ate 56,
        // entao esse valor muda quando o RF21 for corrigido
        assertThat(saved.getHamScore()).isEqualTo(52);
    }

    @Test
    void shouldScoreZeroWhenNothingIsAnswered() {
        repositorySavesWhatItGets();

        HamiltonScale emptyScale = new HamiltonScale();

        HamiltonScale saved = hamiltonScaleService.create(emptyScale);

        // formulario vazio hoje pontua 0, q na escala significa
        // "sem ansiedade". eh o problema descrito na RN10: item n
        // respondido tem q ser ausente e n zero
        assertThat(saved.getHamScore()).isZero();
    }
}
