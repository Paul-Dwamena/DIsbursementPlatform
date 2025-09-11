package com.example.dispatcher.consumer;

import com.example.dispatcher.provider.RefundProvider;
import com.example.dispatcher.provider.momo.mtn.MtnRefundAdapter;
import com.example.dispatcher.service.ProviderResult;
import com.example.dispatcher.service.RefundRequestEvt;
import com.example.disbursement.model.Refund;
import com.example.disbursement.model.RefundStatus;
import com.example.disbursement.repository.PaymentProviderRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import jakarta.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefundRequestedConsumer {

    private final ReceiverOptions<String, String> receiverOptions;
    private final KafkaSender<String, String> kafkaSender;
    private final Map<String, RefundProvider> refundProvidersByChannel; 
    private final PaymentProviderRepo paymentProviderRepo;
    private final ObjectMapper om;
    @Autowired
    private DatabaseClient dbClient;
    @Autowired
    private MtnRefundAdapter mtnRefundProvider;

    @PostConstruct
    public void initProviders() {
        refundProvidersByChannel.put("MOMO_MTN", mtnRefundProvider);
    
    }

    @PostConstruct
    public void consume() {
        KafkaReceiver.create(receiverOptions.subscription(Collections.singleton("refund.requested.v1")))
            .receive()
            .flatMap(rec -> handleRecord(rec.key(), rec.value())
                    .then(Mono.fromRunnable(rec.receiverOffset()::acknowledge))
                    .onErrorResume(ex -> {
                        log.error("Error processing refund record with key {}: {}", rec.key(), ex.getMessage(), ex);
                        return sendToDlq(rec.key(), rec.value());
                    })
            )
            .subscribe();
    }

    
    

       private Mono<Void> handleRecord(String key, String value) {
        return Mono.defer(() -> {
        JsonNode evtNode;
        try {
            evtNode = om.readTree(value);
        } catch (Exception e) {
            return sendToDlq(key, value); 
        }

        Map<String, Object> evtMap = om.convertValue(evtNode, Map.class);
        RefundRequestEvt req = RefundRequestEvt.from(evtMap);

        String channel = (String) evtMap.get("channel");
        UUID providerId = UUID.fromString((String) evtMap.get("providerId"));
        UUID refundId = req.getRefundId();

        return paymentProviderRepo.findById(providerId)
                .flatMap(providerEntity -> {
                    String providerName = providerEntity.getCode();
                    String mapKey = channel + "_" + providerName;
                    RefundProvider refundAdapter = refundProvidersByChannel.get(mapKey);

                    if (refundAdapter == null) {
                        log.warn("No adapter for {} — routing to DLQ", mapKey);
                        return sendToDlq(key, value);
                    }

                    // Idempotency: mark SENT only if not already SENT or SUCCESS
                    return dbClient.sql("""
                            UPDATE refunds
                            SET status = :sentStatus, updated_at = :updatedAt
                            WHERE id = :id AND status NOT IN (:sent, :success)
                            """)
                            .bind("sentStatus", RefundStatus.SENT.name())
                            .bind("updatedAt", OffsetDateTime.now(ZoneOffset.UTC))
                            .bind("id", refundId)
                            .bind("sent", RefundStatus.SENT.name())
                            .bind("success", RefundStatus.SUCCESS.name())
                            .fetch()
                            .rowsUpdated()
                            .flatMap(updatedRows -> {
                                if (updatedRows == 0) {
                                    // Already processed → publish current status
                                    return dbClient.sql("SELECT * FROM refunds WHERE id = :id")
                                            .bind("id", refundId)
                                            .map(this::mapRowToRefund)
                                            .one()
                                            .flatMap(this::publishStatus)
                                            .then();
                                }

                                // Proceed to call provider
                                return refundAdapter.refundMomo(req)
                                        .flatMap(result -> updateAndPublish(refundId, result))
                                        .then();
                            });
                })
                .switchIfEmpty(sendToDlq(key, value).then());
    });
}


private Refund mapRowToRefund(Row row, RowMetadata meta) {
    UUID id = row.get("id", UUID.class);
    RefundStatus status = RefundStatus.valueOf(row.get("status", String.class));
    String providerRef = row.get("provider_ref", String.class);
    return new Refund(id, status, providerRef);
}


private Mono<Void> publishStatus(Refund refund) {
    ObjectNode statusEvt = om.createObjectNode()
            .put("refundId", refund.getId().toString())
            .put("status", refund.getStatus().name())
            .put("providerRef", Optional.ofNullable(refund.getProviderRef()).orElse(""));

    SenderRecord<String, String, String> statusRecord = SenderRecord.create(
            "refund.status.v1",
            null,
            null,
            refund.getId().toString(),
            statusEvt.toString(),
            null
    );

    return kafkaSender.send(Mono.just(statusRecord)).next().then();
}

/** Update refund record + publish new status */
private Mono<Void> updateAndPublish(UUID refundId, ProviderResult result) {
    RefundStatus status = result.isSuccess() ? RefundStatus.SUCCESS : RefundStatus.FAILED;
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    String providerRef = Optional.ofNullable(result.getProviderRef()).orElse("");
    String errorCode = result.getErrorCode();
    String errorMessage = result.getErrorMessage();

    DatabaseClient.GenericExecuteSpec spec = dbClient.sql("""
        UPDATE refunds
        SET status = :status, provider_ref = :providerRef,
            error_code = :errorCode, error_message = :errorMessage, updated_at = :updatedAt
        WHERE id = :id
        """)
        .bind("status", status.name())
        .bind("providerRef", providerRef)
        .bind("updatedAt", now)
        .bind("id", refundId);

    if (errorCode != null) {
        spec = spec.bind("errorCode", errorCode);
    } else {
        spec = spec.bindNull("errorCode", String.class);
    }

    if (errorMessage != null) {
        spec = spec.bind("errorMessage", errorMessage);
    } else {
        spec = spec.bindNull("errorMessage", String.class);
    }

    return spec.then()
            .then(Mono.defer(() -> publishStatus(
                    new Refund(refundId, status, providerRef)
            )));
}



    



    private Mono<Void> sendToDlq(String key, String value) {
        SenderRecord<String, String, String> dlqRecord = SenderRecord.create(
                "refund.requested.v1.dlq",
                null,
                null,
                key,
                value,
                null
        );
        return kafkaSender.send(Mono.just(dlqRecord)).next().then();
    }
}

