package org.furb.bolsavalores.controller;

import org.furb.bolsavalores.model.Acao;
import org.furb.bolsavalores.repository.AcoesRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador REST responsável pela consulta de ações registradas no sistema.
 *
 * Endpoints:
 *  GET /acoes
 *      → Retorna todas as ações salvas no banco.
 *
 *  GET /acoes/{symbol}
 *      → Retorna todas as entradas referentes ao símbolo informado (ex: PETR4).
 *
 * Este controlador acessa o {@link AcoesRepository} para realizar operações
 * de leitura no banco de dados (MongoDB).
 */
@RestController
@RequestMapping("/acoes")
public class AcoesController {
    private final AcoesRepository acoesRepository;

    /**
     * Injeta o repositório de ações.
     *
     * @param acoesRepository Repositório responsável pela persistência de {@link Acao}.
     */
    public AcoesController(AcoesRepository acoesRepository) {
        this.acoesRepository = acoesRepository;
    }

    /**
     * Lista todas as ações cadastradas no banco.
     *
     * @return Lista completa contendo todos os documentos da coleção "acoes".
     */
    @GetMapping()
    public List<Acao> listarAcoes() {
        return acoesRepository.findAll();
    }

    /**
     * Busca ações pelo símbolo informado (ex: "PETR4").
     *
     * O símbolo sempre é convertido para uppercase para padronizar as consultas.
     *
     * @param symbol Símbolo da ação a ser buscada.
     * @return 200 OK com a lista de ações, caso existam.
     *         404 NOT FOUND caso nenhuma ação seja encontrada.
     */
    @GetMapping("/{symbol}")
    public ResponseEntity<List<Acao>> getBySymbol(@PathVariable String symbol) {
        var acoes = acoesRepository.findBySymbol(symbol.toUpperCase());
        if (acoes.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(acoes);
    }
}
