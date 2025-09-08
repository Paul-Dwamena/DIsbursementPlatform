package com.example.disbursement.repository;

import com.example.disbursement.model.Customer;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface CustomerRepo extends ReactiveCrudRepository<Customer, UUID> {

    Mono<Customer> findByPhone(String phone);

    Mono<Customer> findByEmail(String email);

    Flux<Customer> findByNameContainingIgnoreCase(String name);
}

