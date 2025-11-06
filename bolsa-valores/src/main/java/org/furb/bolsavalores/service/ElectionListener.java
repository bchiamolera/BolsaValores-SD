package org.furb.bolsavalores.service;

import org.furb.bolsavalores.model.ElectionMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ElectionListener {
    private final ElectionService electionService;

    public ElectionListener(ElectionService electionService) {
        this.electionService = electionService;
    }

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
