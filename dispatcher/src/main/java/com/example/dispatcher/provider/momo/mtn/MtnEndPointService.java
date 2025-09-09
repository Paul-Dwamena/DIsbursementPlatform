package com.example.dispatcher.provider.momo.mtn;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;

@Service
public class MtnEndPointService {

    private final WebClient webClient;
    private final String targetEnvironment;
    private final String subscriptionKey;  // <-- add this

    public MtnEndPointService(WebClient mtnWebClient,
                              @Value("${mtn.sandbox.targetEnvironment}") String targetEnvironment,
                              @Value("${mtn.sandbox.subscriptionKey}") String subscriptionKey) {
        this.webClient = mtnWebClient;
        this.targetEnvironment = targetEnvironment;
        this.subscriptionKey = subscriptionKey;
    }

    public Mono<Map<String, Object>> depositV1(Map<String, Object> payload, String token,
                                           String callbackUrl, String referenceId) {
    return post("/v1_0/deposit", payload, token, callbackUrl, referenceId);
    }

    public Mono<Map<String, Object>> depositV2(Map<String, Object> payload, String token,
                                           String callbackUrl, String referenceId) {
    return post("/v2_0/deposit", payload, token, callbackUrl, referenceId);
    }


    
    public Mono<Map<String, Object>> transfer(Map<String, Object> payload, String token) {
        return post("/v1_0/transfer", payload, token);
    }

    public Mono<Map<String, Object>> refund(Map<String, Object> payload, String token) {
        return post("/v1_0/refund", payload, token);
    }

    public Mono<Map<String, Object>> getAccountDetails(String token, String accountHolderIdType,
                                                       String accountHolderId) {
        String uri = String.format("/v1_0/accountholder/%s/%s/basicuserinfo", accountHolderIdType, accountHolderId);
        return get(uri, token);
    }

    public Mono<Map<String, Object>> getBalance(String token) {
        return get("/v1_0/account/balance", token);
    }

    public Mono<Map<String, Object>> getPaymentStatus(String referenceId, String status, String token) {
        return get("/v1_0/" + status + "/" + referenceId, token);
    }

    private Mono<Map<String, Object>> post(String uri, Map<String, Object> payload, String token) {
        return post(uri, payload, token, null, null);
    }

    private Mono<Map<String, Object>> post(String uri, Map<String, Object> payload, String token,
                                           String callbackUrl, String referenceId) {
        return webClient.post()
                .uri(uri)
                .headers(headers -> {
                    headers.setBearerAuth(token);
                    headers.add("Ocp-Apim-Subscription-Key", subscriptionKey); // ✅ fixed
                    headers.add("X-Target-Environment", targetEnvironment);
                    if (callbackUrl != null)
                        headers.add("X-Callback-Url", callbackUrl);
                    if (referenceId != null)
                        headers.add("X-Reference-Id", referenceId);
                    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
                })
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    private Mono<Map<String, Object>> get(String uri, String token) {
        return get(uri, token, null, null);
    }

    private Mono<Map<String, Object>> get(String uri, String token, String callbackUrl, String referenceId) {
        return webClient.get()
                .uri(uri)
                .headers(headers -> {
                    headers.setBearerAuth(token);
                    headers.add("Ocp-Apim-Subscription-Key", subscriptionKey); // ✅ fixed
                    headers.add("X-Target-Environment", targetEnvironment);
                    if (callbackUrl != null)
                        headers.add("X-Callback-Url", callbackUrl);
                    if (referenceId != null)
                        headers.add("X-Reference-Id", referenceId);
                    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
                })
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {});
    }
}
