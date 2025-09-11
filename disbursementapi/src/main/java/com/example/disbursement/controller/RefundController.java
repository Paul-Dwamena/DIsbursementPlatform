package com.example.disbursement.controller;

import com.example.disbursement.dto.RefundRequest;
import com.example.disbursement.model.Refund;
import com.example.disbursement.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;

import java.util.UUID;

@RestController
@RequestMapping("/api/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

     @PostMapping
    public Mono<Refund> create(@Valid @RequestBody RefundRequest request) {
        return refundService.createRefund(request);
    }

    @GetMapping("/{id}")
    public Mono<Refund> get(@PathVariable String id) {
        return refundService.get(UUID.fromString(id));
    }

    @GetMapping(produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<Refund> listAll() {
        return refundService.findAll();
    }
}

