package com.example.dispatcher.provider.momo.telecel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;



@Service
public class TelecelEndPointService {

    private final WebClient webClient;
    private final String origin;

    public TelecelEndPointService(WebClient telecelWebClient,
                                  @Value("${telecel.sandbox.origin:*}") String origin) {
        this.webClient = telecelWebClient;
        this.origin = origin;
    }

   
    private Mono<Map<String, Object>> post(String uri, Map<String, Object> payload, String encryptedSessionKey) {
        return webClient.post()
                .uri(uri)
                .headers(headers -> {
                    headers.setBearerAuth(encryptedSessionKey);
                    headers.add("Origin", "*");
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
                })
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

   
    public Mono<Map<String, Object>> initiatePayment(Map<String, Object> payload, String encryptedSessionKey) {
        return post("/b2bPayment/", payload, encryptedSessionKey);
    }

    public Mono<Map<String, Object>> initiatePaymentToCustomer(Map<String, Object> payload, String encryptedSessionKey) {
        return post("/b2cPayment/", payload, encryptedSessionKey);
    }

    public Mono<Map<String, Object>> reverseTransaction(Map<String, Object> payload, String encryptedSessionKey) {
        return post("/reversal/", payload, encryptedSessionKey);
    }

    
    
}
