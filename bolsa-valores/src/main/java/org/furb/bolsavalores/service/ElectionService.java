package org.furb.bolsavalores.service;

import jakarta.annotation.PostConstruct;
import org.furb.bolsavalores.config.RabbitMQConfig;
import org.furb.bolsavalores.model.ElectionMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
public class ElectionService {
    private final RabbitTemplate rabbitTemplate;
    private final List<String> knownNodes;
    private final String myPort;
    private final long myStartTime;
    private Map<String, CompletableFuture<Boolean>> electionFutures = new ConcurrentHashMap<>();

    private volatile boolean isLeader = false;
    private volatile String currentLeaderPort = null;

    // configurable
    private final long OK_WAIT_MS = 3000;

    public ElectionService(RabbitTemplate rabbitTemplate,
                           @Value("${cluster.known-ports}") String knownPortsCsv,
                           @Value("${server.port}") String myPort) {
        this.rabbitTemplate = rabbitTemplate;
        this.knownNodes = parsePorts(knownPortsCsv);
        this.myPort = myPort;
        this.myStartTime = Instant.now().toEpochMilli();
    }

    public void sendToProcess(String targetPort, String message) {
        rabbitTemplate.convertAndSend(
                "election.exchange",
                "process." + targetPort,
                message
        );
        System.out.println("Enviado para process." + targetPort + ": " + message);
    }

    private List<String> parsePorts(String csv) {
        if (csv == null || csv.isBlank()) return Collections.emptyList();
        String[] parts = csv.split(",");
        List<String> ports = new ArrayList<>();
        for (String p : parts) ports.add(p.trim());
        return ports;
    }

    @PostConstruct
    public void init() {
        Executors.newSingleThreadScheduledExecutor()
                .schedule(this::startElection, 1, TimeUnit.SECONDS);
    }

    public synchronized void startElection() {
        System.out.println("🔍 [" + myPort + "] Iniciando eleição (startTime=" + myStartTime + ")");
        isLeader = false;
        currentLeaderPort = null;

        String electionId = UUID.randomUUID().toString();
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        electionFutures.put(electionId, future);

        // envia ELECTION para todos os outros nós
        for (String targetPort : knownNodes) {
            if (targetPort.equals(myPort)) continue;
            ElectionMessage msg = new ElectionMessage();
            msg.setElectionId(electionId);
            msg.setType(ElectionMessage.Type.ELECTION);
            msg.setSenderPort(myPort);
            msg.setSenderStartTime(myStartTime);

            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_ELECTION,
                    "process." + targetPort, msg);
            System.out.println("[" + myPort + "] ELECTION -> process." + targetPort);
        }

        // aguarda se algum OK chegar
        try {
            boolean gotOk = future.get(OK_WAIT_MS, TimeUnit.MILLISECONDS);
            if (gotOk) {
                System.out.println("[" + myPort + "] Recebeu OK; aguardando COORDINATOR...");
                // aguarda que outro processo anuncie COORDINATOR
                // se nenhum COORDINATOR chegar em intervalo maior, podemos reiniciar eleição (não implementado aqui)
            } else {
                // não deveria chegar false, mas tratamos abaixo
                becomeLeader();
            }
        } catch (TimeoutException te) {
            // nenhum OK - posso me tornar líder
            becomeLeader();
        } catch (Exception ex) {
            ex.printStackTrace();
            // falha: torna-se leader para evitar indecisão (ou poderia reiniciar)
            becomeLeader();
        } finally {
            electionFutures.remove(electionId);
        }
    }

    // chamado quando receber OK
    public void onOk(String electionId) {
        CompletableFuture<Boolean> f = electionFutures.get(electionId);
        if (f != null && !f.isDone()) {
            f.complete(true);
        }
    }

    // quando receber COORDINATOR via fanout
    public void onCoordinator(String leaderPort) {
        this.currentLeaderPort = leaderPort;
        boolean leaderIsMe = myPort.equals(leaderPort);
        this.isLeader = leaderIsMe;
        System.out.println("[" + myPort + "] COORDINATOR recebido -> " + leaderPort + " (isLeader=" + isLeader + ")");
    }

    private void becomeLeader() {
        this.isLeader = true;
        this.currentLeaderPort = myPort;
        System.out.println("[" + myPort + "] Tornou-se líder!");
        // broadcast COORDINATOR
        ElectionMessage coord = new ElectionMessage();
        coord.setType(ElectionMessage.Type.COORDINATOR);
        coord.setSenderPort(myPort);
        coord.setSenderStartTime(myStartTime);
        coord.setElectionId(UUID.randomUUID().toString());

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_LEADER, "", coord);
        System.out.println("[" + myPort + "] Broadcast COORDINATOR");
    }

    // quando receber ELECTION - chamamos isso a partir do listener
    public void onElectionReceived(ElectionMessage msg) {
        // compare startTimes: menor startTime => mais antigo => maior prioridade
        if (this.myStartTime < msg.getSenderStartTime()) {
            // sou mais velho -> respondo OK e inicio minha própria eleição
            System.out.println("[" + myPort + "] Sou mais velho que " + msg.getSenderPort() + " -> respondo OK");
            ElectionMessage ok = new ElectionMessage();
            ok.setElectionId(msg.getElectionId());
            ok.setType(ElectionMessage.Type.OK);
            ok.setSenderPort(myPort);
            ok.setSenderStartTime(myStartTime);

            // send OK directly to the initiator
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_ELECTION,
                    "process." + msg.getSenderPort(), ok);

            // iniciar nova eleição para declarar se for o mais velho absoluto
            Executors.newSingleThreadScheduledExecutor().schedule(this::startElection, 200, TimeUnit.MILLISECONDS);
        } else {
            // sou mais novo -> ignoro
            System.out.println("[" + myPort + "] Sou mais novo que " + msg.getSenderPort() + " -> ignoro");
        }
    }

    public boolean isLeader() {
        return isLeader;
    }

    public String getCurrentLeaderPort() {
        return currentLeaderPort;
    }

    public String getMyPort() {
        return myPort;
    }

    public long getMyStartTime() {
        return myStartTime;
    }
}
