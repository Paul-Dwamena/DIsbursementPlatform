package com.example.disbursement.repository;

import com.example.disbursement.model.BulkDisbursement;
import com.example.disbursement.model.BulkDisbursementStatus;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface BulkDisbursementRepo extends ReactiveCrudRepository<BulkDisbursement, UUID> {

    Mono<BulkDisbursement> findByBatchReference(String batchReference);

    Flux<BulkDisbursement> findByStatus(BulkDisbursementStatus status);
}
