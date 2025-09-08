package com.example.disbursement.repository;

import com.example.disbursement.model.Disbursement;
import com.example.disbursement.model.DisbursementStatus;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface DisbursementRepo extends ReactiveCrudRepository<Disbursement, UUID> {

    Mono<Disbursement> findByReference(String reference);

    Flux<Disbursement> findAllByReferenceIn(List<String> references); // ✅ new method

    Flux<Disbursement> findByStatus(DisbursementStatus status);

    Flux<Disbursement> findAllByTransactionIdIn(List<UUID> transactionIds);

    Mono<Disbursement> findByTransactionId(UUID transactionId);

    @Query("SELECT * FROM disbursements WHERE status = :status ORDER BY created_at DESC LIMIT :limit")
    Flux<Disbursement> findRecentByStatus(@Param("status") String status, @Param("limit") int limit);

                                  

}
