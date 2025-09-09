package com.example.disbursement.controller;

import com.example.disbursement.dto.DisbursementRequest;
import com.example.disbursement.model.BulkDisbursement;
import com.example.disbursement.model.Disbursement;
import com.example.disbursement.service.DisbursementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/disbursements")
@RequiredArgsConstructor
public class DisbursementController {

    private final DisbursementService service;

    @PostMapping
    public Mono<Disbursement> create(@Valid @RequestBody DisbursementRequest request) {
        return service.createSingle(request);
    }

    @PostMapping("/bulk")
    public Mono<BulkDisbursement> createBulk(@RequestBody List<DisbursementRequest> requests) {
        return service.createBulk(requests);
    }

    @GetMapping("/{id}")
    public Mono<Disbursement> get(@PathVariable String id) {
        return service.get(UUID.fromString(id));
    }
    @GetMapping("/test")
    public String test() {
        return "Hello";
    }

     @PostMapping("/callback")
    public String handleCallback(@RequestBody String payload) {
        System.out.println("📩 Callback received: " + payload);
        // You can parse payload into a DTO if you know the structure
        return "Callback received successfully";
    }

    @GetMapping("/callback")
    public String testCallback() {
        return "Callback endpoint is up!";
    }

    @GetMapping(produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<Disbursement> listAll() {
        return service.findAll();
    }

    // @GetMapping("/get-providers",produces = MediaType.APPLICATION_NDJSON_VALUE)
    // public Flux<PaymentProvider> getAllProviders() {
    //     return service.findAllPaymentProviders();
    // }
}
