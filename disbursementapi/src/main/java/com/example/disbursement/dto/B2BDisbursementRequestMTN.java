package com.example.disbursement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class B2BDisbursementRequestMTN implements PaymentDetails {
    @NotBlank
    private String partyCode;
    private String country;
    private String momoNumber;

    
}
