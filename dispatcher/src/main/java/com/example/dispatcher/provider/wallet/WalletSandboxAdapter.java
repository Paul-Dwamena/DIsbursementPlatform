package com.example.dispatcher.provider.wallet;

import com.example.dispatcher.provider.PayoutProvider;
import com.example.dispatcher.service.ProviderRequest;
import com.example.dispatcher.service.ProviderResult;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@Component
public class WalletSandboxAdapter implements PayoutProvider {

    @Override
    public String channel() {
        return "MOMO";
    }

    @Override
    public Mono<ProviderResult> disburse(ProviderRequest req) {
        return Mono.delay(Duration.ofMillis(150))
                .map(t -> ProviderResult.success("WALLET-" + UUID.randomUUID()))
                .onErrorResume(ex -> Mono.just(ProviderResult.failed("WALLET_ERR", ex.getMessage())));
    }
}
