package org.furb.bolsavalores.service;

import com.rabbitmq.client.Channel;
import org.furb.bolsavalores.config.RabbitMQConfig;
import org.furb.bolsavalores.model.Acao;
import org.furb.bolsavalores.repository.AcoesRepository;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/**
 * Consumidor responsável por receber mensagens de atualização de ações
 * enviadas para a fila {@link RabbitMQConfig#QUEUE_ACOES}.
 *
 * Este consumidor utiliza **ACK manual**, garantindo que somente o líder
 * processe e salve as ações. A responsabilidade é:
 *
 *   • Followers: rejeitam a mensagem com requeue (basicNack)
 *   • Leader: valida duplicação, salva no banco e envia ACK
 *
 * O uso de ACK manual evita perda de mensagens em caso de falha e garante
 * consistência no cluster, já que apenas o líder faz persistência.
 */
@Service
public class AcoesConsumer {
    private final AcoesRepository acoesRepository;
    private final ElectionService electionService;

    /**
     * @param acoesRepository Repositório MongoDB para persistência de ações.
     * @param electionService Serviço que define se esta instância é o líder.
     */
    public AcoesConsumer(AcoesRepository acoesRepository, ElectionService electionService) {
        this.acoesRepository = acoesRepository;
        this.electionService = electionService;
    }

    /**
     * Método que consome mensagens da fila de ações.
     *
     * Configurações:
     *   • Fila: QUEUE_ACOES
     *   • ACK manual (via manualAckContainerFactory)
     *
     * Fluxo de processamento:
     *
     *   1) Se NÃO for líder → envia basicNack(requeue = true) e devolve a mensagem.
     *
     *   2) Verifica se o registro já existe (idempotência):
     *        - Busca por symbol + regularMarketTime.
     *        - Se existir → ACK e ignora.
     *
     *   3) Salva no banco e envia basicAck() manualmente.
     *
     *   4) Em caso de erro → NACK com requeue (para retry futuro).
     *
     * @param acao    Conteúdo da mensagem convertido automaticamente pelo Jackson.
     * @param message Metadados da mensagem AMQP.
     * @param channel Canal RabbitMQ usado para enviar ACK/NACK.
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_ACOES, containerFactory = "manualAckContainerFactory")
    public void receive(Acao acao, Message message, Channel channel) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            // ============================
            // 1 — Somente o líder processa
            // ============================
            if (!electionService.isLeader() || electionService.currentLeaderPort.trim().isEmpty()) {
                // Rejeita a mensagem e devolve para outro nó
                channel.basicNack(deliveryTag, false, true);
                return;
            }

            // ============================================================
            // 2 — Verifica duplicação usando symbol + regularMarketTime
            // ============================================================
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

            // ======================
            // 3 — Salva e confirma
            // ======================
            acoesRepository.save(acao);
            channel.basicAck(deliveryTag, false);
            System.out.println("Ação salva e ACK enviado pelo líder: " + acao.getSymbol());

        } catch (Exception e) {
            // =============================
            // 4 — Falha → NACK com requeue
            // =============================
            System.err.println("Erro ao processar ação: " + e.getMessage());
            try {
                channel.basicNack(deliveryTag, false, true);
            } catch (Exception ex) {
                System.err.println("Falha ao enviar NACK: " + ex.getMessage());
            }
        }
    }
}
