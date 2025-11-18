package org.furb.bolsavalores.service;

import org.furb.bolsavalores.model.ElectionMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumer responsável por receber e processar mensagens relacionadas
 * ao algoritmo de eleição distribuída (variação do Bully Algorithm).
 *
 * <p>Este componente escuta a fila dinâmica criada para cada instância
 * da aplicação e responde a três tipos de mensagens:</p>
 *
 * <ul>
 *   <li><strong>ELECTION</strong> – indica que outro nó iniciou uma eleição</li>
 *   <li><strong>OK</strong> – confirma que um nó com maior prioridade está ativo</li>
 *   <li><strong>COORDINATOR</strong> – informa qual nó é o novo coordenador/líder</li>
 * </ul>
 *
 * <p>A lógica específica de cada etapa é delegada para o
 * {@link ElectionService}, permitindo manter as regras de eleição
 * centralizadas e testáveis.</p>
 */
@Component
public class ElectionConsumer {

    /** Serviço responsável por coordenar toda a lógica da eleição. */
    private final ElectionService electionService;

    /**
     * Construtor com injeção do serviço de eleição.
     *
     * @param electionService serviço contendo as regras do algoritmo de eleição
     */
    public ElectionConsumer(ElectionService electionService) {
        this.electionService = electionService;
    }

    /**
     * Listener responsável por consumir mensagens da fila de eleição
     * associada dinamicamente à porta da instância atual da aplicação.
     *
     * <p>A fila é referenciada via SpEL:
     * <code>#{electionQueue.name}</code>, permitindo que cada instância
     * possua sua própria fila exclusiva sem precisar fixar o nome.</p>
     *
     * <p>O comportamento varia conforme o tipo da mensagem recebida:</p>
     * <ul>
     *   <li><strong>ELECTION</strong> – aciona {@code onElectionReceived()}</li>
     *   <li><strong>OK</strong> – aciona {@code onOk()}</li>
     *   <li><strong>COORDINATOR</strong> – aciona {@code onCoordinator()}</li>
     * </ul>
     *
     * @param msg mensagem recebida contendo o tipo e dados da eleição
     */
    @RabbitListener(queues = "#{electionQueue.name}")
    public void onMessage(ElectionMessage msg) {
        if (msg == null) return;

        switch (msg.getType()) {
            case ELECTION:
                System.out.println("[" + electionService.getMyPort() + "] RECEBIDO ELECTION de " + msg.getSenderPort());
                electionService.onElectionReceived(msg);
                break;
            case OK:
                System.out.println("[" + electionService.getMyPort() + "] RECEBIDO OK de " + msg.getSenderPort());
                electionService.onOk(msg.getElectionId());
                break;
            case COORDINATOR:
                System.out.println("[" + electionService.getMyPort() + "] RECEBIDO COORDINATOR de " + msg.getSenderPort());
                electionService.onCoordinator(msg.getSenderPort());
                break;
        }
    }
}
