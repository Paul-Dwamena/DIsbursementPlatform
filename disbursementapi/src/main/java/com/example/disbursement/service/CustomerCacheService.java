package com.example.disbursement.service;

import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.stereotype.Service;

import com.example.disbursement.dto.CustomerDetails;
import com.example.disbursement.model.Customer;
import com.example.disbursement.repository.CustomerRepo;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class CustomerCacheService {
     private final ReactiveRedisOperations<String, String> redis;
    private final CustomerRepo customerRepo;
    private final ObjectMapper mapper;

    
    public Mono<Customer> findOrCreate(CustomerDetails details) {
    String key = "CUS" + details.getPhoneNumber();

    return redis.opsForValue().get(key)
        .flatMap(json -> {
            try {
                Customer cached = mapper.readValue(json, Customer.class);
                // Only use cached customer if it exists in DB
                if (cached.getId() == null) return Mono.empty();
                return customerRepo.existsById(cached.getId()) // check DB
                        .flatMap(exists -> exists ? Mono.just(cached) : Mono.empty());
            } catch (Exception e) {
                return Mono.empty();
            }
        })
        .switchIfEmpty(
            customerRepo.findByPhone(details.getPhoneNumber())
                .switchIfEmpty(
                    customerRepo.save(Customer.builder()
                        .name(details.getName())
                        .phone(details.getPhoneNumber())
                        .build()
                    )
                )
                .flatMap(saved -> {
                    try {
                        String json = mapper.writeValueAsString(saved);
                        return redis.opsForValue().set(key, json, Duration.ofHours(24))
                            .thenReturn(saved);
                    } catch (Exception e) {
                        return Mono.just(saved);
                    }
                })
        );
}

    
}
