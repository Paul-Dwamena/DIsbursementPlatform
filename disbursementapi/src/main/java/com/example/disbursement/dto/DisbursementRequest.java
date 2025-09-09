package com.example.disbursement.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;




@Data
public class DisbursementRequest {

    @NotBlank
    private String reference;


    @NotNull
    private CustomerDetails customer;

    @NotBlank
    private String provider;

    @NotBlank
    private String narration;

    
    @NotBlank
    private String channel; // MoMo, Bank, Card, Wallet, etc.


    @NotNull
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotBlank
    private String currency;

     private PaymentDetails paymentDetails;

    @JsonCreator
    public DisbursementRequest(
            @JsonProperty("reference") String reference,
            @JsonProperty("customer") CustomerDetails customer,
            @JsonProperty("provider") String provider,
            @JsonProperty("narration") String narration,
            @JsonProperty("channel") String channel,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currency") String currency,
            @JsonProperty("paymentDetails") JsonNode paymentDetailsNode
    ) throws JsonProcessingException {
        this.reference = reference;
        this.customer = customer;
        this.provider = provider;
        this.narration = narration;
        this.channel = channel;
        this.amount = amount;
        this.currency = currency;

        ObjectMapper mapper = new ObjectMapper();

        if ("BANK".equalsIgnoreCase(channel)) {
            this.paymentDetails = mapper.treeToValue(paymentDetailsNode, BankPaymentDetails.class);
        } else if ("MOMO".equalsIgnoreCase(channel)) {
            this.paymentDetails = mapper.treeToValue(paymentDetailsNode, MomoPaymentDetails.class);
        } else {
            throw new IllegalArgumentException("Unsupported channel: " + channel);
        }
    }
    

    
}
