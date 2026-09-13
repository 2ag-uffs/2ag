package dev.uffs.doisag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

// o relogio do acompanhamento. roda uma vez por dia e pede pro servico
// designar o que venceu.
//
// a logica toda fica no TreatmentProtocolService, que recebe a data por
// parametro. aqui so tem o gatilho, pq cron n se testa
@Component
public class ProtocolScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProtocolScheduler.class);

    private final TreatmentProtocolService treatmentProtocolService;

    public ProtocolScheduler(TreatmentProtocolService treatmentProtocolService) {
        this.treatmentProtocolService = treatmentProtocolService;
    }

    // todo dia as 8 da manha, pra pessoa receber a notificacao num
    // horario que faz sentido e n de madrugada
    @Scheduled(cron = "${api.acompanhamento.cron:0 0 8 * * *}")
    public void designarEscalasDoDia() {
        int designadas = treatmentProtocolService.designarEscalasVencidas(LocalDate.now());
        if (designadas > 0) {
            log.info("Acompanhamento automático: {} escalas designadas hoje", designadas);
        }
    }
}
