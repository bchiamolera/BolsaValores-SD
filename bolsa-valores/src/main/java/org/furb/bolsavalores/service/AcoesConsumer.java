package org.furb.bolsavalores.service;

import org.furb.bolsavalores.config.RabbitMQConfig;
import org.furb.bolsavalores.model.Acao;
import org.furb.bolsavalores.repository.AcoesRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class AcoesConsumer {
    private final AcoesRepository acoesRepository;
    private final ElectionService electionService;

    public AcoesConsumer(AcoesRepository acoesRepository, ElectionService electionService) {
        this.acoesRepository = acoesRepository;
        this.electionService = electionService;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_ACOES)
    public void receive(Acao acao) {
        if (!electionService.isLeader()) return;

        boolean existe = acoesRepository
                .findBySymbolAndRegularMarketTime(
                        acao.getSymbol(),
                        acao.getRegularMarketTime())
                .isPresent();
        if (existe) return;

        acoesRepository.save(acao);
    }
}
