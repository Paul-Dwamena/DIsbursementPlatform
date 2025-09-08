package com.example.disbursement.repository;

import com.example.disbursement.model.OutboxEvent;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface OutboxRepo extends ReactiveCrudRepository<OutboxEvent, Long> {

    @Query("SELECT * FROM outbox_events WHERE published_at IS NULL ORDER BY id ASC LIMIT :limit")
    Flux<OutboxEvent> fetchUnpublished(@Param("limit") int limit);


    @Query("""
           INSERT INTO outbox_events(aggregate_id, type, payload, headers)
           VALUES (:aggregateId, :type, :payload::jsonb, :headers::jsonb)
           RETURNING *
           """)
    Mono<OutboxEvent> insertReturning(@Param("aggregateId") UUID aggregateId,
                                      @Param("type") String type,
                                      @Param("payload") String payload,
                                      @Param("headers") String headers);
}
