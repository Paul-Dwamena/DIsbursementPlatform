package com.example.dispatcher.provider.momo.mtn;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class MtnTokenService {

    private final MtnAuthService mtnAuthService;
    private final StringRedisTemplate redisTemplate;

    private static final String TOKEN_KEY = "mtn:access_token";

    public MtnTokenService(MtnAuthService mtnAuthService, StringRedisTemplate redisTemplate) {
        this.mtnAuthService = mtnAuthService;
        this.redisTemplate = redisTemplate;
    }

    public Mono<String> getAccessToken() {
        String cachedToken = redisTemplate.opsForValue().get(TOKEN_KEY);
        if (cachedToken != null) {
            return Mono.just(cachedToken);
        } 

        return mtnAuthService.createAccessToken()
                .flatMap(tokenResponse -> {
                    String token = tokenResponse.getAccessToken();
                    long expiresIn = tokenResponse.getExpiresIn();

                    long ttlSeconds = Math.max(0, expiresIn - 60);

                    redisTemplate.opsForValue()
                            .set(TOKEN_KEY, token, Duration.ofSeconds(ttlSeconds));

                    return Mono.just(token);
                });
    }
}
