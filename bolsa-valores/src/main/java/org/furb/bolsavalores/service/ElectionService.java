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

/**
 * Serviço responsável por coordenar o algoritmo de eleição distribuída
 * utilizado pelo cluster. Cada instância do sistema roda esse serviço
 * para determinar qual nó deve atuar como líder.
 *
 * <p>
 * O algoritmo implementado é uma variação do
 * <strong>Bully Algorithm</strong>, porém utilizando o
 * <strong>tempo de início (startTime)</strong> como critério de prioridade:
 * quanto mais antigo o nó (menor startTime), maior sua prioridade.
 * </p>
 *
 * <p>
 * A comunicação entre nós ocorre através de:
 * <ul>
 *     <li>Mensagens RabbitMQ (ELECTION, OK, COORDINATOR)</li>
 *     <li>Requisições HTTP (ping do líder em /status/ping)</li>
 * </ul>
 * </p>
 *
 * <p>
 * O ciclo geral funciona assim:
 * <ol>
 *     <li>Ao iniciar, o nó tenta encontrar o líder atual na rede.</li>
 *     <li>Se não encontrar, inicia uma eleição.</li>
 *     <li>Durante uma eleição:
 *         <ul>
 *             <li>Nós mais antigos respondem com OK.</li>
 *             <li>Se o nó não receber OK dentro do timeout, ele se elege líder.</li>
 *         </ul>
 *     </li>
 *     <li>O líder transmite uma mensagem COORDINATOR via fanout.</li>
 * </ol>
 * </p>
 */
@Service
public class ElectionService {

    /** Template usado para envio de mensagens RabbitMQ. */
    private final RabbitTemplate rabbitTemplate;

    /** Lista de portas conhecidas do cluster, usada para enviar mensagens diretas ELECTION. */
    private final List<String> knownNodes;

    /** Porta da instância atual. */
    @Getter private final String myPort;

    /** Timestamp que representa quando a instância foi iniciada. */
    @Getter private final long myStartTime;

    /** Indica se esse nó é o líder atual. */
    @Getter volatile boolean isLeader = false;

    /** Porta do líder atualmente reconhecido. */
    @Getter volatile String currentLeaderPort = null;

    /** Mapa de Futures para controlar respostas OK durante eleições. */
    private final Map<String, CompletableFuture<Boolean>> electionFutures = new ConcurrentHashMap<>();

    /** Timeout de espera por um OK antes de assumir liderança. */
    private final long OK_WAIT_MS = 3000;

    /** Cliente HTTP usado para verificar se o líder está vivo. */
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Construtor do serviço de eleição.
     *
     * @param rabbitTemplate          template para envio de mensagens RabbitMQ
     * @param knownPortsCsv           lista CSV das portas do cluster
     * @param myPort                  porta local desta instância
     */
    public ElectionService(RabbitTemplate rabbitTemplate,
                           @Value("${cluster.known-ports}") String knownPortsCsv,
                           @Value("${server.port}") String myPort) {
        this.rabbitTemplate = rabbitTemplate;
        this.knownNodes = parsePorts(knownPortsCsv);
        this.myPort = myPort;
        this.myStartTime = Instant.now().toEpochMilli();
    }

    /**
     * Converte a lista de portas do CSV para uma lista.
     *
     * @param csv lista no formato "8081,8082,8083"
     * @return lista de portas normalizada
     */
    private List<String> parsePorts(String csv) {
        if (csv == null || csv.isBlank()) return Collections.emptyList();
        String[] parts = csv.split(",");
        List<String> ports = new ArrayList<>();
        for (String p : parts) ports.add(p.trim());
        return ports;
    }

    /**
     * Executado automaticamente após a inicialização do bean.
     * Inicia a busca por um líder após 1 segundo.
     */
    @PostConstruct
    public void init() {
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
                if (!tryFindLeader()) startElection();
            }, 1, TimeUnit.SECONDS);
    }

    /**
     * Tenta descobrir o líder consultando via HTTP cada nó conhecido.
     *
     * @return true se encontrou um líder, false caso contrário
     */
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

    /**
     * Inicia o processo de eleição enviando mensagens ELECTION
     * para todos os nós conhecidos.
     *
     * Caso nenhum nó mais velho responda com OK dentro do timeout,
     * esta instância se torna o líder.
     */
    public synchronized void startElection() {
        System.out.println("[" + myPort + "] Iniciando eleição (startTime=" + myStartTime + ")");
        isLeader = false;
        currentLeaderPort = null;

        String electionId = UUID.randomUUID().toString();
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        electionFutures.put(electionId, future);

        // Broadcast ELECTION para todos os nós
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

    /**
     * Tratamento de resposta OK durante a eleição.
     *
     * @param electionId id da eleição em curso
     */
    public void onOk(String electionId) {
        CompletableFuture<Boolean> f = electionFutures.get(electionId);
        if (f != null && !f.isDone()) {
            f.complete(true);
        }
    }

    /**
     * Recebe a mensagem COORDINATOR indicando o novo líder.
     *
     * @param leaderPort porta da instância líder
     */
    public void onCoordinator(String leaderPort) {
        this.currentLeaderPort = leaderPort;
        boolean leaderIsMe = myPort.equals(leaderPort);
        this.isLeader = leaderIsMe;
        System.out.println("[" + myPort + "] COORDINATOR recebido -> " + leaderPort + " (isLeader=" + isLeader + ")");
    }

    /**
     * Torna esta instância o líder e envia uma mensagem COORDINATOR
     * via exchange fanout.
     */
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

    /**
     * Lógica executada quando um nó recebe uma mensagem ELECTION.
     * <p>
     * Se esta instância for mais velha que o emissor, ela responde OK
     * e inicia sua própria eleição.
     * </p>
     *
     * @param msg mensagem de eleição recebida
     */
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

    /**
     * Verifica periodicamente se o líder está ativo.
     * <p>
     * Caso o líder não responda ao ping HTTP, uma nova eleição é iniciada.
     * </p>
     */
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
