package com.example.dispatcher.provider;
import com.example.dispatcher.service.ProviderResult;
import com.example.dispatcher.service.RefundRequestEvt;

import reactor.core.publisher.Mono;

public interface RefundProvider {
    Mono<ProviderResult>refundMomo(RefundRequestEvt req);
    String channel();

    
}
