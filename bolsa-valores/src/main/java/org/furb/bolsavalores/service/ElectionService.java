package org.furb.bolsavalores.service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.furb.bolsavalores.config.RabbitMQConfig;
import org.furb.bolsavalores.model.ElectionMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
public class ElectionService {
    private final RabbitTemplate rabbitTemplate;
    private final List<String> knownNodes;

    @Getter private final String myPort;
    @Getter private final long myStartTime;
    @Getter volatile boolean isLeader = false;
    @Getter volatile String currentLeaderPort = null;

    private final Map<String, CompletableFuture<Boolean>> electionFutures = new ConcurrentHashMap<>();
    private final long OK_WAIT_MS = 3000;
    private final RestTemplate restTemplate = new RestTemplate();

    public ElectionService(RabbitTemplate rabbitTemplate,
                           @Value("${cluster.known-ports}") String knownPortsCsv,
                           @Value("${server.port}") String myPort) {
        this.rabbitTemplate = rabbitTemplate;
        this.knownNodes = parsePorts(knownPortsCsv);
        this.myPort = myPort;
        this.myStartTime = Instant.now().toEpochMilli();
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
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
                if (!tryFindLeader()) startElection();
            }, 1, TimeUnit.SECONDS);
    }

    private boolean tryFindLeader() {
        System.out.println("[" + myPort + "] Procurando o líder atual...");
        for (String node : knownNodes) {
            if (node.equals(myPort)) continue;
            try {
                String url = "http://localhost:" + node + "/api/status";
                String status = restTemplate.getForObject(url, String.class);

                if ("leader".equalsIgnoreCase(status)) {
                    this.currentLeaderPort = node;
                    this.isLeader = false;
                    System.out.println("[" + myPort + "] líder encontrado! Porta " + node);
                    return true;
                }
            }
            catch (Exception e) {
                System.err.println("[" + myPort + "] erro ao dar ping na porta " + node + ": " + e.getMessage());
            }
        }
        System.out.println("[" + myPort + "] Líder não encontrado");
        return false;
    }

    public synchronized void startElection() {
        System.out.println("[" + myPort + "] Iniciando eleição (startTime=" + myStartTime + ")");
        isLeader = false;
        currentLeaderPort = null;

        String electionId = UUID.randomUUID().toString();
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        electionFutures.put(electionId, future);

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

        try {
            boolean gotOk = future.get(OK_WAIT_MS, TimeUnit.MILLISECONDS);
            if (gotOk) {
                System.out.println("[" + myPort + "] Recebeu OK; aguardando COORDINATOR...");
            } else {
                becomeLeader();
            }
        } catch (TimeoutException te) {
            becomeLeader();
        } catch (Exception ex) {
            ex.printStackTrace();
            becomeLeader();
        } finally {
            electionFutures.remove(electionId);
        }
    }

    public void onOk(String electionId) {
        CompletableFuture<Boolean> f = electionFutures.get(electionId);
        if (f != null && !f.isDone()) {
            f.complete(true);
        }
    }

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
        ElectionMessage coord = new ElectionMessage();
        coord.setType(ElectionMessage.Type.COORDINATOR);
        coord.setSenderPort(myPort);
        coord.setSenderStartTime(myStartTime);
        coord.setElectionId(UUID.randomUUID().toString());

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_LEADER, "", coord);
        System.out.println("[" + myPort + "] Broadcast COORDINATOR");
    }

    public void onElectionReceived(ElectionMessage msg) {
        if (this.myStartTime < msg.getSenderStartTime()) {
            System.out.println("[" + myPort + "] Sou mais velho que " + msg.getSenderPort() + " -> respondo OK");
            ElectionMessage ok = new ElectionMessage();
            ok.setElectionId(msg.getElectionId());
            ok.setType(ElectionMessage.Type.OK);
            ok.setSenderPort(myPort);
            ok.setSenderStartTime(myStartTime);

            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_ELECTION,
                    "process." + msg.getSenderPort(), ok);

            Executors.newSingleThreadScheduledExecutor().schedule(this::startElection, 200, TimeUnit.MILLISECONDS);
        } else {
            System.out.println("[" + myPort + "] Sou mais novo que " + msg.getSenderPort() + " -> ignoro");
        }
    }

    public void pingLeader() {
        if (currentLeaderPort == null) {
            System.out.println("[" + myPort + "] Nenhum líder ativo, iniciando eleição...");
            startElection();
            return;
        }

        if (isLeader) return;

        try {
            String leaderUrl = "http://localhost:" + currentLeaderPort + "/api/status/ping";
            String response = restTemplate.getForObject(leaderUrl, String.class);
            if (!"alive".equalsIgnoreCase(response)) {
                throw new RuntimeException("Resposta inesperada do líder: " + response);
            }
            System.out.println("[" + myPort + "] Líder " + currentLeaderPort + " está ativo.");
        } catch (Exception e) {
            System.out.println("[" + myPort + "] Líder " + currentLeaderPort + " não responde — iniciando nova eleição.");
            startElection();
        }
    }
}
