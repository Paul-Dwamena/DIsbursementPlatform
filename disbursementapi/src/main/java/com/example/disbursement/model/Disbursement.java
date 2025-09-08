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

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("disbursements")
public class Disbursement {

    @Id
    private UUID id;

    private String reference;


    @Column("transaction_id")
    private UUID transactionId;

    @Column("customer_id")
    private String customerId;

    private PaymentChannel channel;

    private BigDecimal amount;

    private String currency;

    private DisbursementStatus status;

    @Column("provider_id")
    private String providerId;


    @Column("provider_ref")
    private String providerRef;

    @Column("error_code")
    private String errorCode;

    @Column("error_message")
    private String errorMessage;

    private String narration;

    
    // @Column("payment_details")
    // private String paymentDetails;

    // public String getPaymentDetails() { return paymentDetails; }
    // public void setPaymentDetails(String paymentDetails) { this.paymentDetails = paymentDetails; }



    @CreatedDate
    @Column("created_at")
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
