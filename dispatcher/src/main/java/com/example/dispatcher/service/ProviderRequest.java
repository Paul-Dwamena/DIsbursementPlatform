package com.example.dispatcher.service;

import lombok.Value;

import java.math.BigDecimal;
import java.util.Map;

@Value
public class ProviderRequest {

    String disbursementId;
    String reference;
    String transactionId;
    BigDecimal amount;
    String currency;
    Map<String, Object> customer;

   
    public static ProviderRequest from(Map<String, Object> evt) {
        // evt is your Kafka payload JSON converted to Map
        return new ProviderRequest(
                (String) evt.get("disbursementId"),
                (String) evt.get("transactionId"),
                (String) evt.get("reference"),
                new BigDecimal(evt.get("amount").toString()),
                (String) evt.get("currency"),
                (Map<String, Object>) evt.get("customer")
        );
    }
}
