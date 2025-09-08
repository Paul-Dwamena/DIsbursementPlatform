package com.example.disbursement.repository;

import com.example.disbursement.model.BulkItem;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface BulkItemRepo extends ReactiveCrudRepository<BulkItem, UUID> {

    Flux<BulkItem> findByBulkId(UUID bulkId);

    Flux<BulkItem> findByDisbursementId(UUID disbursementId);
}
