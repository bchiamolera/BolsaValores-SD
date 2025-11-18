package org.furb.bolsavalores.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Classe de configuração responsável por criar e disponibilizar
 * instâncias de {@link WebClient} para consumo de múltiplas APIs externas.
 *
 * <p>O objetivo é centralizar comportamentos comuns como:</p>
 * <ul>
 *     <li>Headers padrão (Content-Type: application/json)</li>
 *     <li>Filtros globais (logging, tracing, métricas)</li>
 *     <li>Evitar duplicação de configurações para cada API</li>
 * </ul>
 *
 * <p>APIs suportadas:</p>
 * <ul>
 *     <li>BRAPI — https://brapi.dev/api</li>
 *     <li>StockData — https://api.stockdata.org</li>
 * </ul>
 *
 * <p>Cada WebClient é identificado por um {@code @Qualifier} para permitir
 * injeção explícita quando necessário.</p>
 */
@Configuration
public class WebClientConfig {

    /**
     * Builder base compartilhado entre todos os WebClients.
     *
     * <p>Inclui:</p>
     * <ul>
     *     <li>Content-Type global como JSON</li>
     *     <li>Filter genérico (pode ser usado para logs, tracing ou métricas)</li>
     * </ul>
     *
     * @return builder base reutilizável
     */
    @Bean
    public WebClient.Builder baseWebClientBuilder() {
        return WebClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .filter((request, next) -> {
                    return next.exchange(request);
                });
    }

    /**
     * WebClient configurado para a API BRAPI.
     *
     * @param baseWebClientBuilder builder base compartilhado
     * @return WebClient para BRAPI
     */
    @Bean(name = "brapi")
    public WebClient brapiWebClient(WebClient.Builder baseWebClientBuilder) {
        return baseWebClientBuilder
                .clone()
                .baseUrl("https://brapi.dev/api")
                .build();
    }

    /**
     * WebClient configurado para a API StockData.org.
     *
     * <p>A URL base é usada para endpoints como:</p>
     * <code>/data/quote?symbols=AAPL&api_token=XXX</code>
     *
     * @param baseWebClientBuilder builder base compartilhado
     * @return WebClient para StockData
     */
    @Bean(name = "stockdata")
    public WebClient stockDataWebClient(WebClient.Builder baseWebClientBuilder) {
        return baseWebClientBuilder
                .clone()
                .baseUrl("https://api.stockdata.org/v1")
                .build();
    }
}
