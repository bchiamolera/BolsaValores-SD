package org.furb.bolsavalores.service;

import org.furb.bolsavalores.model.BrapiResponse;
import org.furb.bolsavalores.model.BrapiResponseWrapper;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class ConsultaBolsaService {
    String token = "2AeR5xx7b8xcFCWRjgREH7";

    private final WebClient webClient;

    public ConsultaBolsaService(WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<BrapiResponse> getMessage(String ticker) {
        return this.webClient.get()
                .uri("/api/quote/" + ticker + "?token=" + token)
                .retrieve()
                .bodyToMono(BrapiResponseWrapper.class)
                .map(wrapper -> wrapper.getResults().get(0));
    }
}
