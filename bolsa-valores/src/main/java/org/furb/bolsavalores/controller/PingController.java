package org.furb.bolsavalores.controller;

import org.furb.bolsavalores.service.ElectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador responsável por expor endpoints de monitoramento e status
 * relacionados ao algoritmo de eleição (Bully) e ao estado da instância atual.
 *
 * Endpoints:
 *
 *  GET /status/ping
 *      → Verifica se o serviço está respondendo.
 *
 *  GET /status/uptime
 *      → Retorna o timestamp de inicialização da instância.
 *
 *  GET /status/leader
 *      → Retorna a porta do processo coordenador atual.
 *
 *  GET /status
 *      → Retorna se esta instância é "leader" ou "follower".
 */
@RestController
@RequestMapping("/status")
public class PingController {
    private final ElectionService electionService;

    /**
     * Injeta o serviço responsável pelo algoritmo de eleição.
     *
     * @param electionService Serviço que controla o estado de líder e início do processo.
     */
    public PingController(ElectionService electionService) {
        this.electionService = electionService;
    }

    /**
     * Endpoint simples de verificação de vida ("health check").
     *
     * @return 200 OK com o texto "alive".
     */
    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("alive");
    }

    /**
     * Retorna o tempo de início da instância atual.
     * Usado para determinar idade do processo (critério do Bully).
     *
     * @return 200 OK contendo o timestamp de inicialização.
     */
    @GetMapping("/uptime")
    public ResponseEntity<Long> uptime() {
        return ResponseEntity.ok(electionService.getMyStartTime());
    }

    /**
     * Obtém o líder atual do cluster.
     *
     * @return 200 OK com:
     *         - número da porta do líder (ex: "8082"), ou
     *         - "none" caso nenhum líder esteja definido.
     */
    @GetMapping("/leader")
    public ResponseEntity<String> leader() {
        String leader = electionService.getCurrentLeaderPort();
        return ResponseEntity.ok(leader == null ? "none" : leader);
    }

    /**
     * Retorna o estado de liderança da instância atual.
     *
     * @return 200 OK contendo:
     *         - "leader" se esta instância for o coordenador.
     *         - "follower" caso contrário.
     */
    @GetMapping()
    public ResponseEntity<String> status() {
        return ResponseEntity.ok(electionService.isLeader() ? "leader" : "follower");
    }
}
