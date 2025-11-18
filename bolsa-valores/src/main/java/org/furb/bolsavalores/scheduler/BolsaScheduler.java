package org.furb.bolsavalores.scheduler;

import org.furb.bolsavalores.config.AppConfig;
import org.furb.bolsavalores.config.RabbitMQConfig;
import org.furb.bolsavalores.model.Acao;
import org.furb.bolsavalores.service.ConsultaBolsaService;
import org.furb.bolsavalores.service.ElectionService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Scheduler responsável por buscar periodicamente a cotação de ações
 * e publicar atualizações no RabbitMQ. A execução automática acontece
 * somente quando o processo **não é o líder**, seguindo a lógica do
 * algoritmo Bully implementado no sistema.
 *
 * Este componente:
 *   • Consulta o serviço externo de bolsa (Yahoo/Brapi API)
 *   • Converte a resposta para o modelo interno {@link Acao}
 *   • Publica os dados no Exchange de ações via RabbitMQ
 *   • Envia ping periódico ao líder para verificar sua presença
 */
@Component
public class BolsaScheduler {
    private final ConsultaBolsaService consultaBolsaService;
    private final RabbitTemplate rabbitTemplate;
    private final ElectionService electionService;
    private final AppConfig appConfig;

    /**
     * Construtor com injeção de dependências.
     *
     * @param consultaBolsaService Serviço que consulta o preço da ação.
     * @param rabbitTemplate       Template para publicar mensagens no RabbitMQ.
     * @param electionService      Serviço responsável pela lógica de liderança.
     */
    public BolsaScheduler(ConsultaBolsaService consultaBolsaService, RabbitTemplate rabbitTemplate, ElectionService electionService, AppConfig appConfig) {
        this.consultaBolsaService = consultaBolsaService;
        this.rabbitTemplate = rabbitTemplate;
        this.electionService = electionService;
        this.appConfig = appConfig;
    }

    /**
     * Tarefa executada **a cada 1 hora**, com 10 segundos de atraso inicial.
     *
     * Agora:
     *   • Usa API e ticker definidos em AppConfig
     *   • Usa método síncrono consulta() do serviço
     *   • Publica a ação no Exchange usando routing key dinâmica
     */
    @Scheduled(fixedRate = 60 * 60 * 1000, initialDelay = 10 * 1000)
    public void atualizarCotacaoAutomatica() {
        // Followers atualizam preços; o líder apenas coordena o cluster.
        if (electionService.isLeader()) return;

        String ticker = appConfig.getTicker();

        try {
            // consulta de acordo com API escolhida (AppConfig.API)
            Acao acao = consultaBolsaService.consultar();

            // routing key dinâmica
            String routingKey = "bolsa.acoes." + ticker.toLowerCase();

            // publica no RabbitMQ
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_ACOES, routingKey, acao);

            System.out.println("[Scheduler] Atualização enviada: " + ticker
                    + " via API=" + appConfig.getApi());

        } catch (Exception ex) {
            System.err.println("[Scheduler] ERRO ao consultar ou enviar ação: " + ex.getMessage());
        }

        // Sinaliza ao líder que este processo está ativo
        electionService.pingLeader();
    }
}
