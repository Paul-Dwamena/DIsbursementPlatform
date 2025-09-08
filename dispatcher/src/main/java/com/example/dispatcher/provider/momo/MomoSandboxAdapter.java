package com.example.dispatcher.provider.momo;

import com.example.dispatcher.provider.PayoutProvider;
import com.example.dispatcher.service.ProviderRequest;
import com.example.dispatcher.service.ProviderResult;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@Component
public class MomoSandboxAdapter implements PayoutProvider {

    @Override
    public String channel() {
        return "MOMO";
    }

    @Override
    public Mono<ProviderResult> disburse(ProviderRequest req) {
        return Mono.delay(Duration.ofMillis(150))
                .map(t -> ProviderResult.success("MOMO-" + UUID.randomUUID()))
                .onErrorResume(ex -> Mono.just(ProviderResult.failed("MOMO_ERR", ex.getMessage())));
    }
}
