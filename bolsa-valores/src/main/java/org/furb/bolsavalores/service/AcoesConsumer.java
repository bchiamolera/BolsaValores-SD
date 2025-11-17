package org.furb.bolsavalores.service;

import com.rabbitmq.client.Channel;
import org.furb.bolsavalores.config.RabbitMQConfig;
import org.furb.bolsavalores.model.Acao;
import org.furb.bolsavalores.repository.AcoesRepository;
import org.springframework.amqp.core.Message;
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

    @RabbitListener(queues = RabbitMQConfig.QUEUE_ACOES, containerFactory = "manualAckContainerFactory")
    public void receive(Acao acao, Message message, Channel channel) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            if (!electionService.isLeader() || electionService.currentLeaderPort.trim().isEmpty()) {
                channel.basicNack(deliveryTag, false, true);
                return;
            }

            boolean existe = acoesRepository
                             .findBySymbolAndRegularMarketTime(
                                acao.getSymbol(),
                                acao.getRegularMarketTime())
                             .isPresent();
            if (existe) {
                System.out.println("Ação já existe");
                channel.basicAck(deliveryTag, false);
                return;
            }

            acoesRepository.save(acao);
            channel.basicAck(deliveryTag, false);
            System.out.println("Ação salva e ACK enviado pelo líder: " + acao.getSymbol());

        } catch (Exception e) {
            System.err.println("Erro ao processar ação: " + e.getMessage());
            try {
                channel.basicNack(deliveryTag, false, true);
            } catch (Exception ex) {
                System.err.println("Falha ao enviar NACK: " + ex.getMessage());
            }
        }
    }
}
