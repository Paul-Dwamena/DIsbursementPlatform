package com.example.disbursement.repository;

import com.example.disbursement.model.Refund;
import com.example.disbursement.model.RefundStatus;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface RefundRepo extends ReactiveCrudRepository<Refund, UUID> {

    Mono<Refund> findByReference(String reference);

    Flux<Refund> findAllByReferenceIn(List<String> references);

    Flux<Refund> findByStatus(RefundStatus status);

    Flux<Refund> findAllByTransactionIdIn(List<UUID> transactionIds);

    Mono<Refund> findByTransactionId(UUID transactionId);

    @Query("SELECT * FROM refunds WHERE status = :status ORDER BY created_at DESC LIMIT :limit")
    Flux<Refund> findRecentByStatus(@Param("status") String status, @Param("limit") int limit);

                                  

}
