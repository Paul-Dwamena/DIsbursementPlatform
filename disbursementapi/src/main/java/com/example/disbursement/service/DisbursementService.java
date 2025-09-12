package com.example.disbursement.service;

import com.example.common.config.KafkaTopics;
import com.example.common.exception.CustomNotFoundException;
import com.example.disbursement.dto.BulkDisbursementRequest;
import com.example.disbursement.dto.BulkDisbursementResult;
import com.example.disbursement.dto.DisbursementRequest;
import com.example.disbursement.model.*;
import com.example.disbursement.repository.*;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;


import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DisbursementService {

        private final DisbursementRepo repo;
        
        private final PaymentProviderRepo providerRepo;
        private final OutboxRepo outbox;
        private final ReactiveStringRedisTemplate redis;
        
        private final TransactionalOperator txOperator;
        private final CustomerCacheService customerCacheService;
        @Autowired
        private DatabaseClient dbClient;

        public Mono<Disbursement> createSingle(DisbursementRequest req) {
        String idemKey = "idem:request:" + req.getReference();
        ObjectMapper mapper = new ObjectMapper();

        return redis.opsForValue().get(idemKey)
        .flatMap(existingId -> repo.findById(UUID.fromString(existingId))
            .switchIfEmpty(redis.opsForValue().delete(idemKey).then(Mono.<Disbursement>empty())))
        .switchIfEmpty(Mono.defer(() -> {
            UUID transactionId = UUID.randomUUID();
            return customerCacheService.findOrCreate(req.getCustomer())
                .flatMap(savedCustomer -> 
                    providerRepo.findByCode(req.getProvider())
                        .switchIfEmpty(Mono.error(new CustomNotFoundException(
                            "Provider not found: " + req.getProvider())))
                        .flatMap(provider -> {
                            

                            String paymentDetailsJson;
                            try {
                                 paymentDetailsJson = mapper.writeValueAsString(req.getPaymentDetails());

                            } catch (JsonProcessingException e) {
                                return Mono.error(new RuntimeException("Failed to serialize KYC details", e));
                            }

                          

                            // Insert Disbursement using DatabaseClient
                            return dbClient.sql("""
                                    INSERT INTO disbursements
                                        (reference, transaction_id, customer_id, channel, amount, currency, status, provider_id, narration, payment_details, disburse_type)
                                        VALUES (:reference, :transactionId, :customerId, :channel, :amount, :currency, :status, :providerId, :narration, CAST(:payment_details AS jsonb), :disburse_type)
                                        RETURNING *
        
                                    """)
                                .bind("reference", req.getReference())
                                .bind("transactionId", transactionId)
                                .bind("customerId", UUID.fromString(savedCustomer.getId().toString()))
                                .bind("channel", req.getChannel().toUpperCase())
                                .bind("amount", req.getAmount())
                                .bind("currency",req.getCurrency())
                                .bind("status", "PENDING")
                                .bind("providerId", UUID.fromString(provider.getId().toString()))
                                .bind("narration", req.getNarration())
                                .bind("payment_details", paymentDetailsJson)
                                .bind("disburse_type", req.getDisburseType())
                                .map((row, meta) -> {
                                    Disbursement d = new Disbursement();
                                    d.setId(row.get("id", UUID.class));
                                    d.setReference(row.get("reference", String.class));
                                    d.setTransactionId(row.get("transaction_id", UUID.class));
                                    d.setCustomerId(row.get("customer_id", UUID.class));
                                    d.setChannel(PaymentChannel.valueOf(row.get("channel", String.class)));
                                    d.setAmount(row.get("amount", BigDecimal.class));
                                    d.setCurrency(row.get("currency", String.class));
                                    d.setStatus(DisbursementStatus.valueOf(row.get("status", String.class)));
                                    d.setProviderId(row.get("provider_id", UUID.class));
                                    d.setNarration(row.get("narration", String.class));
                                    d.setPaymentDetails(row.get("payment_details", String.class));
                                    d.setDisburseType(row.get("disburse_type", String.class));

                                    return d;
                                })
                                .one()
                                .flatMap(savedDisbursement -> {
                                  

                                    // cache idemKey
                                    return redis.opsForValue()
                                        .set(idemKey, savedDisbursement.getId().toString(), Duration.ofHours(24))
                                        .thenReturn(savedDisbursement);
                                })
                                .flatMap(savedDisbursement -> {

                                    ObjectNode customerNode = mapper.createObjectNode()
                                    .put("id", savedCustomer.getId().toString())
                                    .put("name", savedCustomer.getName())
                                    .put("phone", savedCustomer.getPhone())
                                    .set("paymentDetails", mapper.valueToTree(req.getPaymentDetails()));

                                ObjectNode evt = JsonNodeFactory.instance.objectNode()
                                    .put("disbursementId", savedDisbursement.getId().toString())
                                    .put("reference", savedDisbursement.getReference())
                                    .put("transactionId", savedDisbursement.getTransactionId().toString())
                                    .put("channel", savedDisbursement.getChannel().name())
                                    .put("amount", savedDisbursement.getAmount().toPlainString())
                                    .put("currency", savedDisbursement.getCurrency())
                                    .put("provider", savedDisbursement.getProviderId().toString())
                                    .put("disburse_type", savedDisbursement.getDisburseType())
                                    .put("narration", savedDisbursement.getNarration())
                                    .set("customer", customerNode);

                                
                                    // Build Outbox Event
                                    
                                    

                                    return outbox.save(OutboxEvent.builder()
                                            .aggregateId(savedDisbursement.getId())
                                            .type(KafkaTopics.PAYOUT_REQUESTED)
                                            .payload(evt.toString())
                                            .headers(JsonNodeFactory.instance.objectNode().toString())
                                            .createdAt(OffsetDateTime.now())
                                            .build())
                                        .thenReturn(savedDisbursement);
                                });
                        })
                );
        }))
        .as(txOperator::transactional).onErrorResume(e -> {
        e.printStackTrace(); // log full stacktrace
        return Mono.error(new RuntimeException("createSingle failed: " + e.getMessage(), e));
    });
}


     


    public Mono<BulkDisbursementResult> createBulk(String batchNumber, List<DisbursementRequest> requests) {

    BulkDisbursement batch = BulkDisbursement.builder()
            .id(UUID.randomUUID())
            .batchReference(batchNumber)
            .status(BulkDisbursementStatus.PENDING)
            .totalCount(requests.size())
            .successCount(0)
            .failedCount(0)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

    // Insert bulk_disbursement row first (transactional if needed)
    return dbClient.sql("""
            INSERT INTO bulk_disbursements
                (id, batch_reference, status, total_count, success_count, failed_count, created_at, updated_at)
            VALUES (:id, :batchReference, :status, :totalCount, :successCount, :failedCount, :createdAt, :updatedAt)
        """)
        .bind("id", batch.getId())
        .bind("batchReference", batch.getBatchReference())
        .bind("status", batch.getStatus().name())
        .bind("totalCount", batch.getTotalCount())
        .bind("successCount", batch.getSuccessCount())
        .bind("failedCount", batch.getFailedCount())
        .bind("createdAt", batch.getCreatedAt())
        .bind("updatedAt", batch.getUpdatedAt())
        .fetch()
        .rowsUpdated()
        .doOnNext(rows -> System.out.println("Inserted bulk row, rows=" + rows))
        .thenMany(Flux.fromIterable(requests))
        .flatMap(req ->
            createSingle(req)
                .doOnNext(disb -> System.out.println("Created disbursement: " + disb.getReference() + " id=" + disb.getId()))
                .flatMap(disb -> dbClient.sql("""
                        INSERT INTO bulk_items (id, bulk_id, disbursement_id)
                        VALUES (:id, :bulkId, :disbursementId)
                    """)
                    .bind("id", UUID.randomUUID())
                    .bind("bulkId", batch.getId())
                    .bind("disbursementId", disb.getId())
                    .fetch()
                    .rowsUpdated()
                    .doOnNext(rows -> System.out.println("Inserted bulk_item for disbursement=" + disb.getId() + ", rows=" + rows))
                    .thenReturn(Tuples.of(disb.getReference(), true))
                )
                .onErrorResume(e -> {
                    
                    return Mono.just(Tuples.of(req.getReference(), false));
                })
        , 256) // concurrency
        .collectList()
        .flatMap(results -> {
            // Count success/failures
            long successCount = results.stream().filter(Tuple2::getT2).count();
            long failedCount = results.size() - successCount;

            List<String> failedRefs = results.stream()
                    .filter(r -> !r.getT2())
                    .map(Tuple2::getT1)
                    .toList();

            BulkDisbursementStatus finalStatus = failedCount == 0
                    ? BulkDisbursementStatus.COMPLETED
                    : (successCount == 0 ? BulkDisbursementStatus.FAILED : BulkDisbursementStatus.PARTIAL);

            // System.out.println("Bulk processing done. Success=" + successCount + ", Failed=" + failedCount);

            // Update bulk_disbursement with final counts & status
            return dbClient.sql("""
                    UPDATE bulk_disbursements
                    SET success_count = :successCount,
                        failed_count = :failedCount,
                        status = :status,
                        updated_at = :updatedAt
                    WHERE id = :id
                """)
                .bind("successCount", (int) successCount)
                .bind("failedCount", (int) failedCount)
                .bind("status", finalStatus.name())
                .bind("updatedAt", OffsetDateTime.now())
                .bind("id", batch.getId())
                .fetch()
                .rowsUpdated()
                .doOnNext(rows -> System.out.println("Updated bulk_disbursement counts, rows=" + rows))
                .then(Mono.fromCallable(() -> {
                    BulkDisbursement updated = batch.toBuilder()
                        .successCount((int) successCount)
                        .failedCount((int) failedCount)
                        .status(finalStatus)
                        .updatedAt(OffsetDateTime.now())
                        .build();

                    return new BulkDisbursementResult(updated, failedRefs);
                }));
        });
}




        public Mono<Disbursement> get(UUID id) {
                return repo.findById(id);
        }

        public Flux<Disbursement> findAll() {
                return repo.findAll();
        }

        public Flux<PaymentProvider> findAllPaymentProviders() {
                return providerRepo.findAll();
        }
}
