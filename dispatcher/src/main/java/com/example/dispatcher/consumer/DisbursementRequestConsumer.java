package com.example.dispatcher.consumer;

import com.example.dispatcher.provider.PayoutProvider;
import com.example.dispatcher.provider.momo.mtn.MtnDisburseAdapter;
import com.example.dispatcher.service.ProviderRequest;
import com.example.dispatcher.service.ProviderResult;
import com.example.disbursement.model.Disbursement;
import com.example.disbursement.model.DisbursementStatus;
import com.example.disbursement.repository.PaymentProviderRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.r2dbc.spi.RowMetadata;
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



@Slf4j
@Component
@RequiredArgsConstructor
public class DisbursementRequestConsumer {

    private final ReceiverOptions<String, String> receiverOptions;
    private final KafkaSender<String, String> kafkaSender;
    private final Map<String, PayoutProvider> providersByChannel;
    private final PaymentProviderRepo paymentProviderRepo;
    private final ObjectMapper om;

    @Autowired
    private MtnDisburseAdapter momoMtnProvider;

    @Autowired
    private DatabaseClient dbClient;

    @PostConstruct
    public void initProviders() {
        providersByChannel.put("MOMO_MTN", momoMtnProvider);
    }

    @PostConstruct
    public void consume() {
    KafkaReceiver.create(receiverOptions.subscription(Collections.singleton("payout.requested.v1")))
            .receive()
            .flatMap(rec -> handleRecord(rec.key(), rec.value())
                    .then(Mono.fromRunnable(rec.receiverOffset()::acknowledge))
                    .onErrorResume(ex -> {
                        log.error("Error processing record with key {}: {}", rec.key(), ex.getMessage(), ex);
                        return sendToDlq(rec.key(), rec.value());
                    })
            )
            .subscribe();
}

public Mono<Void> handleRecord(String key, String value) {
    return Mono.defer(() -> {
        JsonNode evtNode;
        try {
            evtNode = om.readTree(value);
        } catch (Exception e) {
            return sendToDlq(key, value); // parsing failed
        }

        Map<String, Object> evtMap = om.convertValue(evtNode, Map.class);
        ProviderRequest req = ProviderRequest.from(evtMap);

        String channel = (String) evtMap.get("channel");
        UUID providerId = UUID.fromString((String) evtMap.get("provider"));
        UUID disbursementId = UUID.fromString(req.getDisbursementId());

        return paymentProviderRepo.findById(providerId)
                .flatMap(providerEntity -> {
                    String providerName = providerEntity.getCode();
                    String mapKey = channel + "_" + providerName;
                    PayoutProvider providerAdapter = providersByChannel.get(mapKey);

                    if (providerAdapter == null) {
                        log.warn("No adapter for {} — routing to DLQ", mapKey);
                        return sendToDlq(key, value);
                    }

                    // Idempotency: mark SENT only if not already SENT or SUCCESS
                    return dbClient.sql("""
                            UPDATE disbursements
                            SET status = :sentStatus, updated_at = :updatedAt
                            WHERE id = :id AND status NOT IN (:sent, :success)
                            """)
                            .bind("sentStatus", DisbursementStatus.SENT.name())
                            .bind("updatedAt", OffsetDateTime.now(ZoneOffset.UTC))
                            .bind("id", disbursementId)
                            .bind("sent", DisbursementStatus.SENT.name())
                            .bind("success", DisbursementStatus.SUCCESS.name())
                            .fetch()
                            .rowsUpdated()
                            .flatMap(updatedRows -> {
                                if (updatedRows == 0) {
                                    // Already processed → publish current status
                                    return dbClient.sql("SELECT * FROM disbursements WHERE id = :id")
                                        .bind("id", disbursementId)
                                        .map((row, meta) -> mapRowToDisbursement(row, meta))
                                        .one()
                                        .flatMap(this::publishStatus)
                                        .then();

                                }

                                // Proceed to call provider
                                return providerAdapter.disburse(req)
                                        .flatMap(result -> updateAndPublish(disbursementId, result))
                                        .then();
                            });
                })
                .switchIfEmpty(sendToDlq(key, value).then());
    });
}

    private Disbursement mapRowToDisbursement(Row row, RowMetadata meta) {
        UUID id = row.get("id", UUID.class);
        DisbursementStatus status = DisbursementStatus.valueOf(row.get("status", String.class));
        String providerRef = row.get("provider_ref", String.class);
        return new Disbursement(id, status, providerRef);
    }



    private Mono<Void> publishStatus(Disbursement d) {
        ObjectNode statusEvt = om.createObjectNode()
                .put("disbursementId", d.getId().toString())
                .put("status", d.getStatus().name())
                .put("providerRef", Optional.ofNullable(d.getProviderRef()).orElse(""));

        SenderRecord<String, String, String> statusRecord = SenderRecord.create(
                "payout.status.v1",
                null,
                null,
                d.getId().toString(),
                statusEvt.toString(),
                null
        );

        return kafkaSender.send(Mono.just(statusRecord)).next().then();
    }

    private Mono<Void> sendToDlq(String key, String value) {
        SenderRecord<String, String, String> dlqRecord = SenderRecord.create(
                "payout.requested.v1.dlq",
                null,
                null,
                key,
                value,
                null
        );
        return kafkaSender.send(Mono.just(dlqRecord)).next().then();
    }

    private Mono<Void> updateAndPublish(UUID disbursementId, ProviderResult result) {
    DisbursementStatus status = result.isSuccess() ? DisbursementStatus.SUCCESS : DisbursementStatus.FAILED;
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    String providerRef = Optional.ofNullable(result.getProviderRef()).map(Object::toString).orElse("");
    String errorCode = result.getErrorCode();
    String errorMessage = result.getErrorMessage();

    DatabaseClient.GenericExecuteSpec spec = dbClient.sql("""
        UPDATE disbursements
        SET status = :status, provider_ref = :providerRef,
            error_code = :errorCode, error_message = :errorMessage, updated_at = :updatedAt
        WHERE id = :id
        """)
        .bind("status", status.name())
        .bind("providerRef", providerRef)
        .bind("updatedAt", now)
        .bind("id", disbursementId);

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
                        new Disbursement(disbursementId, status, providerRef)
                )));

        }


    

}
