package org.furb.bolsavalores.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String EXCHANGE_NAME = "bolsa.exchange";
    public static final String QUEUE_NAME = "cotacoes_bolsa";
    public static final String ROUTING_KEY = "bolsa.cotacao.#";

    @Bean
    public TopicExchange bolsaExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public Queue cotacaoQueue() {
        return new Queue(QUEUE_NAME, true);
    }

    @Bean
    public Binding cotacaoBinding(Queue cotacaoQueue, TopicExchange bolsaExchange) {
        return BindingBuilder.bind(cotacaoQueue).to(bolsaExchange).with(ROUTING_KEY);
    }
}
