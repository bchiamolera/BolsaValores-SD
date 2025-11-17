package org.furb.bolsavalores.scheduler;

import org.furb.bolsavalores.config.RabbitMQConfig;
import org.furb.bolsavalores.model.Acao;
import org.furb.bolsavalores.service.ConsultaBolsaService;
import org.furb.bolsavalores.service.ElectionService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BolsaScheduler {
    private final ConsultaBolsaService consultaBolsaService;
    private final RabbitTemplate rabbitTemplate;
    private final ElectionService electionService;

    public BolsaScheduler(ConsultaBolsaService consultaBolsaService, RabbitTemplate rabbitTemplate, ElectionService electionService) {
        this.consultaBolsaService = consultaBolsaService;
        this.rabbitTemplate = rabbitTemplate;
        this.electionService = electionService;
    }

    @Scheduled(fixedRate = 30 * 1000, initialDelay = 5 * 1000)
    public void atualizarCotacaoAutomatica() {
        if (electionService.isLeader()) return;

        String ticker = "PETR4";

        consultaBolsaService.getMessage(ticker)
                .subscribe(response -> {
                    Acao acao = new Acao();
                    acao.setSymbol(response.getSymbol());
                    acao.setShortName(response.getShortName());
                    acao.setLongName(response.getLongName());
                    acao.setRegularMarketPrice(response.getRegularMarketPrice());
                    acao.setRegularMarketTime(response.getRegularMarketTime());

                    rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_ACOES, "bolsa.acoes." + ticker.toLowerCase(), acao);
                });
        System.out.println("Ações de " + ticker + " atualizadas com sucesso!");
        electionService.pingLeader();
    }
}
