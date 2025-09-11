package com.example.dispatcher.service;

import lombok.Value;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Value
public class RefundRequestEvt {

    UUID refundId;
    UUID originalDisbursementId;
    BigDecimal amount;
    String currency;
    String refundReference;
    UUID reversalId;
    String reason;
    String channel;
    UUID providerId;
    UUID transactionId;

    
    public static RefundRequestEvt from(Map<String, Object> evt) {
        return new RefundRequestEvt(
                UUID.fromString((String) evt.get("refundId")),
                UUID.fromString((String) evt.get("originalDisbursementId")),
                new BigDecimal(evt.get("amount").toString()),
                (String) evt.get("currency"),
                (String) evt.get("refundReference"),
                UUID.fromString((String) evt.get("reversalId")),
                (String) evt.get("reason"),
                (String) evt.get("channel"),
                UUID.fromString((String) evt.get("providerId")),
                UUID.fromString((String) evt.get("transactionId"))
        );
    }
}
