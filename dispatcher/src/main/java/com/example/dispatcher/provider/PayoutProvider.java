package com.example.dispatcher.provider;
import com.example.dispatcher.service.ProviderRequest;
import com.example.dispatcher.service.ProviderResult;
import reactor.core.publisher.Mono;

public interface PayoutProvider {
    Mono<ProviderResult>disburse(ProviderRequest req);
    String channel();

    
}
