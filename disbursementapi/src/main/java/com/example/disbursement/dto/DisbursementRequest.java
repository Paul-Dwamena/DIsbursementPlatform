package com.example.disbursement.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DisbursementRequest {

    @NotBlank
    private String reference;


    @NotNull
    private CustomerDetails customer; // 👈 polymorphic object

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
}
