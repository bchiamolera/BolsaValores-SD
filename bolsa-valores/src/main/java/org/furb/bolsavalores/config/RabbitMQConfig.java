package org.furb.bolsavalores.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração central do RabbitMQ para o sistema Bolsa de Valores.
 *
 * Esta classe define:
 * - Exchanges (Direct, Fanout e Topic)
 * - Filas dinâmicas e estáticas
 * - Bindings
 * - Conversor de mensagens JSON
 * - Container Factory com ACK manual para consumidores que exigem controle explícito
 *
 * Filas usadas:
 *  • acoes.queue — recebe atualizações de ações via Topic Exchange
 *  • eleicao.queue.{port} — fila efêmera por instância para eleição de coordenador
 *
 * Exchanges:
 *  • bolsa.acoes.exchange (Topic)
 *  • eleicao.exchange (Direct)
 *  • coordenador.exchange (Fanout)
 */
@Configuration
public class RabbitMQConfig {
    // ================== CONSTS ==================

    public static final String EXCHANGE_ACOES = "bolsa.acoes.exchange";
    public static final String QUEUE_ACOES = "acoes.queue";
    public static final String ROUTING_KEY_ACOES = "bolsa.acoes.#";

    public static final String EXCHANGE_ELECTION = "eleicao.exchange";
    public static final String QUEUE_ELECTION = "eleicao.queue";

    public static final String EXCHANGE_LEADER = "coordenador.exchange";

    // ================== ELEIÇÃO (BULLY) ==================

    /**
     * Exchange usada no algoritmo de eleição (Bully).
     * Cada instância envia mensagens direcionadas para um processo específico.
     * DirectExchange permite roteamento para filas exclusivas por porta.
     */
    @Bean
    public DirectExchange electionExchange() {
        return new DirectExchange(EXCHANGE_ELECTION);
    }

    /**
     * Fila efêmera (auto-delete) criada dinamicamente para cada nó do cluster.
     *
     * @param port Porta da instância atual (processo participante).
     * @return Queue exclusiva, não durável, auto-delete e com nome único.
     */
    @Bean
    public Queue electionQueue(@Value("${server.port}") String port) {
        return new Queue(QUEUE_ELECTION + "." + port, false, true, true);
    }

    /**
     * Bind entre a fila dinâmica e a exchange de eleição.
     * Cada processo escuta somente mensagens enviadas para "process.{port}".
     */
    @Bean
    public Binding electionBinding(Queue electionQueue, DirectExchange electionExchange, @Value("${server.port}") String port) {
        return BindingBuilder.bind(electionQueue).to(electionExchange).with("process." + port);
    }

    // ================== COORDENADOR (FANOUT) ==================

    /**
     * Exchange usada para anunciar quem é o novo coordenador.
     * Fanout envia broadcast para todas as instâncias — ideal para notificação geral.
     */
    @Bean
    public FanoutExchange leaderExchange() {
        return new FanoutExchange(EXCHANGE_LEADER);
    }

    /**
     * Mesmo a fila de eleição recebe mensagens de anúncio do líder.
     * Isso evita criar uma segunda fila efêmera por processo.
     */
    @Bean
    public Binding leaderBinding(Queue electionQueue, @Value("${server.port}") String port) {
        return BindingBuilder.bind(electionQueue).to(leaderExchange());
    }

    // ================== AÇÕES (BOLSA) ==================

    /**
     * Exchange principal dos dados da bolsa de valores.
     * TopicExchange permite encaminhar múltiplas rotas, incluindo padrões.
     */
    @Bean
    public TopicExchange acoesExchange() {
        return new TopicExchange(EXCHANGE_ACOES);
    }

    /**
     * Fila durável onde todas as atualizações de ações serão consumidas.
     */
    @Bean
    public Queue acoesQueue() {
        return new Queue(QUEUE_ACOES, true);
    }

    /**
     * Bind para receber todas as atualizações de ações,
     * usando wildcard "bolsa.acoes.#".
     */
    @Bean
    public Binding acoesBinding(Queue acoesQueue, TopicExchange bolsaExchange) {
        return BindingBuilder.bind(acoesQueue).to(bolsaExchange).with(ROUTING_KEY_ACOES);
    }

    // ================== CONVERSOR DE MENSAGENS ==================

    /**
     * Conversor que transforma mensagens JSON em objetos Java (e vice-versa).
     * Essencial para comunicação entre serviços.
     */
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Container Factory para consumidores que exigem ACK manual.
     * Uso:
     *  @RabbitListener(queues = "...", containerFactory = "manualAckContainerFactory")
     * O consumidor deve chamar:
     *  channel.basicAck(deliveryTag, false)
     *
     * @param connectionFactory Conexão já configurada pelo Spring AMQP.
     * @return Factory com modo de ACK manual e prefetch 1.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory manualAckContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jackson2JsonMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(1);
        return factory;
    }
}
