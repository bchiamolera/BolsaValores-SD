package org.furb.bolsavalores.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String EXCHANGE_ACOES = "bolsa.acoes.exchange";
    public static final String QUEUE_ACOES = "acoes_bolsa";
    public static final String ROUTING_KEY_ACOES = "bolsa.acoes.#";

    public static final String EXCHANGE_ELECTION = "eleicao.exchange";
    public static final String QUEUE_ELECTION = "eleicao.queue";

    public static final String EXCHANGE_LEADER = "coordenador.exchange";

    @Bean
    public DirectExchange electionExchange() {
        return new DirectExchange(EXCHANGE_ELECTION);
    }

    @Bean
    public Queue electionQueue(@Value("${server.port}") String port) {
        return new Queue(QUEUE_ELECTION + "." + port, false);
    }

    @Bean
    public Binding electionBinding(Queue electionQueue, DirectExchange electionExchange, @Value("${server.port}") String port) {
        return BindingBuilder.bind(electionQueue).to(electionExchange).with("process." + port);
    }

    @Bean
    public FanoutExchange leaderExchange() {
        return new FanoutExchange(EXCHANGE_LEADER);
    }

    @Bean
    public Binding leaderBinding(Queue electionQueue, @Value("${server.port}") String port) {
        return BindingBuilder.bind(electionQueue).to(leaderExchange());
    }

    @Bean
    public TopicExchange acoesExchange() {
        return new TopicExchange(EXCHANGE_ACOES);
    }

    @Bean
    public Queue acoesQueue() {
        return new Queue(QUEUE_ACOES, true);
    }

    @Bean
    public Binding acoesBinding(Queue acoesQueue, TopicExchange bolsaExchange) {
        return BindingBuilder.bind(acoesQueue).to(bolsaExchange).with(ROUTING_KEY_ACOES);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
