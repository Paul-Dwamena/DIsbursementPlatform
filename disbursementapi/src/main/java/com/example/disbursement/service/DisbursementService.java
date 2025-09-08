package com.example.disbursement.service;

import com.example.common.config.KafkaTopics;
import com.example.common.exception.CustomNotFoundException;
import com.example.common.util.MapToJsonConverter;
import com.example.disbursement.dto.BankCustomerDetails;
import com.example.disbursement.dto.CustomerDetails;
import com.example.disbursement.dto.DisbursementRequest;
import com.example.disbursement.dto.MomoCustomerDetails;
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
import reactor.util.function.Tuples;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;


import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DisbursementService {

        private final DisbursementRepo repo;
        private final CustomerRepo customerRepo;
        private final PaymentProviderRepo providerRepo;
        private final OutboxRepo outbox;
        private final ReactiveStringRedisTemplate redis;
        private final BulkItemRepo bulkItemRepo;
        private final BulkDisbursementRepo bulkDisbursementRepo;
        private final TransactionalOperator txOperator;
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

            // Build Customer
            CustomerDetails details = req.getCustomer();
            Customer customer = new Customer();
            customer.setName(details.getName());
            customer.setPhone(details.getPhoneNumber());

            // Build KYC details
            Map<String, Object> kycDetails = new HashMap<>();
            if ("MOMO".equalsIgnoreCase(req.getChannel()) && details instanceof MomoCustomerDetails momo) {
                kycDetails.put("name", momo.getName());
                kycDetails.put("phoneNumber", momo.getPhoneNumber());
            } else if ("BANK".equalsIgnoreCase(req.getChannel()) && details instanceof BankCustomerDetails bank) {
                kycDetails.put("accountNumber", bank.getAccountNumber());
                kycDetails.put("accountName", bank.getAccountName());
                kycDetails.put("bankCode", bank.getBankCode());
                kycDetails.put("bankName", bank.getBankName());
                
            }

            try {
                customer.setKycDetails(mapper.writeValueAsString(kycDetails));
            } catch (JsonProcessingException e) {
                return Mono.error(new RuntimeException("Failed to serialize KYC details", e));
            }

            // Save Customer
            return customerRepo.save(customer)
                .flatMap(savedCustomer -> 
                    providerRepo.findByCode(req.getProvider())
                        .switchIfEmpty(Mono.error(new CustomNotFoundException(
                            "Provider not found: " + req.getProvider())))
                        .flatMap(provider -> {
                            System.out.println("✅ Found Provider: " + provider);

                            String paymentDetailsJson;
                            try {
                                 paymentDetailsJson = mapper.writeValueAsString(kycDetails);

                            } catch (JsonProcessingException e) {
                                return Mono.error(new RuntimeException("Failed to serialize KYC details", e));
                            }

                            

                            // Insert Disbursement using DatabaseClient
                            return dbClient.sql("""
                                    INSERT INTO disbursements
                                        (reference, transaction_id, customer_id, channel, amount, currency, status, provider_id, narration, payment_details)
                                        VALUES (:reference, :transactionId, :customerId, :channel, :amount, :currency, :status, :providerId, :narration, CAST(:payment_details AS jsonb))
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
                                .map((row, meta) -> {
                                    Disbursement d = new Disbursement();
                                    d.setId(row.get("id", UUID.class));
                                    d.setReference(row.get("reference", String.class));
                                    d.setTransactionId(row.get("transaction_id", UUID.class));
                                    d.setCustomerId(row.get("customer_id", String.class));
                                    d.setChannel(PaymentChannel.valueOf(row.get("channel", String.class)));
                                    d.setAmount(row.get("amount", BigDecimal.class));
                                    d.setCurrency(row.get("currency", String.class));
                                    d.setStatus(DisbursementStatus.valueOf(row.get("status", String.class)));
                                    d.setProviderId(row.get("provider_id", String.class));
                                    d.setNarration(row.get("narration", String.class));
                                    d.setPaymentDetails(row.get("payment_details", String.class));
                                    return d;
                                })
                                .one()
                                .flatMap(savedDisbursement -> {
                                    System.out.println("Saved Disbursement ID: " + savedDisbursement.getId());

                                    // cache idemKey
                                    return redis.opsForValue()
                                        .set(idemKey, savedDisbursement.getId().toString(), Duration.ofHours(24))
                                        .thenReturn(savedDisbursement);
                                })
                                .flatMap(savedDisbursement -> {

                                ObjectNode customerNode = mapper.valueToTree(savedCustomer);
                                if (savedCustomer.getKycDetails() != null) {
                                try {
                                        ObjectNode kycNode = (ObjectNode) mapper.readTree(savedCustomer.getKycDetails());
                                        customerNode.set("kycDetails", kycNode);
                                } catch (JsonProcessingException e) {
                                        return Mono.error(new RuntimeException("Failed to parse KYC JSON", e));
                                }
                                }
                                    // Build Outbox Event
                                    ObjectNode evt = JsonNodeFactory.instance.objectNode()
                                        .put("disbursementId", savedDisbursement.getId().toString())
                                        .put("reference", savedDisbursement.getReference())
                                        .put("channel", savedDisbursement.getChannel().name())
                                        .put("amount", savedDisbursement.getAmount().toPlainString())
                                        .put("currency", savedDisbursement.getCurrency())
                                        .set("customer", customerNode);

                                    System.out.println("Outbox Event: " + evt);

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
        .as(txOperator::transactional);
}


     


        public Mono<BulkDisbursement> createBulk(List<DisbursementRequest> requests) {
                BulkDisbursement batch = BulkDisbursement.builder()
                                .id(UUID.randomUUID())
                                .batchReference("BATCH-" + OffsetDateTime.now().toEpochSecond())
                                .status(BulkDisbursementStatus.PENDING)
                                .totalCount(requests.size())
                                .successCount(0)
                                .failedCount(0)
                                .createdAt(OffsetDateTime.now())
                                .updatedAt(OffsetDateTime.now())
                                .build();

                return bulkDisbursementRepo.save(batch)
                                .flatMap(savedBatch -> Flux.fromIterable(requests)
                                                .flatMap(req -> createSingle(req)
                                                                .flatMap(disb -> bulkItemRepo.save(
                                                                                BulkItem.builder()
                                                                                                .id(UUID.randomUUID())
                                                                                                .bulkId(savedBatch
                                                                                                                .getId())
                                                                                                .disbursementId(disb
                                                                                                                .getId())
                                                                                                .build())
                                                                                .thenReturn(disb))
                                                                .map(disb -> Tuples.of(disb, true))
                                                                .onErrorResume(e -> {
                                                                        return Mono.just(Tuples.of((Disbursement) null,
                                                                                        false));
                                                                }),
                                                                256)
                                                .collectList()
                                                .flatMap(results -> {
                                                        long successCount = results.stream().filter(t -> t.getT2())
                                                                        .count();
                                                        long failedCount = results.size() - successCount;

                                                        savedBatch.setSuccessCount((int) successCount);
                                                        savedBatch.setFailedCount((int) failedCount);
                                                        savedBatch.setStatus(failedCount == 0
                                                                        ? BulkDisbursementStatus.COMPLETED
                                                                        : (successCount == 0
                                                                                        ? BulkDisbursementStatus.FAILED
                                                                                        : BulkDisbursementStatus.PARTIAL));
                                                        savedBatch.setUpdatedAt(OffsetDateTime.now());

                                                        return bulkDisbursementRepo.save(savedBatch);
                                                }))
                                .as(txOperator::transactional);
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
