package com.example.dispatcher.consumer;

import com.example.dispatcher.provider.PayoutProvider;
import com.example.dispatcher.provider.momo.mtn.MtnAdapter;
import com.example.dispatcher.service.ProviderRequest;
import com.example.dispatcher.service.ProviderResult;
import com.example.disbursement.model.Disbursement;
import com.example.disbursement.model.DisbursementStatus;
import com.example.disbursement.repository.DisbursementRepo;
import com.example.disbursement.repository.PaymentProviderRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequestedConsumer {

    private final ReceiverOptions<String, String> receiverOptions;
    private final KafkaSender<String, String> kafkaSender;
    private final Map<String, PayoutProvider> providersByChannel;
    private final DisbursementRepo repo;
    private final PaymentProviderRepo paymentProviderRepo;
    private final ObjectMapper om;

    @Autowired
    private MtnAdapter momoMtnProvider;

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

     private Mono<Void> handleRecord(String key, String value) {
        return Mono.fromCallable(() -> {
                    JsonNode evtNode = om.readTree(value);
                    return evtNode;
                })
                .flatMap(evtNode -> {
                    Map<String, Object> evtMap = om.convertValue(evtNode, Map.class);
                    ProviderRequest req = ProviderRequest.from(evtMap);

                    String channel = (String) evtMap.get("channel");
                    UUID providerId = UUID.fromString((String) evtMap.get("provider"));

                    return paymentProviderRepo.findById(providerId)
                            .flatMap(providerEntity -> {
                                String providerName = providerEntity.getCode(); // MTN, TELECEL, etc.
                                String mapKey = channel + "_" + providerName;   // e.g., "MOMO_MTN"

                                PayoutProvider providerAdapter = providersByChannel.get(mapKey);
                                if (providerAdapter == null) {
                                    log.warn("No adapter for {} — routing to DLQ", mapKey);
                                    return sendToDlq(key, value);
                                }

                                return repo.findById(UUID.fromString(req.getDisbursementId()))
                                        .flatMap(d -> {
                                            d.setStatus(DisbursementStatus.SENT);
                                            d.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                                            return repo.save(d)
                                                    .then(providerAdapter.disburse(req))
                                                    .flatMap(result -> updateAndPublish(d, result))
                                                    .then();
                                        })
                                        .switchIfEmpty(sendToDlq(key, value).then());
                            })
                            .switchIfEmpty(sendToDlq(key, value).then());
                });
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
        return kafkaSender.send(Mono.just(dlqRecord)).then();
    }

    private Mono<Void> updateAndPublish(Disbursement d, ProviderResult result) {
        d.setProviderRef(result.getProviderRef());
        d.setStatus(result.isSuccess() ? DisbursementStatus.SUCCESS : DisbursementStatus.FAILED);
        d.setErrorCode(result.getErrorCode());
        d.setErrorMessage(result.getErrorMessage());
        d.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        return repo.save(d)
                .flatMap(updated -> {
                    ObjectNode statusEvt = om.createObjectNode()
                            .put("disbursementId", updated.getId().toString())
                            .put("status", updated.getStatus().name())
                            .put("providerRef", Optional.ofNullable(updated.getProviderRef()).orElse(""));

                    SenderRecord<String, String, String> statusRecord = SenderRecord.create(
                            "payout.status.v1",
                            null,
                            null,
                            updated.getId().toString(),
                            statusEvt.toString(),
                            null
                    );

                    return kafkaSender.send(Mono.just(statusRecord))
                            .next()
                            .then();
                });
    }
}
