package com.example.dispatcher.provider.momo.mtn;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.dispatcher.dto.TokenResponse;

import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.Map;

@Service
public class MtnAuthService {

    private final WebClient webClient;

    @Value("${mtn.sandbox.apiUserId}")
    private String apiUserId;

    @Value("${mtn.sandbox.apiKey}")
    private String apiKey;

    @Value("${mtn.sandbox.targetEnvironment}")
    private String targetEnvironment;


    @Value("${mtn.sandbox.subscriptionKey}")
    private String subscriptionKey;


    
    public MtnAuthService(WebClient mtnWebClient) {
        this.webClient = mtnWebClient;
    }

    
    public Mono<TokenResponse> createAccessToken() {
        String basicAuth = Base64.getEncoder()
                .encodeToString((apiUserId + ":" + apiKey).getBytes());

    
        return webClient.post()
                .uri("/token/")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                .header("Ocp-Apim-Subscription-Key", subscriptionKey)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(TokenResponse.class);
    }


    public Mono<String> createOauth2Token() {
        String basicAuth = Base64.getEncoder()
                .encodeToString((apiUserId + ":" + apiKey).getBytes());

        return webClient.post()
                .uri("/oauth2/token/") 
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                .header("X-Target-Environment", targetEnvironment)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(Map.class)
                .map(map -> (String) map.get("access_token"));
    }
}

