package com.example.disbursement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class B2CTelecelRequest implements PaymentDetails {
    @NotBlank
    private String momoNumber;
    private String partyCodeSender;
    private String country;
    
}
