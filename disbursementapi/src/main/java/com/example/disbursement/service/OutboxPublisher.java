package com.example.disbursement.service;

import com.example.disbursement.model.OutboxEvent;
import com.example.disbursement.repository.OutboxRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import jakarta.annotation.PostConstruct;

import java.time.OffsetDateTime;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxRepo outbox;
    private final KafkaTemplate<String, String> kafka;

    @PostConstruct
    public void startPump() {
        Flux.interval(Duration.ofSeconds(30))
            .flatMap(tick -> fetchAndPublishBatch(500))
            .onErrorContinue((ex, evt) -> {
                System.err.println("Error publishing outbox event: " + ex.getMessage());
            })
            .subscribe();
    }

    private Flux<OutboxEvent> fetchAndPublishBatch(int limit) {
        return outbox.fetchUnpublished(limit)
                .flatMap(this::publishEventWithTransaction);
    }

    private Mono<OutboxEvent> publishEventWithTransaction(OutboxEvent evt) {
        String key = evt.getAggregateId().toString();
        String topic = evt.getType();

        return Mono.fromFuture(() -> kafka.send(topic, key, evt.getPayload()).toCompletableFuture())
                   .flatMap(sentRecord -> {
                       evt.setPublishedAt(OffsetDateTime.now());
                       return outbox.save(evt);
                   })
                   .doOnSuccess(e -> System.out.println("[OutboxPublisher] Published event ID: " + evt.getId()))
                   .doOnError(e -> System.err.println("[OutboxPublisher] Failed to publish event ID: " + evt.getId() + " -> " + e.getMessage()));
    }
}
