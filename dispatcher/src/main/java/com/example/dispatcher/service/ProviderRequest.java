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
    String disburse_type;
    String narration;

   
    public static ProviderRequest from(Map<String, Object> evt) {
        return new ProviderRequest(
                (String) evt.get("disbursementId"),
                (String) evt.get("reference"),
                (String) evt.get("transactionId"),   
                new BigDecimal(evt.get("amount").toString()),
                (String) evt.get("currency"),
                (Map<String, Object>) evt.get("customer"),
                (String) evt.get("disburse_type"),
                (String) evt.get("narration")
        );
    }
}
