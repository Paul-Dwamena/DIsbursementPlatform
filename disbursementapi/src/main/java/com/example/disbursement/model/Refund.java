package com.example.disbursement.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("refunds")
public class Refund {

    @Id
    private UUID id;

    private String reference;

    @Column("original_disbursement_id")
    private UUID originalDisbursementId;

    @Column("transaction_id")
    private UUID transactionId;

    private BigDecimal amount;

    private String currency;

    private RefundStatus status;

    @Column("provider_id")
    private UUID providerId;

    @Column("provider_ref")
    private String providerRef;

    @Column("error_code")
    private String errorCode;

    @Column("error_message")
    private String errorMessage;

    private String narration;

    @CreatedDate
    @Column("created_at")
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private OffsetDateTime updatedAt;

    public Refund(UUID id, RefundStatus status, String providerRef) {
        this.id = id;
        this.status = status;
        this.providerRef = providerRef;
    }
}
