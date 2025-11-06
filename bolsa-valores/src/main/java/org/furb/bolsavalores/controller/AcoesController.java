package org.furb.bolsavalores.controller;

import org.furb.bolsavalores.model.Acao;
import org.furb.bolsavalores.repository.AcoesRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/acoes")
public class AcoesController {
    private final AcoesRepository acoesRepository;

    public AcoesController(AcoesRepository acoesRepository) {
        this.acoesRepository = acoesRepository;
    }

    @GetMapping()
    public List<Acao> listarAcoes() {
        return acoesRepository.findAll();
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<List<Acao>> getBySymbol(@PathVariable String symbol) {
        var acoes = acoesRepository.findBySymbol(symbol.toUpperCase());
        if (acoes.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(acoes);
    }
}
