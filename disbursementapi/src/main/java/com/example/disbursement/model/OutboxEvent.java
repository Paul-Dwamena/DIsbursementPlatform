package com.example.disbursement.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("outbox_events")
public class OutboxEvent {

    @Id
    private Long id;

    private UUID aggregateId;
    private String type;        // payout.requested, payout.status
    private String payload;     // JSON string
    private String headers;     // JSON string

    @CreatedDate
    private OffsetDateTime createdAt;

    private OffsetDateTime publishedAt;
}
