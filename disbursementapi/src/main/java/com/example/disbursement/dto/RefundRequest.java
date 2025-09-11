package com.example.disbursement.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RefundRequest {

    @NotBlank
    private String disbursementReference; 

    @NotBlank
    private String refundReference; 

    @NotNull
    @DecimalMin(value = "0.01", message = "Refund amount must be greater than zero")
    private BigDecimal amount;


    @NotBlank
    private String reason; 
}
