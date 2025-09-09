package com.example.disbursement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MomoPaymentDetails implements PaymentDetails {
    @NotBlank
    private String momoNumber;
}
