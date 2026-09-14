package dev.uffs.doisag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

// o relogio do acompanhamento. roda uma vez por dia, fecha as tarefas
// q venceram e pede pro servico enviar o q chegou a hora
//
// a logica toda fica nos servicos, q recebem a data por parametro.
// aqui so tem o gatilho, pq cron n se testa
@Component
public class ProtocolScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProtocolScheduler.class);

    private final TreatmentProtocolService treatmentProtocolService;
    private final ScaleTaskService scaleTaskService;

    public ProtocolScheduler(TreatmentProtocolService treatmentProtocolService, ScaleTaskService scaleTaskService) {
        this.treatmentProtocolService = treatmentProtocolService;
        this.scaleTaskService = scaleTaskService;
    }

    // todo dia as 8 da manha, pra pessoa receber a notificacao num
    // horario que faz sentido e n de madrugada
    @Scheduled(cron = "${api.acompanhamento.cron:0 0 8 * * *}")
    public void designarEscalasDoDia() {
        LocalDate hoje = LocalDate.now();
        int fechadas = scaleTaskService.closeOverdue(hoje);
        int designadas = treatmentProtocolService.designarEscalasVencidas(hoje);
        if (fechadas > 0 || designadas > 0) {
            log.info("Acompanhamento automático: {} tarefas fechadas e {} escalas enviadas hoje",
                    fechadas, designadas);
        }
    }
}
