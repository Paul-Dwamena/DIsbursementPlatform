package com.example.disbursement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data

public class B2BTelecelRequest implements PaymentDetails {
    @NotBlank
    private String partyCodeReciever;
    private String partyCodeSender;
    private String country;
    

    
    
}
