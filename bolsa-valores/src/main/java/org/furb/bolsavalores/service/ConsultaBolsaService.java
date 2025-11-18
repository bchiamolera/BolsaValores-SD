package org.furb.bolsavalores.service;

import org.furb.bolsavalores.config.AppConfig;
import org.furb.bolsavalores.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Map;

@Service
public class ConsultaBolsaService {

    private String brapiToken;
    private String stockdataToken;
    private final Map<String, WebClient> clients;
    private final AppConfig config;

    public ConsultaBolsaService(@Value("${brapi.token}") String brapiToken,
                                @Value("${stockdata.token}") String stockdataToken,
                                Map<String, WebClient> clients,
                                AppConfig config) {
        this.brapiToken = brapiToken;
        this.stockdataToken = stockdataToken;
        this.clients = clients;
        this.config = config;
    }

    public Acao consultar() {
        String api = config.getApi();
        String ticker = config.getTicker();

        WebClient client = clients.get(api.toLowerCase());
        if (client == null) {
            throw new IllegalArgumentException("API não suportada: " + api);
        }

        return switch (api.toLowerCase().trim()) {
            case "brapi" -> consultaBrapi(client, ticker);
            case "stockdata" -> consultaStockData(client, ticker);
            default -> throw new IllegalArgumentException("API inválida: " + api);
        };
    }

    private Acao consultaBrapi(WebClient webClient, String ticker) {
        BrapiResponse response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/quote/{ticker}")
                        .queryParam("token", brapiToken)
                        .build(ticker))
                .retrieve()
                .bodyToMono(BrapiResponseWrapper.class)
                .map(wrapper -> {
                    if (wrapper.getResults() == null || wrapper.getResults().isEmpty()) {
                        throw new RuntimeException("BRAPI retornou uma lista vazia para: " + ticker);
                    }
                    return wrapper.getResults().get(0);
                })
                .block(); // executa de forma síncrona

        if (response == null) {
            throw new RuntimeException("Não foi possível obter dados da BRAPI para: " + ticker);
        }

        Acao acao = new Acao();
        acao.setSymbol(response.getSymbol());
        acao.setShortName(response.getShortName());
        acao.setLongName(response.getLongName());
        acao.setRegularMarketPrice(response.getRegularMarketPrice());
        if (response.getRegularMarketTime() != null) {
            acao.setRegularMarketTime(Instant.parse(response.getRegularMarketTime()));
        }

        return acao;
    }

    private Acao consultaStockData(WebClient webClient, String ticker) {
        StockDataResponse response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/data/quote")
                        .queryParam("symbols", ticker)
                        .queryParam("api_token", stockdataToken)
                        .build())
                .retrieve()
                .bodyToMono(StockDataResponseWrapper.class)
                .map(wrapper -> {
                    if (wrapper.getData() == null || wrapper.getData().isEmpty()) {
                        throw new RuntimeException("StockData retornou uma lista vazia para: " + ticker);
                    }
                    return wrapper.getData().get(0);
                })
                .block();

        if (response == null) {
            throw new RuntimeException("Não foi possível obter dados da StockData para: " + ticker);
        }

        Acao acao = new Acao();
        acao.setSymbol(response.getTicker());
        acao.setShortName(response.getName());
        acao.setLongName(response.getName()); // StockData não separa short/long
        acao.setRegularMarketPrice(response.getPrice());
        if (response.getLast_trade_time() != null) {
            String timestamp = response.getLast_trade_time();
            // StockData usa formato com microssegundos: 2023-09-12T15:59:56.000000
            // Alguns servidores enviam sem 'Z', então adicionamos:
            if (!timestamp.endsWith("Z")) {
                timestamp += "Z";
            }

            acao.setRegularMarketTime(Instant.parse(timestamp));
        }

        return acao;
    }
}
