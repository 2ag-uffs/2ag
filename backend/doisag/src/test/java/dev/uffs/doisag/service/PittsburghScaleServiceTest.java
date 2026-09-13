package dev.uffs.doisag.service;

import dev.uffs.doisag.model.PittsburghScale;
import dev.uffs.doisag.repository.PittsburghScaleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// o indice global do psqi vai de 0 a 21 e sai de 7 componentes
// derivados, n da soma das respostas. como a prescritora usa esse
// numero pra ajustar dose, ele precisa bater com o instrumento
@ExtendWith(MockitoExtension.class)
class PittsburghScaleServiceTest {

    @Mock
    private PittsburghScaleRepository pittsburghScaleRepository;

    @Mock
    private ScaleAssignmentService scaleAssignmentService;

    @InjectMocks
    private PittsburghScaleService pittsburghScaleService;

    private void repositorioDevolveOQueRecebe() {
        when(pittsburghScaleRepository.save(any(PittsburghScale.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // dorme bem: adormece rapido, dorme 8h das 8h30 na cama, nada
    // atrapalha, n toma remedio e passa bem o dia.
    // os 7 componentes dao 0, entao o indice eh 0
    private PittsburghScale bomDormidor() {
        PittsburghScale scale = new PittsburghScale();
        scale.setSleepQualityRating(0);        // C1 = 0, muito boa
        scale.setMinutesToFallAsleep(10);      // C2: <= 15 vale 0
        scale.setFreqCannotFallAsleep(0);      //     + 0 = 0
        scale.setActualSleepHours(8.0f);       // C3 = 0, mais de 7h
        scale.setUsualBedTime(LocalTime.of(23, 0));
        scale.setUsualWakeUpTime(LocalTime.of(7, 30)); // 8h30 na cama
        // C4: 8 / 8,5 = 94%, acima de 85 vale 0
        scale.setFreqWakesUpMiddleNight(0);
        scale.setFreqWakeUpForBathroom(0);
        scale.setFreqCannotBreathe(0);
        scale.setFreqCoughOrSnore(0);
        scale.setFreqFeelCold(0);
        scale.setFreqFeelHot(0);
        scale.setFreqHaveBadDreams(0);
        scale.setFreqHavePain(0);
        scale.setFreqOtherReason(0);           // C5 = 0
        scale.setFreqUseSleepMedication(0);    // C6 = 0
        scale.setFreqTroubleStayingAwake(0);
        scale.setTroubleWithEnthusiasm(0);     // C7 = 0
        return scale;
    }

    @Test
    void quemDormeBemFicaComIndiceZero() {
        repositorioDevolveOQueRecebe();

        PittsburghScale salva = pittsburghScaleService.create(bomDormidor());

        assertThat(salva.getPsqiScore()).isZero();
    }

    // caso de referencia, com os 7 componentes calculados na mao:
    //   C1 qualidade "boa"                                  = 1
    //   C2 latencia: 40 min vale 2, mais frequencia 1 = 3   = 2
    //   C3 duracao: 5,5 horas, entre 5 e 6                  = 2
    //   C4 eficiencia: 5,5 / 8 = 68,75%, entre 65 e 74      = 2
    //   C5 disturbios: soma 4, entre 1 e 9                  = 1
    //   C6 medicacao: nunca usou                            = 0
    //   C7 disfuncao diurna: 1 + 2 = 3, entre 3 e 4         = 2
    //   -------------------------------------------------------
    //   indice global                                       = 10
    @Test
    void calculaOIndiceGlobalPelosSeteComponentes() {
        repositorioDevolveOQueRecebe();

        PittsburghScale scale = new PittsburghScale();
        scale.setSleepQualityRating(1);
        scale.setMinutesToFallAsleep(40);
        scale.setFreqCannotFallAsleep(1);
        scale.setActualSleepHours(5.5f);
        scale.setUsualBedTime(LocalTime.of(23, 0));
        scale.setUsualWakeUpTime(LocalTime.of(7, 0));
        scale.setFreqWakesUpMiddleNight(1);
        scale.setFreqWakeUpForBathroom(1);
        scale.setFreqCannotBreathe(0);
        scale.setFreqCoughOrSnore(0);
        scale.setFreqFeelCold(0);
        scale.setFreqFeelHot(0);
        scale.setFreqHaveBadDreams(1);
        scale.setFreqHavePain(1);
        scale.setFreqOtherReason(0);
        scale.setFreqUseSleepMedication(0);
        scale.setFreqTroubleStayingAwake(1);
        scale.setTroubleWithEnthusiasm(2);

        PittsburghScale salva = pittsburghScaleService.create(scale);

        assertThat(salva.getPsqiScore()).isEqualTo(10);
        // acima de 5 eh o corte publicado pra qualidade de sono ruim
        assertThat(salva.getPsqiScore()).isGreaterThan(5);
    }

    // o indice n pode passar de 21 nem que a pessoa responda tudo no
    // pior valor possivel. era ai que a soma crua ia ate 39
    @Test
    void oIndiceNuncaPassaDeVinteEUm() {
        repositorioDevolveOQueRecebe();

        PittsburghScale scale = new PittsburghScale();
        scale.setSleepQualityRating(3);
        scale.setMinutesToFallAsleep(120);
        scale.setFreqCannotFallAsleep(3);
        scale.setActualSleepHours(2.0f);
        scale.setUsualBedTime(LocalTime.of(22, 0));
        scale.setUsualWakeUpTime(LocalTime.of(8, 0));
        scale.setFreqWakesUpMiddleNight(3);
        scale.setFreqWakeUpForBathroom(3);
        scale.setFreqCannotBreathe(3);
        scale.setFreqCoughOrSnore(3);
        scale.setFreqFeelCold(3);
        scale.setFreqFeelHot(3);
        scale.setFreqHaveBadDreams(3);
        scale.setFreqHavePain(3);
        scale.setFreqOtherReason(3);
        scale.setFreqUseSleepMedication(3);
        scale.setFreqTroubleStayingAwake(3);
        scale.setTroubleWithEnthusiasm(3);

        PittsburghScale salva = pittsburghScaleService.create(scale);

        assertThat(salva.getPsqiScore()).isEqualTo(21);
    }

    // sem o horario de deitar n da pra calcular a eficiencia do sono, e
    // sem um componente n existe indice global (RN10)
    @Test
    void semOsHorariosNaoExisteIndice() {
        repositorioDevolveOQueRecebe();

        PittsburghScale scale = bomDormidor();
        scale.setUsualBedTime(null);

        PittsburghScale salva = pittsburghScaleService.create(scale);

        assertThat(salva.getPsqiScore()).isNull();
    }

    // o item 10, sobre parceiro de quarto, eh informacao de contexto e
    // n entra no indice
    @Test
    void oParceiroDeQuartoNaoMudaOIndice() {
        repositorioDevolveOQueRecebe();

        PittsburghScale semParceiro = bomDormidor();
        PittsburghScale comParceiro = bomDormidor();
        comParceiro.setRoomPartner(3);

        Integer indiceSemParceiro = pittsburghScaleService.create(semParceiro).getPsqiScore();
        Integer indiceComParceiro = pittsburghScaleService.create(comParceiro).getPsqiScore();

        assertThat(indiceComParceiro).isEqualTo(indiceSemParceiro);
    }
}
