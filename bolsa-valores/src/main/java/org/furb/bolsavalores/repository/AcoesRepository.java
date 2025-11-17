package org.furb.bolsavalores.repository;

import org.furb.bolsavalores.model.Acao;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AcoesRepository extends MongoRepository<Acao, String> {
    List<Acao> findBySymbol(String symbol);
    Optional<Acao> findBySymbolAndRegularMarketTime(String symbol, Instant regularMarketTime);
}
