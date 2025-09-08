package com.example.dispatcher.consumer;

import com.example.dispatcher.provider.PayoutProvider;
import com.example.dispatcher.service.ProviderRequest;
import com.example.dispatcher.service.ProviderResult;
import com.example.disbursement.model.Disbursement;
import com.example.disbursement.model.DisbursementStatus;
import com.example.disbursement.repository.DisbursementRepo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


@Slf4j
@Component
@RequiredArgsConstructor
public class RequestedConsumer {

    private final ReceiverOptions<String, String> receiverOptions;
    private final KafkaSender<String, String> kafkaSender;
    private final Map<String, PayoutProvider> providersByChannel;
    private final DisbursementRepo repo;
    private final ObjectMapper om;

    @PostConstruct
    public void consume() {
        // create a receiver with subscription to the topic
        KafkaReceiver.create(receiverOptions.subscription(Collections.singleton("payout.requested.v1")))
            .receive()
            .flatMap(rec ->
                handleRecord(rec.key(), rec.value())
                    // ack only after successful processing
                    .then(Mono.fromRunnable(rec.receiverOffset()::acknowledge))
                    .onErrorResume(ex -> {
                        // Log and send original record to DLQ
                        log.error("Error processing record with key {}: {}", rec.key(), ex.getMessage(), ex);

                        SenderRecord<String, String, String> dlqRecord =
                            SenderRecord.create(
                                "payout.requested.v1.dlq", // topic
                                null,                      // partition - let Kafka decide
                                null,                      // timestamp - let Kafka decide
                                rec.key(),                 // key
                                rec.value(),               // value
                                null                       // correlation metadata
                            );

                        return kafkaSender.send(Mono.just(dlqRecord)).then();
                    })
            )
            .subscribe(); // pipeline root subscription
    }

    private Mono<Void> handleRecord(String key, String value) {
        final DisbursementStatus sentStatus = DisbursementStatus.SENT;

        return Mono.fromCallable(() -> om.readTree(value))
            .flatMap(evt -> {
                UUID id = UUID.fromString(evt.get("disbursementId").asText());
                String channel = evt.get("channel").asText();

                PayoutProvider provider = providersByChannel.get(channel);
                if (provider == null) {
                    log.warn("No provider for channel {} — routing to DLQ", channel);

                    SenderRecord<String, String, String> dlqRecord =
                        SenderRecord.create(
                            "payout.requested.v1.dlq",
                            null,
                            null,
                            key,
                            value,
                            null
                        );

                    return kafkaSender.send(Mono.just(dlqRecord)).then();
                }

                return repo.findById(id)
                    .flatMap(d -> {
                        d.setStatus(sentStatus);
                        d.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                        return repo.save(d)
                            .then(provider.disburse(ProviderRequest.from(d)))
                            .flatMap(result -> updateAndPublish(d, result));
                    })
                    .then();
            });
    }

    private Mono<Disbursement> updateAndPublish(Disbursement d, ProviderResult result) {
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

                SenderRecord<String, String, String> statusRecord =
                        SenderRecord.create(
                                "payout.status.v1",
                                null,
                                null,
                                updated.getId().toString(),
                                statusEvt.toString(),
                                null
                        );

                return kafkaSender.send(Mono.just(statusRecord))
                        .next()                
                        .thenReturn(updated);   
            });
}
}