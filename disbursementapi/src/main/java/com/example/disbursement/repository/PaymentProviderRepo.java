package com.example.disbursement.repository;

import com.example.disbursement.model.PaymentProvider;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PaymentProviderRepo extends ReactiveCrudRepository<PaymentProvider, UUID> {

    Mono<PaymentProvider> findByName(String name);

    Mono<PaymentProvider> findByCode(String code);

    Flux<PaymentProvider> findByChannel(String channel);

    Flux<PaymentProvider> findByActiveTrue();
}
