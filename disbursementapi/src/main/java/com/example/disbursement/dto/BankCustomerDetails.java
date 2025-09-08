package com.example.disbursement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BankCustomerDetails implements CustomerDetails {

    @NotBlank
    private String accountNumber;

    @NotBlank
    private String accountName;

    @NotBlank
    private String bankName;

    private String bankCode;


    @NotBlank
    private String name;

    @NotBlank
    private String phoneNumber;
}
