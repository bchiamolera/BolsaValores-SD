package org.furb.bolsavalores.controller;

import org.furb.bolsavalores.service.ElectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {
    private final ElectionService electionService;

    public PingController(ElectionService electionService) {
        this.electionService = electionService;
    }

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("alive");
    }

    @GetMapping("/uptime")
    public ResponseEntity<Long> uptime() {
        return ResponseEntity.ok(electionService.getMyStartTime());
    }

    @GetMapping("/leader")
    public ResponseEntity<String> leader() {
        String leader = electionService.getCurrentLeaderPort();
        return ResponseEntity.ok(leader == null ? "none" : leader);
    }
}
