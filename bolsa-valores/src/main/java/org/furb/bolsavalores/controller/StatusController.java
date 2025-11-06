package org.furb.bolsavalores.controller;

import org.furb.bolsavalores.service.ElectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {
    private final ElectionService electionService;
    private final long startTime = System.currentTimeMillis();

    public StatusController(ElectionService electionService) {
        this.electionService = electionService;
    }

    @GetMapping("/status")
    public ResponseEntity<String> status() {
        return ResponseEntity.ok(electionService.isLeader() ? "leader" : "follower");
    }

    @GetMapping("/uptime")
    public ResponseEntity<Long> uptime() {
        return ResponseEntity.ok(startTime);
    }
}
