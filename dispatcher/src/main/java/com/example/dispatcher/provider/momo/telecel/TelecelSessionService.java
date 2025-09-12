package com.example.dispatcher.provider.momo.telecel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.time.Duration;


import java.util.Map;

@Service
public class TelecelSessionService {

    private final WebClient webClient;
    private final StringRedisTemplate redisTemplate;

    private static final String SESSION_KEY = "telecel:session_id";

    @Value("${telecel.sandbox.apiKey}")
    private String apiKey;

    @Value("${telecel.sandbox.publicKey}")
    private String publicKey;

    public TelecelSessionService(WebClient telecelWebClient, StringRedisTemplate redisTemplate) {
        this.webClient = telecelWebClient;
        this.redisTemplate = redisTemplate;
    }

    private String getEncryptedApiKey() throws Exception {
        byte[] decodedKey = Base64.getDecoder().decode(publicKey);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decodedKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey rsaPublicKey = keyFactory.generatePublic(keySpec);

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);

        byte[] encryptedBytes = cipher.doFinal(apiKey.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    /**
     * Retrieves a valid session ID from Redis or generates a new one if expired.
     */
    public Mono<String> getBearerToken() {
    String cachedSessionId = redisTemplate.opsForValue().get(SESSION_KEY);
    if (cachedSessionId != null) {
        try {
            return Mono.just(encryptWithPublicKey(cachedSessionId));
        } catch (Exception e) {
            return Mono.error(new RuntimeException("Failed to encrypt cached sessionId", e));
        }
    }

    return generateSessionKey()
            .flatMap(response -> {
                String sessionId = (String) response.get("output_SessionID");

                // Cache raw SessionID for 59 minutes
                long ttlSeconds = Duration.ofMinutes(1).toSeconds();
                redisTemplate.opsForValue()
                        .set(SESSION_KEY, sessionId, Duration.ofSeconds(ttlSeconds));

                try {
                    return Mono.just(encryptWithPublicKey(sessionId));
                } catch (Exception e) {
                    return Mono.error(new RuntimeException("Failed to encrypt new sessionId", e));
                }
            });
}


    private Mono<Map<String, Object>> generateSessionKey() {
        try {
            String encryptedApiKey = getEncryptedApiKey();

            return webClient.get()
                    .uri("/getSession/")
                    .header("Origin", "*")
                    .header("Authorization", "Bearer " + encryptedApiKey)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Mono.error(new RuntimeException("Failed to encrypt API key", e));
        }
    }

    private String encryptWithPublicKey(String data) throws Exception {
    byte[] decodedKey = Base64.getDecoder().decode(publicKey);

    X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decodedKey);
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    PublicKey rsaPublicKey = keyFactory.generatePublic(keySpec);

    Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
    cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);

    byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(encryptedBytes);
}

}
