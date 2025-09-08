package com.example.dispatcher.provider.bank;
import com.example.dispatcher.provider.PayoutProvider;
import com.example.dispatcher.service.ProviderRequest;
import com.example.dispatcher.service.ProviderResult;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@Component
public class BankSandboxAdapter implements PayoutProvider {

    @Override
    public String channel(){
        return "BANK";
    }

    @Override
    public Mono<ProviderResult> disburse(ProviderRequest req){
        return Mono.delay(Duration.ofMillis(200))
                .map(t -> ProviderResult.success("BANK-"+UUID.randomUUID()))
                .onErrorResume(ex -> Mono.just(ProviderResult.failed("BANK_ERR", ex.getMessage())));
    }
    
}
