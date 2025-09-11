package com.example.disbursement.service;

import com.example.common.config.KafkaTopics;
import com.example.common.exception.CustomNotFoundException;
import com.example.disbursement.dto.RefundRequest;
import com.example.disbursement.model.*;
import com.example.disbursement.repository.*;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


import java.math.BigDecimal;
import java.time.OffsetDateTime;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefundService {

        private final RefundRepo repo;
        private final DisbursementRepo disbursementrepo;
        private final OutboxRepo outbox;
        private final TransactionalOperator txOperator;
        @Autowired
        private DatabaseClient dbClient;

        public Mono<Refund> createRefund(RefundRequest req) {
    

        return disbursementrepo.findByReference(req.getDisbursementReference())
            .switchIfEmpty(Mono.error(new CustomNotFoundException(
                "Original disbursement not found: " + req.getDisbursementReference())))
            .flatMap(original -> {
                if (req.getAmount().compareTo(original.getAmount()) > 0) {
                    return Mono.error(new IllegalArgumentException("Refund amount exceeds original amount"));
                }

                UUID refundId = UUID.randomUUID();
                UUID transactionId = UUID.randomUUID();

                return dbClient.sql("""
                            INSERT INTO refunds
                                (id, original_disbursement_id, amount, currency, status, provider_id, narration, transaction_id, reference)
                            VALUES (:id, :originalId, :amount, :currency, :status, :providerId, :narration, :transactionId, :reference)
                            RETURNING *
                            """)
                        .bind("id", refundId)
                        .bind("originalId", original.getId())
                        .bind("amount", req.getAmount())
                        .bind("currency", original.getCurrency())
                        .bind("status", "PENDING")
                        .bind("providerId", original.getProviderId())
                        .bind("narration", req.getReason() != null ? req.getReason() : original.getNarration())
                        .bind("transactionId", transactionId)
                        .bind("reference", req.getRefundReference())
                        .map((row, meta) -> {
                            Refund r = new Refund();
                            r.setId(row.get("id", UUID.class));
                            r.setOriginalDisbursementId(row.get("original_disbursement_id", UUID.class));
                            r.setAmount(row.get("amount", BigDecimal.class));
                            r.setCurrency(row.get("currency", String.class));
                            r.setStatus(RefundStatus.valueOf(row.get("status", String.class)));
                            r.setProviderId(row.get("provider_id", UUID.class));
                            r.setTransactionId(row.get("transaction_id", UUID.class));
                            r.setReference(row.get("reference", String.class));  
                            r.setNarration(row.get("narration", String.class));
                            r.setCreatedAt(row.get("created_at", OffsetDateTime.class));
                            r.setUpdatedAt(row.get("updated_at", OffsetDateTime.class));
                            return r;
                        })
                    .one()
                    .flatMap(savedRefund -> {
                        ObjectNode evt = JsonNodeFactory.instance.objectNode()
                            .put("refundId", savedRefund.getId().toString())
                            .put("originalDisbursementId", original.getId().toString())
                            .put("amount", savedRefund.getAmount().toPlainString())
                            .put("currency", savedRefund.getCurrency())
                            .put("refundReference", savedRefund.getReference())
                            .put("reversalId", original.getTransactionId().toString())
                            .put("channel", original.getChannel().name())
                            .put("providerId", original.getProviderId().toString())
                            .put("transactionId", transactionId.toString())
                            .put("reason", savedRefund.getNarration());


                        return outbox.save(OutboxEvent.builder()
                                .aggregateId(savedRefund.getId())
                                .type(KafkaTopics.REFUND_REQUESTED)
                                .payload(evt.toString())
                                .headers(JsonNodeFactory.instance.objectNode().toString())
                                .createdAt(OffsetDateTime.now())
                                .build())
                            .thenReturn(savedRefund);
                    });
            })
            .as(txOperator::transactional);
}


    
        public Mono<Refund> get(UUID id) {
                return repo.findById(id);
        }

        public Flux<Refund> findAll() {
                return repo.findAll();
        }

       
}
